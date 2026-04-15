package opendoja.probes;

import de.gurkenlabs.input4j.ComponentType;
import de.gurkenlabs.input4j.InputComponent;
import de.gurkenlabs.input4j.InputDevice;
import de.gurkenlabs.input4j.InputDevicePlugin;
import de.gurkenlabs.input4j.InputDevices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Input4jRumbleRepro {
    private static final long ACTIVE_INPUT_WINDOW_MS = 5_000L;
    private static final long POLL_INTERVAL_MS = 20L;
    private static final long RUMBLE_DURATION_MS = 2_000L;
    private static final long PHASE_PAUSE_MS = 800L;
    private static final float RUMBLE_INTENSITY = 1.0f;
    private static final float BUTTON_ACTIVE_THRESHOLD = 0.5f;
    private static final float AXIS_ACTIVE_THRESHOLD = 0.6f;

    private Input4jRumbleRepro() {
    }

    public static void main(String[] args) throws Exception {
        String filter = args.length > 0 ? args[0].trim() : "";

        InputDevices.configure().setAccuracy(3);
        InputDevices.configure().setHotPlugInterval(3_000);

        InputDevicePlugin plugin = InputDevices.init();
        if (plugin == null) {
            throw new IllegalStateException("InputDevices.init() returned null");
        }

        try (plugin) {
            System.out.println("java=" + System.getProperty("java.version"));
            System.out.println("plugin=" + plugin.getClass().getName());
            if (!filter.isBlank()) {
                System.out.println("filter=" + filter);
            }

            List<InputDevice> allDevices = List.copyOf(plugin.getAll());
            System.out.println("allDevices=" + allDevices.size());
            printDevices("all", allDevices);

            List<InputDevice> candidateDevices = selectCandidateDevices(allDevices, filter);
            System.out.println("candidateDevices=" + candidateDevices.size());
            if (candidateDevices.isEmpty()) {
                if (allDevices.isEmpty()) {
                    throw new IllegalStateException(
                            "input4j detected no devices. On Linux, check /dev/input permissions "
                                    + "and rerun after joining the input group if needed.");
                }
                throw new IllegalStateException(
                        "No controller-like devices matched the current filter: " + filter);
            }
            printDevices("candidate", candidateDevices);

            System.out.println();
            System.out.println("Press any control on the target controller within " + ACTIVE_INPUT_WINDOW_MS + " ms");
            InputDevice activeDevice = detectActiveDevice(candidateDevices, ACTIVE_INPUT_WINDOW_MS);
            if (activeDevice != null) {
                System.out.println("activeDevice=" + describeDevice(activeDevice));
                pulseDevices("active-device", List.of(activeDevice));
            } else {
                System.out.println("activeDevice=<none detected>");
            }

            for (InputDevice device : candidateDevices) {
                pulseDevices("single-device " + describeDevice(device), List.of(device));
            }

            Map<String, List<InputDevice>> groups = groupByPhysicalSignature(candidateDevices);
            for (Map.Entry<String, List<InputDevice>> entry : groups.entrySet()) {
                pulseDevices("group " + entry.getKey(), entry.getValue());
            }

            pulseDevices("all-candidate-devices", candidateDevices);
            System.out.println("repro-complete");
        }
    }

    private static void printDevices(String prefix, Collection<InputDevice> devices) {
        int index = 0;
        for (InputDevice device : devices) {
            int buttons = 0;
            int axes = 0;
            List<String> componentNames = new ArrayList<>();
            for (InputComponent component : device.getComponents()) {
                if (component == null || component.isRelative()) {
                    continue;
                }
                if (component.getType() == ComponentType.BUTTON) {
                    buttons++;
                } else if (component.getType() == ComponentType.AXIS) {
                    axes++;
                }
                if (component.getId() != null && component.getId().name != null) {
                    componentNames.add(component.getId().name);
                }
            }
            System.out.println(prefix + "[" + index + "] id=" + device.getID());
            System.out.println(prefix + "[" + index + "] name=" + device.getName());
            System.out.println(prefix + "[" + index + "] product=" + device.getProductName());
            System.out.println(prefix + "[" + index + "] buttons=" + buttons + " axes=" + axes);
            System.out.println(prefix + "[" + index + "] components=" + abbreviate(componentNames, 24));
            index++;
        }
    }

    private static List<InputDevice> selectCandidateDevices(Collection<InputDevice> devices, String filter) {
        List<InputDevice> selected = new ArrayList<>();
        for (InputDevice device : devices) {
            if (!isControllerLike(device) || !matchesFilter(device, filter)) {
                continue;
            }
            selected.add(device);
        }
        return List.copyOf(selected);
    }

    private static boolean isControllerLike(InputDevice device) {
        if (device == null) {
            return false;
        }

        String combinedName = ((device.getName() == null ? "" : device.getName()) + " "
                + (device.getProductName() == null ? "" : device.getProductName())).toLowerCase(Locale.ROOT);
        if (combinedName.contains("keyboard")
                || combinedName.contains("mouse")
                || combinedName.contains("touchpad")
                || combinedName.contains("consumer control")
                || combinedName.contains("power button")
                || combinedName.contains("video bus")
                || combinedName.contains("hdmi")) {
            return false;
        }

        int buttons = 0;
        int axes = 0;
        for (InputComponent component : device.getComponents()) {
            if (component == null || component.isRelative()) {
                continue;
            }
            if (component.getType() == ComponentType.BUTTON) {
                buttons++;
            } else if (component.getType() == ComponentType.AXIS) {
                axes++;
            }
        }
        return buttons > 0 || axes > 0;
    }

    private static boolean matchesFilter(InputDevice device, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(device.getID(), needle)
                || containsIgnoreCase(device.getName(), needle)
                || containsIgnoreCase(device.getProductName(), needle);
    }

    private static boolean containsIgnoreCase(String value, String needleLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private static InputDevice detectActiveDevice(List<InputDevice> devices, long windowMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + windowMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            for (InputDevice device : devices) {
                device.poll();
                if (hasMeaningfulActivity(device)) {
                    return device;
                }
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return null;
    }

    private static boolean hasMeaningfulActivity(InputDevice device) {
        for (InputComponent component : device.getComponents()) {
            if (component == null || component.isRelative()) {
                continue;
            }
            float value = component.getData();
            if (component.getType() == ComponentType.BUTTON && Math.abs(value) >= BUTTON_ACTIVE_THRESHOLD) {
                return true;
            }
            if (component.getType() == ComponentType.AXIS && Math.abs(value) >= AXIS_ACTIVE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<InputDevice>> groupByPhysicalSignature(List<InputDevice> devices) {
        Map<String, LinkedHashSet<InputDevice>> grouped = new LinkedHashMap<>();
        for (InputDevice device : devices) {
            grouped.computeIfAbsent(physicalSignature(device), ignored -> new LinkedHashSet<>()).add(device);
        }

        Map<String, List<InputDevice>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<InputDevice>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return result;
    }

    private static String physicalSignature(InputDevice device) {
        String product = normalizeToken(device.getProductName());
        String name = normalizeToken(device.getName());
        if (!product.isBlank() && !name.isBlank() && !Objects.equals(product, name)) {
            return product + "." + name;
        }
        if (!product.isBlank()) {
            return product;
        }
        if (!name.isBlank()) {
            return name;
        }
        return normalizeToken(device.getID());
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
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

    private static void pulseDevices(String label, List<InputDevice> devices) throws InterruptedException {
        if (devices.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("phase-start=" + label);
        List<InputDevice> startedDevices = new ArrayList<>();
        for (InputDevice device : devices) {
            System.out.println("phase-target=" + describeDevice(device));
            try {
                device.rumble(RUMBLE_INTENSITY, RUMBLE_INTENSITY);
                startedDevices.add(device);
            } catch (RuntimeException exception) {
                printPhaseException("rumble-start-error", label, device, exception);
            }
        }

        try {
            if (!startedDevices.isEmpty()) {
                System.out.println("phase-rumble-started=" + startedDevices.size());
            }
            Thread.sleep(RUMBLE_DURATION_MS);
        } finally {
            stopAll(label, startedDevices.isEmpty() ? devices : startedDevices);
        }

        Thread.sleep(PHASE_PAUSE_MS);
        System.out.println("phase-end=" + label);
    }

    private static void stopAll(String label, Collection<InputDevice> devices) {
        for (InputDevice device : devices) {
            try {
                device.rumble(0.0f, 0.0f);
            } catch (RuntimeException exception) {
                printPhaseException("rumble-stop-error", label, device, exception);
            }
        }
    }

    private static void printPhaseException(String kind, String label, InputDevice device, RuntimeException exception) {
        System.out.println(kind + "=" + label);
        System.out.println(kind + "-device=" + describeDevice(device));
        System.out.println(kind + "-type=" + exception.getClass().getName());
        System.out.println(kind + "-message=" + String.valueOf(exception.getMessage()));
        exception.printStackTrace(System.out);
    }

    private static String describeDevice(InputDevice device) {
        return "id=" + device.getID()
                + " name=" + device.getName()
                + " product=" + device.getProductName();
    }

    private static String abbreviate(List<String> values, int maxItems) {
        if (values.isEmpty()) {
            return "[]";
        }
        if (values.size() <= maxItems) {
            return values.toString();
        }
        List<String> head = values.subList(0, maxItems);
        return head + " ... (" + values.size() + " total)";
    }
}
