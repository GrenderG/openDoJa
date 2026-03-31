package com.nttdocomo.opt.device;

import com.nttdocomo.device.DeviceException;
import com.nttdocomo.fs.File;
import com.nttdocomo.system.StoreException;
import opendoja.host.DoJaEncoding;
import opendoja.host.device.DoJaCameraSupport;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class _OptionalDeviceSupport {
    private static final FingerprintAuthenticator FINGERPRINT_AUTHENTICATOR = new FingerprintAuthenticator();
    private static final IrReceiver IR_RECEIVER = new IrReceiver();
    private static final SharedIrInputStream IR_STREAM = new SharedIrInputStream();
    private static final MediaPlayer MEDIA_PLAYER = new MediaPlayer();
    private static final Pedometer PEDOMETER = new Pedometer();
    private static final Map<Integer, Pulsemeter> PULSEMETERS = new HashMap<>();
    private static final Map<Pulsemeter, PulseState> PULSE_STATES = new IdentityHashMap<>();
    private static final Object RADIO_SEEK_LOCK = new Object();
    private static final int[] IR_ATTRIBUTES = {
            IrReceiver.TTIME_MAX, IrReceiver.TTIME_MIN,
            IrReceiver.START_HIGH_MAX, IrReceiver.START_HIGH_MIN,
            IrReceiver.START_LOW_MAX, IrReceiver.START_LOW_MIN,
            IrReceiver.DATA0_HIGH_MAX, IrReceiver.DATA0_HIGH_MIN,
            IrReceiver.DATA0_LOW_MAX, IrReceiver.DATA0_LOW_MIN,
            IrReceiver.DATA1_HIGH_MAX, IrReceiver.DATA1_HIGH_MIN,
            IrReceiver.DATA1_LOW_MAX, IrReceiver.DATA1_LOW_MIN,
            IrReceiver.STOP_HIGH_MAX, IrReceiver.STOP_HIGH_MIN,
            IrReceiver.STOP_LOW_MAX, IrReceiver.STOP_LOW_MIN
    };
    private static final int DEFAULT_MIN_HEIGHT_MM = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_MIN_HEIGHT_MM);
    private static final int DEFAULT_MAX_HEIGHT_MM = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_MAX_HEIGHT_MM);
    private static final int DEFAULT_MIN_WEIGHT_G = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_MIN_WEIGHT_G);
    private static final int DEFAULT_MAX_WEIGHT_G = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_MAX_WEIGHT_G);
    private static final int HEIGHT_UNIT_MM = java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_HEIGHT_UNIT_MM));
    private static final int WEIGHT_UNIT_G = java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_WEIGHT_UNIT_G));
    private static final int DISTANCE_PER_STEP_MM = java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_DISTANCE_PER_STEP_MM));
    private static final int PULSE_WARMUP_MS = java.lang.Math.max(0, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PULSEMETER_WARMUP_MS));

    private static final Map<Integer, Integer> irAttributeValues = new HashMap<>();
    private static boolean irReceiving;
    private static long mediaPlayerLastStoppedPosition;
    private static int bodyHeightMm = roundToUnit(opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_DEFAULT_HEIGHT_MM), HEIGHT_UNIT_MM);
    private static int bodyWeightG = roundToUnit(opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_DEFAULT_WEIGHT_G), WEIGHT_UNIT_G);

    private _OptionalDeviceSupport() {
    }

    static FingerprintAuthenticator fingerprintAuthenticator() {
        return FINGERPRINT_AUTHENTICATOR;
    }

    static int fingerprintSelect() {
        int[] registered = fingerprintIds();
        int selected = selectedFingerprintId(registered);
        return contains(registered, selected) ? selected : -1;
    }

    static int fingerprintAuthenticateAll() {
        int[] registered = fingerprintIds();
        int selected = selectedFingerprintId(registered);
        return contains(registered, selected) ? selected : -1;
    }

    static boolean fingerprintAuthenticateOne(int id) throws StoreException {
        int[] registered = fingerprintIds();
        if (!contains(registered, id)) {
            throw new StoreException(StoreException.NOT_FOUND, "Fingerprint entry not found: " + id);
        }
        return id == selectedFingerprintId(registered);
    }

    static int fingerprintAuthenticateMany(int[] ids) throws StoreException {
        if (ids == null) {
            throw new NullPointerException("id");
        }
        if (ids.length == 0 || ids.length > maxFingerprintCandidates()) {
            throw new IllegalArgumentException("id");
        }
        int[] registered = fingerprintIds();
        boolean found = false;
        for (int id : ids) {
            if (contains(registered, id)) {
                found = true;
                if (id == selectedFingerprintId(registered)) {
                    return id;
                }
            }
        }
        if (!found) {
            throw new StoreException(StoreException.NOT_FOUND, "Fingerprint entries not found");
        }
        return -1;
    }

    static IrReceiver irReceiver() {
        return IR_RECEIVER;
    }

    static InputStream irInputStream() {
        return IR_STREAM;
    }

    static synchronized void irReceive() {
        if (irReceiving) {
            return;
        }
        if (!allIrAttributesConfigured() || hasInvalidIrAttributeRange()) {
            throw new com.nttdocomo.lang.IllegalStateException("Infrared receive attributes are incomplete");
        }
        irReceiving = true;
        IR_STREAM.reset(payloadBytes());
    }

    static synchronized void irCancel() {
        irReceiving = false;
        IR_STREAM.clear();
    }

    static synchronized void irSetAttribute(int attr, int value) {
        if (irReceiving) {
            throw new DeviceException(DeviceException.BUSY_RESOURCE,
                    "Cannot change infrared attributes while receiving");
        }
        if (!isKnownIrAttribute(attr)) {
            return;
        }
        if (value < 0) {
            throw new IllegalArgumentException("value");
        }
        irAttributeValues.put(attr, value);
    }

    static synchronized void markIrReadComplete() {
        irReceiving = false;
    }

    static MediaPlayer mediaPlayer() {
        return MEDIA_PLAYER;
    }

    static synchronized int playMedia(File file, long position) throws IOException {
        if (file == null) {
            throw new NullPointerException("file");
        }
        if (position < 0L) {
            throw new IllegalArgumentException("position");
        }
        file.getLength();
        long configured = opendoja.host.OpenDoJaLaunchArgs.getLong(opendoja.host.OpenDoJaLaunchArgs.MEDIAPLAYER_STOP_POSITION, position);
        mediaPlayerLastStoppedPosition = java.lang.Math.max(0L, configured);
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.MEDIAPLAYER_STATUS);
    }

    static synchronized long lastStoppedPosition() {
        return mediaPlayerLastStoppedPosition;
    }

    static Pedometer pedometer() {
        return PEDOMETER;
    }

    static int pedometerAttribute(int attr) {
        if (attr == Pedometer.DEV_PEDOMETER) {
            return pedometerState();
        }
        return -1;
    }

    static int pedometerCount() {
        return pedometerState() == Pedometer.ATTR_PEDOMETER_OFF ? 0 : pedometerHistory().size();
    }

    static int pedometerTotalSteps() {
        if (pedometerState() == Pedometer.ATTR_PEDOMETER_OFF) {
            return 0;
        }
        int total = 0;
        for (PedometerData data : pedometerHistory()) {
            total += data.getNumberOfSteps();
        }
        return total;
    }

    static int pedometerTotalDistance() {
        if (pedometerState() == Pedometer.ATTR_PEDOMETER_OFF) {
            return 0;
        }
        int total = 0;
        for (PedometerData data : pedometerHistory()) {
            total += data.getDistance();
        }
        return total;
    }

    static PedometerData todayPedometerData() {
        ensurePedometerReadable();
        return pedometerHistory().get(0);
    }

    static PedometerData[] pedometerData() {
        ensurePedometerReadable();
        List<PedometerData> history = pedometerHistory();
        return history.toArray(PedometerData[]::new);
    }

    static PedometerData[] pedometerData(int index, int articles) {
        ensurePedometerReadable();
        if (index < 0) {
            throw new IllegalArgumentException("index");
        }
        if (articles <= 0) {
            throw new IllegalArgumentException("articles");
        }
        List<PedometerData> history = pedometerHistory();
        if (index + articles > history.size()) {
            throw new IllegalArgumentException("index + articles");
        }
        return history.subList(index, index + articles).toArray(PedometerData[]::new);
    }

    static synchronized void setBodyHeight(int height) {
        if (height < DEFAULT_MIN_HEIGHT_MM || height > DEFAULT_MAX_HEIGHT_MM) {
            throw new IllegalArgumentException("height");
        }
        bodyHeightMm = roundToUnit(height, HEIGHT_UNIT_MM);
    }

    static synchronized void setBodyWeight(int weight) {
        if (weight < DEFAULT_MIN_WEIGHT_G || weight > DEFAULT_MAX_WEIGHT_G) {
            throw new IllegalArgumentException("weight");
        }
        bodyWeightG = roundToUnit(weight, WEIGHT_UNIT_G);
    }

    static synchronized int pedometerSetting(int attr) {
        return switch (attr) {
            case PedometerSettings.ATTR_HEIGHT_MIN -> DEFAULT_MIN_HEIGHT_MM;
            case PedometerSettings.ATTR_HEIGHT_MAX -> DEFAULT_MAX_HEIGHT_MM;
            case PedometerSettings.ATTR_WEIGHT_MIN -> DEFAULT_MIN_WEIGHT_G;
            case PedometerSettings.ATTR_WEIGHT_MAX -> DEFAULT_MAX_WEIGHT_G;
            default -> throw new IllegalArgumentException("attr");
        };
    }

    static synchronized Pulsemeter pulsemeter(int id) {
        DoJaCameraSupport.validateCameraId(id);
        return PULSEMETERS.computeIfAbsent(id, key -> {
            Pulsemeter pulsemeter = new Pulsemeter();
            PULSE_STATES.put(pulsemeter, new PulseState(id));
            return pulsemeter;
        });
    }

    static synchronized void startPulsemeter(Pulsemeter pulsemeter) {
        PulseState state = pulseState(pulsemeter);
        if (state.started) {
            return;
        }
        DoJaCameraSupport.startPulsemeter(pulsemeter, state.cameraId);
        state.started = true;
        state.startMillis = System.currentTimeMillis();
        state.lastPulserate = -1;
        state.failureStatus = null;
    }

    static synchronized void stopPulsemeter(Pulsemeter pulsemeter) {
        PulseState state = pulseState(pulsemeter);
        if (!state.started) {
            return;
        }
        state.lastPulserate = currentPulserate(state);
        state.started = false;
        DoJaCameraSupport.stopPulsemeter(pulsemeter);
    }

    static synchronized int pulsemeterStatus(Pulsemeter pulsemeter) {
        PulseState state = pulseState(pulsemeter);
        if (!state.started) {
            return Pulsemeter.STATUS_STOP;
        }
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PULSEMETER_STATUS);
    }

    static synchronized int pulsemeterRate(Pulsemeter pulsemeter) {
        PulseState state = pulseState(pulsemeter);
        if (state.failureStatus != null) {
            throw new DeviceException(state.failureStatus, "Pulse measurement ended abnormally");
        }
        if (state.started) {
            state.lastPulserate = currentPulserate(state);
            return state.lastPulserate;
        }
        return state.lastPulserate;
    }

    static int[] stationsForTuner(int tunerType) {
        String property = System.getProperty(tunerType == RadioTuner.TUNERTYPE_FM
                ? opendoja.host.OpenDoJaLaunchArgs.RADIOTUNER_FM_STATIONS
                : opendoja.host.OpenDoJaLaunchArgs.RADIOTUNER_AM_STATIONS);
        if (property == null || property.isBlank()) {
            return tunerType == RadioTuner.TUNERTYPE_FM
                    ? new int[]{78000, 82500, 90000}
                    : new int[]{594, 954, 1134};
        }
        return Arrays.stream(property.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .mapToInt(Integer::parseInt)
                .sorted()
                .toArray();
    }

    static Object radioSeekLock() {
        return RADIO_SEEK_LOCK;
    }

    private static PulseState pulseState(Pulsemeter pulsemeter) {
        PulseState state = PULSE_STATES.get(pulsemeter);
        if (state == null) {
            throw new IllegalStateException("Unknown pulsemeter instance");
        }
        return state;
    }

    private static int currentPulserate(PulseState state) {
        long elapsed = System.currentTimeMillis() - state.startMillis;
        if (elapsed < PULSE_WARMUP_MS) {
            return -1;
        }
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PULSEMETER_RATE);
    }

    private static void ensurePedometerReadable() {
        if (pedometerState() == Pedometer.ATTR_PEDOMETER_OFF) {
            throw new SecurityException("Pedometer data cannot be read while measurement is off");
        }
    }

    private static int pedometerState() {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_OFF)
                ? Pedometer.ATTR_PEDOMETER_OFF
                : Pedometer.ATTR_PEDOMETER_ON;
    }

    private static List<PedometerData> pedometerHistory() {
        int days = java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_HISTORY_DAYS));
        int todaySteps = java.lang.Math.max(0, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.PEDOMETER_TODAY_STEPS));
        List<PedometerData> history = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            int steps = java.lang.Math.max(0, todaySteps - i * 450);
            int distance = (steps * DISTANCE_PER_STEP_MM) / 1000;
            int aerobicsSteps = java.lang.Math.max(0, steps / 2);
            int aerobicsTime = java.lang.Math.max(0, aerobicsSteps / 100);
            int calories = java.lang.Math.max(0, steps / 40);
            int fat = java.lang.Math.max(0, steps / 200);
            history.add(new PedometerData(atMidnight(today.minusDays(i)), steps, distance, aerobicsSteps, aerobicsTime, calories, fat));
        }
        return history;
    }

    private static Date atMidnight(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static int[] fingerprintIds() {
        String property = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.FINGERPRINT_IDS);
        return Arrays.stream(property.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    private static int selectedFingerprintId(int[] registered) {
        if (registered.length == 0) {
            return -1;
        }
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.FINGERPRINT_MATCH_ID, registered[0]);
    }

    private static int maxFingerprintCandidates() {
        return java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.FINGERPRINT_MAX_CANDIDATES));
    }

    private static boolean contains(int[] values, int candidate) {
        for (int value : values) {
            if (value == candidate) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownIrAttribute(int attr) {
        for (int known : IR_ATTRIBUTES) {
            if (known == attr) {
                return true;
            }
        }
        return false;
    }

    private static boolean allIrAttributesConfigured() {
        for (int attr : IR_ATTRIBUTES) {
            if (!irAttributeValues.containsKey(attr)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasInvalidIrAttributeRange() {
        return invalidRange(IrReceiver.TTIME_MIN, IrReceiver.TTIME_MAX)
                || invalidRange(IrReceiver.START_HIGH_MIN, IrReceiver.START_HIGH_MAX)
                || invalidRange(IrReceiver.START_LOW_MIN, IrReceiver.START_LOW_MAX)
                || invalidRange(IrReceiver.DATA0_HIGH_MIN, IrReceiver.DATA0_HIGH_MAX)
                || invalidRange(IrReceiver.DATA0_LOW_MIN, IrReceiver.DATA0_LOW_MAX)
                || invalidRange(IrReceiver.DATA1_HIGH_MIN, IrReceiver.DATA1_HIGH_MAX)
                || invalidRange(IrReceiver.DATA1_LOW_MIN, IrReceiver.DATA1_LOW_MAX)
                || invalidRange(IrReceiver.STOP_HIGH_MIN, IrReceiver.STOP_HIGH_MAX)
                || invalidRange(IrReceiver.STOP_LOW_MIN, IrReceiver.STOP_LOW_MAX);
    }

    private static boolean invalidRange(int minAttr, int maxAttr) {
        Integer min = irAttributeValues.get(minAttr);
        Integer max = irAttributeValues.get(maxAttr);
        return min != null && max != null && min > max;
    }

    private static byte[] payloadBytes() {
        return opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.IRRECEIVER_DATA)
                .getBytes(DoJaEncoding.DEFAULT_CHARSET);
    }

    private static int roundToUnit(int value, int unit) {
        int half = unit / 2;
        return ((value + half) / unit) * unit;
    }

    private static final class SharedIrInputStream extends InputStream {
        private byte[] data = new byte[0];
        private int position;

        @Override
        public synchronized int read() {
            if (position >= data.length) {
                _OptionalDeviceSupport.markIrReadComplete();
                return -1;
            }
            return data[position++] & 0xFF;
        }

        private synchronized void reset(byte[] data) {
            this.data = data == null ? new byte[0] : data.clone();
            this.position = 0;
        }

        private synchronized void clear() {
            this.data = new byte[0];
            this.position = 0;
        }
    }

    private static final class PulseState {
        private final int cameraId;
        private boolean started;
        private long startMillis;
        private Integer failureStatus;
        private int lastPulserate = -1;

        private PulseState(int cameraId) {
            this.cameraId = cameraId;
        }
    }
}
