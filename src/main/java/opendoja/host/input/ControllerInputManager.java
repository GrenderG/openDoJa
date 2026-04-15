package opendoja.host.input;

import de.gurkenlabs.input4j.ComponentType;
import de.gurkenlabs.input4j.InputComponent;
import de.gurkenlabs.input4j.InputDevice;
import de.gurkenlabs.input4j.InputDevicePlugin;
import de.gurkenlabs.input4j.InputDevices;
import opendoja.host.OpenDoJaLog;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ControllerInputManager implements AutoCloseable {
    private static final long POLL_INTERVAL_MS = 8L;
    private static final long PLUGIN_RETRY_INTERVAL_MS = 5_000L;
    private static final long HOT_PLUG_INTERVAL_MS = 3_000L;
    private static final float DIGITAL_ACTIVE_THRESHOLD = 0.5f;
    private static final float AXIS_DEADZONE = 0.20f;
    private static final float AXIS_PRESS_THRESHOLD = 0.55f;
    private static final float AXIS_RELEASE_THRESHOLD = 0.35f;
    private static final float ONE_SIDED_AXIS_REARM_THRESHOLD = 0.10f;
    private static final float ONE_SIDED_AXIS_NEGATIVE_REST_THRESHOLD = -0.90f;
    private static final float ONE_SIDED_AXIS_ZERO_REST_THRESHOLD = 0.10f;
    private static final long ONE_SIDED_AXIS_REARM_SETTLE_NANOS = TimeUnit.MILLISECONDS.toNanos(60L);
    private static final String INPUT4J_LOGGER_NAME = "de.gurkenlabs.input4j";

    private final Frame ownerFrame;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "openDoJa-controller");
        thread.setDaemon(true);
        return thread;
    });
    private final CopyOnWriteArrayList<ControllerInputListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, DeviceState> deviceStates = new LinkedHashMap<>();

    private volatile InputDevicePlugin plugin;
    private volatile List<ControllerDeviceInfo> devices = List.of();
    private volatile String lastErrorMessage;
    private volatile boolean available;
    private volatile boolean closed;

    private long lastPluginAttemptNanos;
    private String lastLoggedErrorMessage;

    public ControllerInputManager(Frame ownerFrame) {
        silenceInput4jLogs();
        this.ownerFrame = ownerFrame;
        executor.scheduleWithFixedDelay(this::pollLoop, 0L, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public List<ControllerDeviceInfo> devices() {
        return devices;
    }

    public boolean isAvailable() {
        return available;
    }

    public String lastErrorMessage() {
        return lastErrorMessage;
    }

    public List<ControllerInputEvent> activeInputs() {
        List<ControllerInputEvent> active = new ArrayList<>();
        synchronized (deviceStates) {
            for (DeviceState state : deviceStates.values()) {
                for (Map.Entry<String, Boolean> entry : state.activeControls.entrySet()) {
                    if (!entry.getValue()) {
                        continue;
                    }
                    ControllerBindingDescriptor descriptor = ControllerBindingDescriptor.parse(entry.getKey());
                    if (descriptor == null) {
                        continue;
                    }
                    active.add(new ControllerInputEvent(
                            state.info,
                            descriptor,
                            state.activeValues.getOrDefault(entry.getKey(), 0f),
                            true,
                            System.nanoTime()));
                }
            }
        }
        return List.copyOf(active);
    }

    public void addListener(ControllerInputListener listener) {
        if (listener != null) {
            listeners.add(listener);
            listener.onDevicesChanged(devices);
        }
    }

    public void removeListener(ControllerInputListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdownNow();
        InputDevicePlugin currentPlugin = plugin;
        plugin = null;
        if (currentPlugin != null) {
            try {
                currentPlugin.close();
            } catch (Exception exception) {
                OpenDoJaLog.warn(ControllerInputManager.class, "Failed to close controller plugin", exception);
            }
        }
        synchronized (deviceStates) {
            deviceStates.clear();
            devices = List.of();
        }
        listeners.clear();
        available = false;
    }

    private void pollLoop() {
        if (closed) {
            return;
        }
        try {
            ensurePlugin();
            if (plugin == null) {
                return;
            }
            pollDevices();
        } catch (Throwable throwable) {
            OpenDoJaLog.error(ControllerInputManager.class, "Unhandled controller polling failure", throwable);
            handleInitializationFailure("Controller polling failed: " + throwable.getMessage());
        }
    }

    private void ensurePlugin() {
        if (plugin != null) {
            return;
        }
        long now = System.nanoTime();
        long retryIntervalNanos = TimeUnit.MILLISECONDS.toNanos(PLUGIN_RETRY_INTERVAL_MS);
        if (lastPluginAttemptNanos != 0L && now - lastPluginAttemptNanos < retryIntervalNanos) {
            return;
        }
        lastPluginAttemptNanos = now;
        replacePlugin(openPlugin(), true);
    }

    private InputDevicePlugin openPlugin() {
        try {
            InputDevices.configure().setAccuracy(3);
            InputDevices.configure().setHotPlugInterval((int) HOT_PLUG_INTERVAL_MS);
            InputDevicePlugin opened = ownerFrame == null
                    ? InputDevices.init()
                    : InputDevices.init(ownerFrame, InputDevices.InputLibrary.PLATFORM_DEFAULT);
            if (opened == null) {
                handleInitializationFailure("Controller input provider is unavailable");
                return null;
            }
            opened.onDevicesChanged(this::refreshFromPlugin);
            opened.onDeviceConnected(device -> refreshFromPlugin());
            opened.onDeviceDisconnected(device -> refreshFromPlugin());
            clearInitializationFailure();
            return opened;
        } catch (Throwable throwable) {
            handleInitializationFailure("Controller input initialization failed: " + throwable.getMessage());
            OpenDoJaLog.warn(ControllerInputManager.class, "Controller input initialization failed", throwable);
            return null;
        }
    }

    private void replacePlugin(InputDevicePlugin replacement, boolean initialAttempt) {
        if (replacement == null) {
            if (initialAttempt) {
                available = false;
            }
            return;
        }
        InputDevicePlugin previous = plugin;
        syncDevices(replacement.getAll());
        plugin = replacement;
        available = true;
        if (previous != null) {
            try {
                previous.close();
            } catch (Exception exception) {
                OpenDoJaLog.warn(ControllerInputManager.class, "Failed to close stale controller plugin", exception);
            }
        }
    }

    private void refreshFromPlugin() {
        InputDevicePlugin currentPlugin = plugin;
        if (currentPlugin == null || closed) {
            return;
        }
        syncDevices(currentPlugin.getAll());
    }

    private void syncDevices(Collection<InputDevice> inputDevices) {
        List<DeviceSnapshot> snapshots = new ArrayList<>();
        if (inputDevices != null) {
            for (InputDevice inputDevice : inputDevices) {
                DeviceSnapshot snapshot = snapshotFor(inputDevice);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
        }
        snapshots.sort((left, right) -> left.info.displayName().compareToIgnoreCase(right.info.displayName()));

        List<ControllerDeviceInfo> newDeviceInfos = new ArrayList<>(snapshots.size());
        List<ControllerDeviceInfo> disconnected = new ArrayList<>();
        List<ControllerDeviceInfo> connected = new ArrayList<>();

        synchronized (deviceStates) {
            Map<String, DeviceState> updatedStates = new LinkedHashMap<>();
            for (DeviceSnapshot snapshot : snapshots) {
                newDeviceInfos.add(snapshot.info);
                DeviceState existing = deviceStates.get(snapshot.info.id());
                if (existing == null) {
                    existing = new DeviceState(snapshot.device, snapshot.info);
                    connected.add(snapshot.info);
                } else {
                    existing.device = snapshot.device;
                    existing.info = snapshot.info;
                }
                updatedStates.put(snapshot.info.id(), existing);
            }
            for (DeviceState existing : deviceStates.values()) {
                if (!updatedStates.containsKey(existing.info.id())) {
                    disconnected.add(existing.info);
                }
            }
            deviceStates.clear();
            deviceStates.putAll(updatedStates);
            devices = List.copyOf(newDeviceInfos);
        }

        if (!connected.isEmpty() || !disconnected.isEmpty()) {
            for (ControllerDeviceInfo device : connected) {
                OpenDoJaLog.info(ControllerInputManager.class, "Controller connected: " + device.displayName());
            }
            for (ControllerDeviceInfo device : disconnected) {
                OpenDoJaLog.info(ControllerInputManager.class, "Controller disconnected: " + device.displayName());
            }
            notifyDevicesChanged();
        }
    }

    private void pollDevices() {
        List<DeviceState> states;
        synchronized (deviceStates) {
            states = new ArrayList<>(deviceStates.values());
        }
        for (DeviceState state : states) {
            try {
                state.device.poll();
                pollDeviceState(state);
            } catch (Throwable throwable) {
                OpenDoJaLog.warn(ControllerInputManager.class,
                        () -> "Controller poll failed for " + state.info.displayName() + ": " + throwable.getMessage());
            }
        }
    }

    private void pollDeviceState(DeviceState state) {
        List<InputComponent> components = state.device.getComponents();
        for (InputComponent component : components) {
            if (component == null || component.isRelative()) {
                continue;
            }
            ControllerBindingDescriptor descriptor = descriptorFor(component);
            if (descriptor == null) {
                continue;
            }
            String rawComponentId = rawComponentId(component);
            if (component.getType() == ComponentType.AXIS
                    && descriptor.kind() == ControllerBindingDescriptor.Kind.BUTTON
                    && isTriggerAxisComponent(rawComponentId)) {
                handleTriggerAxisAsButton(state, rawComponentId, descriptor, component.getData());
            } else if (descriptor.kind() == ControllerBindingDescriptor.Kind.AXIS) {
                handleAxis(state, descriptor, component.getData());
            } else {
                handleDigital(state, descriptor, component.getData());
            }
        }
        state.initialized = true;
    }

    private void handleTriggerAxisAsButton(DeviceState state, String rawComponentId,
                                           ControllerBindingDescriptor descriptor, float rawValue) {
        AxisMode axisMode = state.axisModes.compute(rawComponentId,
                (componentId, previousMode) -> resolveAxisMode(componentId, rawValue, previousMode));
        float normalized = normalizeOneSidedAxis(rawValue, axisMode);
        boolean previousActive = state.activeControls.getOrDefault(descriptor.controlId(), false);
        boolean readyForPress = state.oneSidedAxisReadyForPress.getOrDefault(rawComponentId, true);
        long now = System.nanoTime();
        // Rearm only after the trigger has stayed near rest briefly, otherwise a slow or noisy
        // analog release can cross the press threshold a second time and emit a false re-press.
        if (normalized <= ONE_SIDED_AXIS_REARM_THRESHOLD) {
            long restStart = state.oneSidedAxisRestStartNanos.getOrDefault(rawComponentId, 0L);
            if (restStart == 0L) {
                state.oneSidedAxisRestStartNanos.put(rawComponentId, now);
            } else if (now - restStart >= ONE_SIDED_AXIS_REARM_SETTLE_NANOS) {
                readyForPress = true;
            }
        } else {
            state.oneSidedAxisRestStartNanos.remove(rawComponentId);
        }

        boolean active = previousActive
                ? normalized > AXIS_RELEASE_THRESHOLD
                : readyForPress && normalized >= AXIS_PRESS_THRESHOLD;
        if (normalized <= AXIS_RELEASE_THRESHOLD) {
            active = false;
        }
        if (active) {
            readyForPress = false;
            state.oneSidedAxisRestStartNanos.remove(rawComponentId);
        }
        state.oneSidedAxisReadyForPress.put(rawComponentId, readyForPress);
        updateControlState(state, descriptor, active, active ? normalized : 0f);
    }

    private void handleDigital(DeviceState state, ControllerBindingDescriptor descriptor, float rawValue) {
        boolean active = Math.abs(rawValue) >= DIGITAL_ACTIVE_THRESHOLD;
        updateControlState(state, descriptor, active, active ? 1f : 0f);
    }

    private void handleAxis(DeviceState state, ControllerBindingDescriptor descriptor, float rawValue) {
        AxisMode axisMode = state.axisModes.compute(descriptor.componentId(),
                (componentId, previousMode) -> resolveAxisMode(componentId, rawValue, previousMode));
        if (axisMode.isOneSided()) {
            handleOneSidedAxis(state, descriptor, rawValue);
            return;
        }
        float normalized = normalizeBidirectionalAxis(rawValue);
        ControllerBindingDescriptor negativeDescriptor = ControllerBindingDescriptor.axis(
                descriptor.componentId(),
                ControllerBindingDescriptor.Direction.NEGATIVE);
        ControllerBindingDescriptor positiveDescriptor = ControllerBindingDescriptor.axis(
                descriptor.componentId(),
                ControllerBindingDescriptor.Direction.POSITIVE);

        boolean previousNegative = state.activeControls.getOrDefault(negativeDescriptor.controlId(), false);
        boolean previousPositive = state.activeControls.getOrDefault(positiveDescriptor.controlId(), false);

        boolean negativeActive = normalized <= -AXIS_PRESS_THRESHOLD
                || (previousNegative && normalized < -AXIS_RELEASE_THRESHOLD);
        boolean positiveActive = normalized >= AXIS_PRESS_THRESHOLD
                || (previousPositive && normalized > AXIS_RELEASE_THRESHOLD);
        if (Math.abs(normalized) <= AXIS_RELEASE_THRESHOLD) {
            negativeActive = false;
            positiveActive = false;
        }

        updateControlState(state, negativeDescriptor, negativeActive, negativeActive ? Math.abs(normalized) : 0f);
        updateControlState(state, positiveDescriptor, positiveActive, positiveActive ? Math.abs(normalized) : 0f);
    }

    private void handleOneSidedAxis(DeviceState state, ControllerBindingDescriptor descriptor, float rawValue) {
        AxisMode axisMode = state.axisModes.getOrDefault(descriptor.componentId(), AxisMode.ONE_SIDED_ZERO_REST);
        float normalized = normalizeOneSidedAxis(rawValue, axisMode);
        ControllerBindingDescriptor negativeDescriptor = ControllerBindingDescriptor.axis(
                descriptor.componentId(),
                ControllerBindingDescriptor.Direction.NEGATIVE);
        ControllerBindingDescriptor positiveDescriptor = ControllerBindingDescriptor.axis(
                descriptor.componentId(),
                ControllerBindingDescriptor.Direction.POSITIVE);
        boolean previousPositive = state.activeControls.getOrDefault(positiveDescriptor.controlId(), false);
        boolean readyForPress = state.oneSidedAxisReadyForPress.getOrDefault(descriptor.componentId(), true);
        long now = System.nanoTime();
        if (normalized <= ONE_SIDED_AXIS_REARM_THRESHOLD) {
            long restStart = state.oneSidedAxisRestStartNanos.getOrDefault(descriptor.componentId(), 0L);
            if (restStart == 0L) {
                state.oneSidedAxisRestStartNanos.put(descriptor.componentId(), now);
            } else if (now - restStart >= ONE_SIDED_AXIS_REARM_SETTLE_NANOS) {
                readyForPress = true;
            }
        } else {
            state.oneSidedAxisRestStartNanos.remove(descriptor.componentId());
        }
        boolean positiveActive = previousPositive
                ? normalized > AXIS_RELEASE_THRESHOLD
                : readyForPress && normalized >= AXIS_PRESS_THRESHOLD;
        if (normalized <= AXIS_RELEASE_THRESHOLD) {
            positiveActive = false;
        }
        if (positiveActive) {
            readyForPress = false;
            state.oneSidedAxisRestStartNanos.remove(descriptor.componentId());
        }
        state.oneSidedAxisReadyForPress.put(descriptor.componentId(), readyForPress);
        updateControlState(state, negativeDescriptor, false, 0f);
        updateControlState(state, positiveDescriptor, positiveActive, positiveActive ? normalized : 0f);
    }

    private void updateControlState(DeviceState state, ControllerBindingDescriptor descriptor, boolean active, float value) {
        String controlId = descriptor.controlId();
        boolean previous = state.activeControls.getOrDefault(controlId, false);
        state.activeControls.put(controlId, active);
        if (active) {
            state.activeValues.put(controlId, value);
        } else {
            state.activeValues.remove(controlId);
        }
        if (!state.initialized || previous == active) {
            return;
        }
        ControllerInputEvent event = new ControllerInputEvent(state.info, descriptor, value, active, System.nanoTime());
        if (OpenDoJaLog.isDebugEnabled()) {
            OpenDoJaLog.debug(ControllerInputManager.class,
                    () -> "Controller input " + state.info.displayName() + " " + event.displayLabel()
                            + " -> " + (event.active() ? "pressed" : "released"));
        }
        for (ControllerInputListener listener : listeners) {
            listener.onInput(event);
        }
    }

    private void notifyDevicesChanged() {
        List<ControllerDeviceInfo> currentDevices = devices;
        for (ControllerInputListener listener : listeners) {
            listener.onDevicesChanged(currentDevices);
        }
    }

    private DeviceSnapshot snapshotFor(InputDevice inputDevice) {
        if (inputDevice == null || !isRelevantController(inputDevice)) {
            return null;
        }
        String stableId = normalizeDeviceId(inputDevice);
        ControllerDeviceInfo deviceInfo = new ControllerDeviceInfo(
                stableId,
                inputDevice.getName(),
                inputDevice.getProductName());
        return new DeviceSnapshot(inputDevice, deviceInfo);
    }

    private static String normalizeDeviceId(InputDevice inputDevice) {
        String productName = safeToken(inputDevice.getProductName());
        String name = safeToken(inputDevice.getName());
        String runtimeId = safeToken(inputDevice.getID());
        String suffix = runtimeId.isBlank() ? "" : "." + runtimeId;
        if (!productName.isBlank()) {
            return productName + (name.isBlank() ? "" : "." + name) + suffix;
        }
        if (!name.isBlank()) {
            return name + suffix;
        }
        return runtimeId;
    }

    private static String safeToken(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toUpperCase(current));
            } else if (current == '_' || current == '-' || current == '.') {
                builder.append(current);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private static boolean isRelevantController(InputDevice inputDevice) {
        List<InputComponent> components = inputDevice.getComponents();
        int buttons = 0;
        int axes = 0;
        for (InputComponent component : components) {
            if (component == null || component.isRelative()) {
                continue;
            }
            if (component.getType() == ComponentType.BUTTON) {
                buttons++;
            } else if (component.getType() == ComponentType.AXIS) {
                axes++;
            }
        }
        String name = ((inputDevice.getName() == null ? "" : inputDevice.getName()) + " "
                + (inputDevice.getProductName() == null ? "" : inputDevice.getProductName())).toLowerCase(Locale.ROOT);
        if (name.contains("keyboard")
                || name.contains("mouse")
                || name.contains("touchpad")
                || name.contains("consumer control")
                || name.contains("volume")
                || name.contains("power button")) {
            return false;
        }
        return buttons >= 2 && (axes >= 1 || buttons >= 4);
    }

    private static ControllerBindingDescriptor descriptorFor(InputComponent component) {
        String componentName = rawComponentId(component);
        if (componentName == null) {
            return null;
        }
        if (component.getType() == ComponentType.BUTTON) {
            return switch (componentName) {
                case "DPAD_UP" -> ControllerBindingDescriptor.pov(ControllerBindingDescriptor.Direction.UP);
                case "DPAD_RIGHT" -> ControllerBindingDescriptor.pov(ControllerBindingDescriptor.Direction.RIGHT);
                case "DPAD_DOWN" -> ControllerBindingDescriptor.pov(ControllerBindingDescriptor.Direction.DOWN);
                case "DPAD_LEFT" -> ControllerBindingDescriptor.pov(ControllerBindingDescriptor.Direction.LEFT);
                default -> ControllerBindingDescriptor.button(componentName);
            };
        }
        if (component.getType() == ComponentType.AXIS) {
            String triggerButtonId = ControllerBindingDescriptor.triggerButtonId(componentName);
            if (triggerButtonId != null) {
                return ControllerBindingDescriptor.button(triggerButtonId);
            }
            return ControllerBindingDescriptor.axis(componentName, ControllerBindingDescriptor.Direction.POSITIVE);
        }
        return null;
    }

    private static String rawComponentId(InputComponent component) {
        if (component == null || component.getId() == null || component.getId().name == null) {
            return null;
        }
        String componentName = component.getId().name.trim().toUpperCase(Locale.ROOT);
        return componentName.isEmpty() ? null : componentName;
    }

    static AxisMode resolveAxisMode(String componentId, float rawValue, AxisMode previousMode) {
        if (previousMode != null) {
            return previousMode;
        }
        if (isTriggerAxisComponent(componentId)) {
            return rawValue <= ONE_SIDED_AXIS_NEGATIVE_REST_THRESHOLD
                    ? AxisMode.ONE_SIDED_NEGATIVE_REST
                    : AxisMode.ONE_SIDED_ZERO_REST;
        }
        if (Float.isFinite(rawValue)
                && rawValue <= ONE_SIDED_AXIS_NEGATIVE_REST_THRESHOLD
                && !looksBidirectionalAxis(componentId)) {
            return AxisMode.ONE_SIDED_NEGATIVE_REST;
        }
        if (Float.isFinite(rawValue)
                && rawValue >= -ONE_SIDED_AXIS_ZERO_REST_THRESHOLD
                && !looksBidirectionalAxis(componentId)) {
            return AxisMode.ONE_SIDED_ZERO_REST;
        }
        return AxisMode.BIDIRECTIONAL;
    }

    static float normalizeBidirectionalAxis(float rawValue) {
        if (!Float.isFinite(rawValue)) {
            return 0f;
        }
        float clamped = Math.max(-1f, Math.min(1f, rawValue));
        float magnitude = Math.abs(clamped);
        if (magnitude <= AXIS_DEADZONE) {
            return 0f;
        }
        float scaled = (magnitude - AXIS_DEADZONE) / (1f - AXIS_DEADZONE);
        return Math.copySign(Math.min(1f, scaled), clamped);
    }

    static float normalizeOneSidedAxis(float rawValue, AxisMode axisMode) {
        if (!Float.isFinite(rawValue)) {
            return 0f;
        }
        float normalized;
        if (axisMode == AxisMode.ONE_SIDED_NEGATIVE_REST) {
            float clamped = Math.max(-1f, Math.min(1f, rawValue));
            normalized = (clamped + 1f) * 0.5f;
        } else {
            normalized = Math.max(0f, Math.min(1f, rawValue));
        }
        if (normalized <= AXIS_DEADZONE) {
            return 0f;
        }
        return Math.min(1f, (normalized - AXIS_DEADZONE) / (1f - AXIS_DEADZONE));
    }

    private static boolean looksBidirectionalAxis(String componentId) {
        if (componentId == null) {
            return false;
        }
        String normalized = componentId.trim().toUpperCase(Locale.ROOT);
        return normalized.endsWith("_X")
                || normalized.endsWith("_Y")
                || normalized.contains("THUMB_X")
                || normalized.contains("THUMB_Y")
                || normalized.contains("LEFT_AXIS")
                || normalized.contains("RIGHT_AXIS")
                || normalized.contains("DPAD");
    }

    private static boolean isTriggerAxisComponent(String componentId) {
        return ControllerBindingDescriptor.triggerButtonId(componentId) != null;
    }

    private void handleInitializationFailure(String message) {
        available = false;
        lastErrorMessage = message;
        if (!Objects.equals(lastLoggedErrorMessage, message)) {
            lastLoggedErrorMessage = message;
            OpenDoJaLog.warn(ControllerInputManager.class, message);
        }
    }

    private void clearInitializationFailure() {
        lastErrorMessage = null;
        lastLoggedErrorMessage = null;
    }

    private static void silenceInput4jLogs() {
        Logger.getLogger(INPUT4J_LOGGER_NAME).setLevel(Level.OFF);
    }

    private static final class DeviceSnapshot {
        private final InputDevice device;
        private final ControllerDeviceInfo info;

        private DeviceSnapshot(InputDevice device, ControllerDeviceInfo info) {
            this.device = device;
            this.info = info;
        }
    }

    private static final class DeviceState {
        private InputDevice device;
        private ControllerDeviceInfo info;
        private final Map<String, Boolean> activeControls = new LinkedHashMap<>();
        private final Map<String, Float> activeValues = new LinkedHashMap<>();
        private final Map<String, AxisMode> axisModes = new LinkedHashMap<>();
        private final Map<String, Boolean> oneSidedAxisReadyForPress = new LinkedHashMap<>();
        private final Map<String, Long> oneSidedAxisRestStartNanos = new LinkedHashMap<>();
        private boolean initialized;

        private DeviceState(InputDevice device, ControllerDeviceInfo info) {
            this.device = device;
            this.info = info;
        }
    }

    enum AxisMode {
        BIDIRECTIONAL,
        ONE_SIDED_NEGATIVE_REST,
        ONE_SIDED_ZERO_REST;

        boolean isOneSided() {
            return this != BIDIRECTIONAL;
        }
    }
}
