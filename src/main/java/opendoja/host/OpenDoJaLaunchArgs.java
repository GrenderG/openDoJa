package opendoja.host;

import opendoja.audio.mld.MLDSynth;

import java.util.*;
import java.util.function.Supplier;

/**
 * Canonical registry for every custom {@code opendoja.*} system property understood by openDoJa.
 * Keep property keys, common property-access helpers, and launcher-help enumeration here so callers
 * do not scatter literal property names across the codebase.
 */
public final class OpenDoJaLaunchArgs {
    /** Acceleration data types reported as available. */
    public static final String ACCELERATION_AVAILABLE_DATA = "opendoja.accelerationAvailableData";
    /** Acceleration events reported as available. */
    public static final String ACCELERATION_AVAILABLE_EVENT = "opendoja.accelerationAvailableEvent";
    /** Current simulated acceleration sample tuple. */
    public static final String ACCELERATION_CURRENT = "opendoja.accelerationCurrent";
    /** Value returned for double-tap acceleration events. */
    public static final String ACCELERATION_EVENT_DOUBLE_TAP = "opendoja.accelerationEventDoubleTap";
    /** Value returned for screen-orientation acceleration events. */
    public static final String ACCELERATION_EVENT_SCREEN_ORIENTATION = "opendoja.accelerationEventScreenOrientation";
    /** Whether acceleration event notifications are exposed. */
    public static final String ACCELERATION_EVENT_SUPPORTED = "opendoja.accelerationEventSupported";
    /** Reported acceleration polling resolution in ms. */
    public static final String ACCELERATION_INTERVAL_RESOLUTION = "opendoja.accelerationIntervalResolution";
    /** Maximum acceleration samples returned per read. */
    public static final String ACCELERATION_MAX_DATA_SIZE = "opendoja.accelerationMaxDataSize";
    /** Whether acceleration sensor support is exposed. */
    public static final String ACCELERATION_SUPPORTED = "opendoja.accelerationSupported";
    /** Number of Audio3D resources the host exposes. */
    public static final String AUDIO3D_RESOURCES = "opendoja.audio3dResources";
    /** Number of explicit AudioPresenter ports the host exposes. */
    public static final String AUDIO_PRESENTER_PORTS = "opendoja.audioPresenterPorts";
    /** Reported Audio3D timer resolution in ms. */
    public static final String AUDIO3D_TIME_RESOLUTION_MS = "opendoja.audio3dTimeResolutionMs";
    /** Maximum cached bitmap font render entries. */
    public static final String BITMAP_FONT_CACHE_ENTRIES = "opendoja.bitmapFontCacheEntries";
    /** Configured mock Bluetooth devices for discovery. */
    public static final String BLUETOOTH_DEVICES = "opendoja.bluetoothDevices";
    /** Preferred discovered device index for scan(). */
    public static final String BLUETOOTH_SCAN_INDEX = "opendoja.bluetoothScanIndex";
    /** Preferred device index for search-and-select. */
    public static final String BLUETOOTH_SEARCH_SELECTION_INDEX = "opendoja.bluetoothSearchSelectionIndex";
    /** Preferred device index for selectDevice(). */
    public static final String BLUETOOTH_SELECTION_INDEX = "opendoja.bluetoothSelectionIndex";
    /** Whether Bluetooth APIs are exposed. */
    public static final String BLUETOOTH_SUPPORTED = "opendoja.bluetoothSupported";
    /** Maximum movie size reported by the camera. */
    public static final String CAMERA_MAX_MOVIE_LENGTH = "opendoja.camera.maxMovieLength";
    /** Whether software 3D clips against screen planes. */
    public static final String CLIP_SCREEN_PLANES = "opendoja.clipScreenPlanes";
    /** Current simulated compass azimuth in degrees. */
    public static final String COMPASS_AZIMUTH = "opendoja.compassAzimuth";
    /** Whether compass support is exposed. */
    public static final String COMPASS_SUPPORTED = "opendoja.compassSupported";
    /** Whether UI 3D figures use face culling. */
    public static final String CULL_FIGURES = "opendoja.cullFigures";
    /** Face-culling winding sign for software 3D. */
    public static final String CULL_SIGN = "opendoja.cullSign";
    /** Enables high-level 3D debug logging. */
    public static final String DEBUG3D = "opendoja.debug3d";
    /** Enables verbose per-call 3D tracing. */
    public static final String DEBUG3D_CALLS = "opendoja.debug3dCalls";
    /** Enables FueTrek control-lane debug logging. */
    public static final String DEBUG_FUE_TREK_CONTROL = "opendoja.debugFueTrekControl";
    /** Enables FueTrek note debug logging. */
    public static final String DEBUG_FUE_TREK_NOTES = "opendoja.debugFueTrekNotes";
    /** Whether to draw the host handset frame. */
    public static final String EXTERNAL_FRAME = "opendoja.externalFrame";
    /** Disables external FeliCa speed discovery. */
    public static final String FELICA_EXTERNAL_NO_SPEED_DISCOVERY = "opendoja.felicaExternalNoSpeedDiscovery";
    /** Overrides the polled external FeliCa IDm. */
    public static final String FELICA_EXTERNAL_POLLED_IDM = "opendoja.felicaExternalPolledIdm";
    /** Whether external FeliCa supports 212 kbps. */
    public static final String FELICA_EXTERNAL_SUPPORTS212 = "opendoja.felicaExternalSupports212";
    /** Whether external FeliCa supports 424 kbps. */
    public static final String FELICA_EXTERNAL_SUPPORTS424 = "opendoja.felicaExternalSupports424";
    /** Delay before mock online FeliCa completion. */
    public static final String FELICA_ONLINE_DELAY_MILLIS = "opendoja.felicaOnlineDelayMillis";
    /** Forces mock online FeliCa resource exhaustion. */
    public static final String FELICA_ONLINE_NO_RESOURCES = "opendoja.felicaOnlineNoResources";
    /** Status code returned by mock online FeliCa. */
    public static final String FELICA_ONLINE_STATUS = "opendoja.felicaOnlineStatus";
    /** Configured mock fingerprint IDs. */
    public static final String FINGERPRINT_IDS = "opendoja.fingerprint.ids";
    /** Fingerprint ID that should authenticate successfully. */
    public static final String FINGERPRINT_MATCH_ID = "opendoja.fingerprint.matchId";
    /** Maximum fingerprint candidates accepted per match. */
    public static final String FINGERPRINT_MAX_CANDIDATES = "opendoja.fingerprint.maxCandidates";
    /** UI font renderer type: bitmap or system. */
    public static final String FONT_TYPE = "opendoja.fontType";
    /** Scale factor applied to handset font sizing. */
    public static final String FONT_SCALE = "opendoja.fontScale";
    /** Selects the FueTrek mixer profile variant. */
    public static final String FUETREK_MIX_PROFILE = "opendoja.fuetrekMixProfile";
    /** OpenGL ES backend mode: software or hardware. */
    public static final String OPEN_GLES_RENDERER = "opendoja.openGlesRenderer";
    /** Whether the OpenGLES FPS overlay is shown. */
    public static final String SHOW_OPEN_GLES_FPS = "opendoja.showOpenGlesFps";
    /** Internal supersample scale used by the hardware OpenGL ES renderer. */
    public static final String OPEN_GLES_SUPERSAMPLE_SCALE = "opendoja.openGlesSupersampleScale";
    /** Enables explicit AudioPresenter lifecycle tracing. */
    /** Enables hardware/software OpenGLES sync tracing. */
    public static final String TRACE_OPEN_GLES_SYNC = "opendoja.traceOpenGlesSync";
    /** Reported GPS accuracy constant. */
    public static final String GPS_ACCURACY = "opendoja.gpsAccuracy";
    /** Reported GPS altitude. */
    public static final String GPS_ALTITUDE = "opendoja.gpsAltitude";
    /** Reported GPS datum constant. */
    public static final String GPS_DATUM = "opendoja.gpsDatum";
    /** Delay before mock GPS completion. */
    public static final String GPS_DELAY_MILLIS = "opendoja.gpsDelayMillis";
    /** Configured GPS failure mode keyword. */
    public static final String GPS_FAILURE = "opendoja.gpsFailure";
    /** Reported GPS latitude in degrees. */
    public static final String GPS_LATITUDE = "opendoja.gpsLatitude";
    /** Reported GPS longitude in degrees. */
    public static final String GPS_LONGITUDE = "opendoja.gpsLongitude";
    /** Reported minimum GPS interval in ms. */
    public static final String GPS_MINIMAL_INTERVAL = "opendoja.gpsMinimalInterval";
    /** Whether GPS positioning is exposed. */
    public static final String GPS_SUPPORTED = "opendoja.gpsSupported";
    /** Whether GPS tracking mode is exposed. */
    public static final String GPS_TRACKING_SUPPORTED = "opendoja.gpsTrackingSupported";
    /** Fixed integer host scale, or {@code fullscreen} for platform fullscreen. */
    public static final String HOST_SCALE = "opendoja.hostScale";
    /** Automated IME response used by host-side probes instead of showing a desktop dialog. */
    public static final String IME_AUTOMATION_RESPONSE = "opendoja.imeAutomationResponse";
    /** Delay in milliseconds before the automated IME response is delivered. */
    public static final String IME_AUTOMATION_DELAY_MS = "opendoja.imeAutomationDelayMs";
    /** Automated zero-based PhoneBook selection index used by host-side probes. */
    public static final String PHONEBOOK_SELECTION_INDEX = "opendoja.phonebookSelectionIndex";
    /** Hostname to force for outbound HTTP requests. */
    public static final String HTTP_OVERRIDE_DOMAIN = "opendoja.httpOverrideDomain";
    /** Override value returned for microedition.platform during launch. */
    public static final String MICROEDITION_PLATFORM_OVERRIDE = "opendoja.microeditionPlatformOverride";
    /** Simulated handset terminal ID returned to apps. */
    public static final String TERMINAL_ID = "opendoja.terminalId";
    /** Simulated user ID returned to apps. */
    public static final String USER_ID = "opendoja.userId";
    /** Debounce window for repeated key releases. */
    public static final String INPUT_KEY_REPEAT_RELEASE_DEBOUNCE_MS = "opendoja.input.keyRepeatReleaseDebounceMs";
    /** Serialized host input bindings for the active launcher profile. */
    public static final String INPUT_BINDINGS = "opendoja.input.bindings";
    /** Minimum select-key press duration in ms. */
    public static final String INPUT_MINIMUM_SELECT_PRESS_MS = "opendoja.input.minimumSelectPressMs";
    /** Payload returned by mock IR receive. */
    public static final String IRRECEIVER_DATA = "opendoja.irreceiver.data";
    /** Keeps explicit System.gc() calls enabled. */
    public static final String KEEP_EXPLICIT_GC = "opendoja.keepExplicitGc";
    /** Marks that launch compatibility tweaks were applied. */
    public static final String LAUNCH_COMPAT_APPLIED = "opendoja.launchCompatApplied";
    /** Marks that the one-time VerifyError fallback re-exec was applied. */
    public static final String VERIFY_FALLBACK_APPLIED = "opendoja.verifyFallbackApplied";
    /** Minimum OpenDoJa log level. */
    public static final String LOG_LEVEL = "opendoja.logLevel";
    /** Fallback completion delay for visual playback shims in ms. */
    public static final String VISUAL_PLAYER_FALLBACK_DELAY_MS = "opendoja.visualPlayerFallbackDelayMs";
    /** Status returned by the mock media player. */
    public static final String MEDIAPLAYER_STATUS = "opendoja.mediaplayer.status";
    /** Stop position returned by the mock media player. */
    public static final String MEDIAPLAYER_STOP_POSITION = "opendoja.mediaplayer.stopPosition";
    /** Audio buffer size in frames for MLD playback. */
    public static final String MLD_BUFFER_FRAMES = "opendoja.mldBufferFrames";
    /** SourceDataLine buffer size in frames for MLD playback. */
    public static final String MLD_LINE_BUFFER_FRAMES = "opendoja.mldLineBufferFrames";
    /** Output sample rate for MLD playback. */
    public static final String MLD_SAMPLE_RATE = "opendoja.mldSampleRate";
    /** MLD synth backend to use. */
    public static final String MLD_SYNTH = "opendoja.mldSynth";
    /** Owner profile email address. */
    public static final String OWNER_EMAIL1 = "opendoja.owner.email1";
    /** Owner profile phone number. */
    public static final String OWNER_PHONE1 = "opendoja.owner.phone1";
    /** Default pedometer body height in mm. */
    public static final String PEDOMETER_DEFAULT_HEIGHT_MM = "opendoja.pedometer.defaultHeightMm";
    /** Default pedometer body weight in g. */
    public static final String PEDOMETER_DEFAULT_WEIGHT_G = "opendoja.pedometer.defaultWeightG";
    /** Distance per pedometer step in mm. */
    public static final String PEDOMETER_DISTANCE_PER_STEP_MM = "opendoja.pedometer.distancePerStepMm";
    /** Height rounding unit for pedometer settings. */
    public static final String PEDOMETER_HEIGHT_UNIT_MM = "opendoja.pedometer.heightUnitMm";
    /** Number of mock pedometer history days. */
    public static final String PEDOMETER_HISTORY_DAYS = "opendoja.pedometer.historyDays";
    /** Maximum allowed pedometer height in mm. */
    public static final String PEDOMETER_MAX_HEIGHT_MM = "opendoja.pedometer.maxHeightMm";
    /** Maximum allowed pedometer weight in g. */
    public static final String PEDOMETER_MAX_WEIGHT_G = "opendoja.pedometer.maxWeightG";
    /** Minimum allowed pedometer height in mm. */
    public static final String PEDOMETER_MIN_HEIGHT_MM = "opendoja.pedometer.minHeightMm";
    /** Minimum allowed pedometer weight in g. */
    public static final String PEDOMETER_MIN_WEIGHT_G = "opendoja.pedometer.minWeightG";
    /** Forces the pedometer into the off state. */
    public static final String PEDOMETER_OFF = "opendoja.pedometer.off";
    /** Step count used for today's mock pedometer entry. */
    public static final String PEDOMETER_TODAY_STEPS = "opendoja.pedometer.todaySteps";
    /** Weight rounding unit for pedometer settings. */
    public static final String PEDOMETER_WEIGHT_UNIT_G = "opendoja.pedometer.weightUnitG";
    /** Maximum pointing-device Z direction value. */
    public static final String POINTING_DEVICE_MAX_DIRECTION_Z = "opendoja.pointingDeviceMaxDirectionZ";
    /** Hides pointing-device support from apps. */
    public static final String POINTING_DEVICE_UNAVAILABLE = "opendoja.pointingDeviceUnavailable";
    /** Pulse rate returned by the mock pulsemeter. */
    public static final String PULSEMETER_RATE = "opendoja.pulsemeter.rate";
    /** Status returned by the mock pulsemeter. */
    public static final String PULSEMETER_STATUS = "opendoja.pulsemeter.status";
    /** Warmup time before pulsemeter readings start. */
    public static final String PULSEMETER_WARMUP_MS = "opendoja.pulsemeter.warmupMs";
    /** Configured AM station list. */
    public static final String RADIOTUNER_AM_STATIONS = "opendoja.radiotuner.amStations";
    /** Configured FM station list. */
    public static final String RADIOTUNER_FM_STATIONS = "opendoja.radiotuner.fmStations";
    /** Delay applied to radio seek completion. */
    public static final String RADIOTUNER_SEEK_DELAY_MS = "opendoja.radiotuner.seekDelayMs";
    /** PCM chunk size for sampled audio playback. */
    public static final String SAMPLED_CHUNK_BYTES = "opendoja.sampledChunkBytes";
    /** Minimum short-timer interval in ms. */
    public static final String SHORT_TIMER_MIN_TIME_INTERVAL = "opendoja.shortTimerMinTimeInterval";
    /** Reported short-timer resolution in ms. */
    public static final String SHORT_TIMER_RESOLUTION = "opendoja.shortTimerResolution";
    /** Supported speech frontend codec list. */
    public static final String SPEECH_CODECS = "opendoja.speechCodecs";
    /** Mock speech feature chunks emitted during capture. */
    public static final String SPEECH_FEATURE_CHUNKS = "opendoja.speechFeatureChunks";
    /** Interval between speech feature chunks in ms. */
    public static final String SPEECH_FEATURE_INTERVAL = "opendoja.speechFeatureInterval";
    /** Speech level reported with mock feature data. */
    public static final String SPEECH_LEVEL = "opendoja.speechLevel";
    /** Maximum speech capture time in ms. */
    public static final String SPEECH_MAX_SPEECH_TIME = "opendoja.speechMaxSpeechTime";
    /** Speech frontend name/version string. */
    public static final String SPEECH_NAME = "opendoja.speechName";
    /** Speech frontend ready time in ms. */
    public static final String SPEECH_READY_TIME = "opendoja.speechReadyTime";
    /** Mock speech signal-to-noise ratio. */
    public static final String SPEECH_SIGNAL_TO_NOISE_RATIO = "opendoja.speechSignalToNoiseRatio";
    /** Whether speech frontend support is exposed. */
    public static final String SPEECH_SUPPORTED = "opendoja.speechSupported";
    /** Supported speech recognition result types. */
    public static final String SPEECH_TYPES = "opendoja.speechTypes";
    /** Mock speech voice-activity indicator. */
    public static final String SPEECH_VOICE_ACTIVITY = "opendoja.speechVoiceActivity";
    /** Device name used for status-bar icons. */
    public static final String STATUS_BAR_ICON_DEVICE = "opendoja.statusBarIconDevice";
    /** Reported sub-display height in pixels. */
    public static final String SUB_DISPLAY_HEIGHT = "opendoja.subDisplayHeight";
    /** Reported sub-display width in pixels. */
    public static final String SUB_DISPLAY_WIDTH = "opendoja.subDisplayWidth";
    /** Java text antialias hint name. */
    public static final String TEXT_ANTIALIAS = "opendoja.textAntialias";
    /** Logs audio failures before rethrowing. */
    public static final String TRACE_AUDIO_FAILURES = "opendoja.traceAudioFailures";
    /** Logs high-level input and runtime events. */
    public static final String TRACE_EVENTS = "opendoja.traceEvents";
    /** Logs API failure details before rethrowing. */
    public static final String TRACE_FAILURES = "opendoja.traceFailures";
    /** Scale factor for UI 3D figure vertices. */
    public static final String UI_FIGURE_VERTEX_SCALE = "opendoja.uiFigureVertexScale";

    private static final List<String> PROPERTIES = List.of(
            ACCELERATION_AVAILABLE_DATA,
            ACCELERATION_AVAILABLE_EVENT,
            ACCELERATION_CURRENT,
            ACCELERATION_EVENT_DOUBLE_TAP,
            ACCELERATION_EVENT_SCREEN_ORIENTATION,
            ACCELERATION_EVENT_SUPPORTED,
            ACCELERATION_INTERVAL_RESOLUTION,
            ACCELERATION_MAX_DATA_SIZE,
            ACCELERATION_SUPPORTED,
            AUDIO3D_RESOURCES,
            AUDIO_PRESENTER_PORTS,
            AUDIO3D_TIME_RESOLUTION_MS,
            BITMAP_FONT_CACHE_ENTRIES,
            BLUETOOTH_DEVICES,
            BLUETOOTH_SCAN_INDEX,
            BLUETOOTH_SEARCH_SELECTION_INDEX,
            BLUETOOTH_SELECTION_INDEX,
            BLUETOOTH_SUPPORTED,
            CAMERA_MAX_MOVIE_LENGTH,
            CLIP_SCREEN_PLANES,
            COMPASS_AZIMUTH,
            COMPASS_SUPPORTED,
            CULL_FIGURES,
            CULL_SIGN,
            DEBUG3D,
            DEBUG3D_CALLS,
            DEBUG_FUE_TREK_CONTROL,
            DEBUG_FUE_TREK_NOTES,
            EXTERNAL_FRAME,
            FELICA_EXTERNAL_NO_SPEED_DISCOVERY,
            FELICA_EXTERNAL_POLLED_IDM,
            FELICA_EXTERNAL_SUPPORTS212,
            FELICA_EXTERNAL_SUPPORTS424,
            FELICA_ONLINE_DELAY_MILLIS,
            FELICA_ONLINE_NO_RESOURCES,
            FELICA_ONLINE_STATUS,
            FINGERPRINT_IDS,
            FINGERPRINT_MATCH_ID,
            FINGERPRINT_MAX_CANDIDATES,
            FONT_TYPE,
            FONT_SCALE,
            FUETREK_MIX_PROFILE,
            OPEN_GLES_RENDERER,
            SHOW_OPEN_GLES_FPS,
            OPEN_GLES_SUPERSAMPLE_SCALE,
            TRACE_OPEN_GLES_SYNC,
            GPS_ACCURACY,
            GPS_ALTITUDE,
            GPS_DATUM,
            GPS_DELAY_MILLIS,
            GPS_FAILURE,
            GPS_LATITUDE,
            GPS_LONGITUDE,
            GPS_MINIMAL_INTERVAL,
            GPS_SUPPORTED,
            GPS_TRACKING_SUPPORTED,
            HOST_SCALE,
            IME_AUTOMATION_RESPONSE,
            IME_AUTOMATION_DELAY_MS,
            PHONEBOOK_SELECTION_INDEX,
            HTTP_OVERRIDE_DOMAIN,
            MICROEDITION_PLATFORM_OVERRIDE,
            TERMINAL_ID,
            USER_ID,
            INPUT_KEY_REPEAT_RELEASE_DEBOUNCE_MS,
            INPUT_BINDINGS,
            INPUT_MINIMUM_SELECT_PRESS_MS,
            IRRECEIVER_DATA,
            KEEP_EXPLICIT_GC,
            LOG_LEVEL,
            VISUAL_PLAYER_FALLBACK_DELAY_MS,
            MEDIAPLAYER_STATUS,
            MEDIAPLAYER_STOP_POSITION,
            MLD_BUFFER_FRAMES,
            MLD_LINE_BUFFER_FRAMES,
            MLD_SAMPLE_RATE,
            MLD_SYNTH,
            OWNER_EMAIL1,
            OWNER_PHONE1,
            PEDOMETER_DEFAULT_HEIGHT_MM,
            PEDOMETER_DEFAULT_WEIGHT_G,
            PEDOMETER_DISTANCE_PER_STEP_MM,
            PEDOMETER_HEIGHT_UNIT_MM,
            PEDOMETER_HISTORY_DAYS,
            PEDOMETER_MAX_HEIGHT_MM,
            PEDOMETER_MAX_WEIGHT_G,
            PEDOMETER_MIN_HEIGHT_MM,
            PEDOMETER_MIN_WEIGHT_G,
            PEDOMETER_OFF,
            PEDOMETER_TODAY_STEPS,
            PEDOMETER_WEIGHT_UNIT_G,
            POINTING_DEVICE_MAX_DIRECTION_Z,
            POINTING_DEVICE_UNAVAILABLE,
            PULSEMETER_RATE,
            PULSEMETER_STATUS,
            PULSEMETER_WARMUP_MS,
            RADIOTUNER_AM_STATIONS,
            RADIOTUNER_FM_STATIONS,
            RADIOTUNER_SEEK_DELAY_MS,
            SAMPLED_CHUNK_BYTES,
            SHORT_TIMER_MIN_TIME_INTERVAL,
            SHORT_TIMER_RESOLUTION,
            SPEECH_CODECS,
            SPEECH_FEATURE_CHUNKS,
            SPEECH_FEATURE_INTERVAL,
            SPEECH_LEVEL,
            SPEECH_MAX_SPEECH_TIME,
            SPEECH_NAME,
            SPEECH_READY_TIME,
            SPEECH_SIGNAL_TO_NOISE_RATIO,
            SPEECH_SUPPORTED,
            SPEECH_TYPES,
            SPEECH_VOICE_ACTIVITY,
            STATUS_BAR_ICON_DEVICE,
            SUB_DISPLAY_HEIGHT,
            SUB_DISPLAY_WIDTH,
            TEXT_ANTIALIAS,
            TRACE_AUDIO_FAILURES,
            TRACE_EVENTS,
            TRACE_FAILURES,
            UI_FIGURE_VERTEX_SCALE,
            LAUNCH_COMPAT_APPLIED,
            VERIFY_FALLBACK_APPLIED);

    private static final Map<String, Supplier<String>> DEFAULTS = buildDefaults();
    private static final Set<String> PROPERTY_SET = Set.copyOf(PROPERTIES);

    private OpenDoJaLaunchArgs() {
    }

    public static String get(String property) {
        String defaultValue = defaultValue(property);
        String raw = raw(property);
        return raw == null ? defaultValue : raw;
    }

    public static String get(String property, String defaultValue) {
        String raw = raw(property);
        return raw == null ? defaultValue : raw;
    }

    public static String microeditionPlatformOverride() {
        return normalizeMicroeditionPlatformOverride(get(MICROEDITION_PLATFORM_OVERRIDE, ""));
    }

    public static OpenGlesRendererMode openGlesRendererMode() {
        return OpenGlesRendererMode.fromId(get(OPEN_GLES_RENDERER));
    }

    public static int openGlesSupersampleScale() {
        return normalizeOpenGlesSupersampleScale(getInt(OPEN_GLES_SUPERSAMPLE_SCALE));
    }

    public static boolean isEnabled(String property) {
        return getBoolean(property);
    }

    public static boolean getBoolean(String property) {
        return Boolean.parseBoolean(get(property));
    }

    public static boolean getBoolean(String property, boolean defaultValue) {
        String raw = raw(property);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw);
    }

    public static int getInt(String property) {
        return parseInt(get(property), parseInt(defaultValue(property), 0));
    }

    public static int getInt(String property, int defaultValue) {
        return parseInt(raw(property), defaultValue);
    }

    public static long getLong(String property) {
        return parseLong(get(property), parseLong(defaultValue(property), 0L));
    }

    public static long getLong(String property, long defaultValue) {
        return parseLong(raw(property), defaultValue);
    }

    public static float getFloat(String property) {
        return parseFloat(get(property), parseFloat(defaultValue(property), 0f));
    }

    public static float getFloat(String property, float defaultValue) {
        return parseFloat(raw(property), defaultValue);
    }

    public static double getDouble(String property) {
        return parseDouble(get(property), parseDouble(defaultValue(property), 0d));
    }

    public static double getDouble(String property, double defaultValue) {
        return parseDouble(raw(property), defaultValue);
    }

    public static void set(String property, String value) {
        if (value == null) {
            clear(property);
            return;
        }
        System.setProperty(property, value);
    }

    public static void clear(String property) {
        System.clearProperty(property);
    }

    public static String defaultValue(String property) {
        Supplier<String> supplier = DEFAULTS.get(property);
        return supplier == null ? null : supplier.get();
    }

    public static List<String> properties() {
        return PROPERTIES;
    }

    public static boolean isKnownProperty(String property) {
        return property != null && PROPERTY_SET.contains(property);
    }

    public static String normalizeMicroeditionPlatformOverride(String candidate) {
        return candidate == null ? "" : candidate.trim();
    }

    public static int normalizeOpenGlesSupersampleScale(int candidate) {
        return Math.clamp(candidate, 1, 5);
    }

    public static String formatProperties() {
        StringJoiner joiner = new StringJoiner("\n", "Custom -Dopendoja.* properties:\n", "");
        for (String property : PROPERTIES) {
            joiner.add("  - " + property + " (default: " + defaultValue(property) + ")");
        }
        return joiner.toString();
    }

    private static Map<String, Supplier<String>> buildDefaults() {
        Map<String, Supplier<String>> defaults = new LinkedHashMap<>();
        defaults.put(ACCELERATION_AVAILABLE_DATA, () -> "1,2,3,4,5,6");
        defaults.put(ACCELERATION_AVAILABLE_EVENT, () -> "1,2");
        defaults.put(ACCELERATION_CURRENT, () -> "0,0,1000,0,0,0");
        defaults.put(ACCELERATION_EVENT_DOUBLE_TAP, () -> Integer.toString(com.nttdocomo.device.location.AccelerationSensor.DOUBLE_TAP_FRONT));
        defaults.put(ACCELERATION_EVENT_SCREEN_ORIENTATION, () -> "0");
        defaults.put(ACCELERATION_EVENT_SUPPORTED, () -> "true");
        defaults.put(ACCELERATION_INTERVAL_RESOLUTION, () -> "50");
        defaults.put(ACCELERATION_MAX_DATA_SIZE, () -> "32");
        defaults.put(ACCELERATION_SUPPORTED, () -> "true");
        defaults.put(AUDIO3D_RESOURCES, () -> "1");
        defaults.put(AUDIO_PRESENTER_PORTS, () -> "4");
        defaults.put(AUDIO3D_TIME_RESOLUTION_MS, () -> "100");
        defaults.put(BITMAP_FONT_CACHE_ENTRIES, () -> "256");
        defaults.put(BLUETOOTH_DEVICES, () -> "");
        defaults.put(BLUETOOTH_SCAN_INDEX, () -> "-1");
        defaults.put(BLUETOOTH_SEARCH_SELECTION_INDEX, () -> "0");
        defaults.put(BLUETOOTH_SELECTION_INDEX, () -> "0");
        defaults.put(BLUETOOTH_SUPPORTED, () -> "true");
        defaults.put(CAMERA_MAX_MOVIE_LENGTH, () -> "1048576");
        defaults.put(CLIP_SCREEN_PLANES, () -> "false");
        defaults.put(COMPASS_AZIMUTH, () -> "0.0");
        defaults.put(COMPASS_SUPPORTED, () -> Boolean.toString(getBoolean(GPS_SUPPORTED)));
        defaults.put(CULL_FIGURES, () -> "false");
        defaults.put(CULL_SIGN, () -> "1");
        defaults.put(DEBUG3D, () -> "false");
        defaults.put(DEBUG3D_CALLS, () -> "false");
        defaults.put(DEBUG_FUE_TREK_CONTROL, () -> "false");
        defaults.put(DEBUG_FUE_TREK_NOTES, () -> "false");
        defaults.put(EXTERNAL_FRAME, () -> "true");
        defaults.put(FELICA_EXTERNAL_NO_SPEED_DISCOVERY, () -> "false");
        defaults.put(FELICA_EXTERNAL_POLLED_IDM, () -> "");
        defaults.put(FELICA_EXTERNAL_SUPPORTS212, () -> "true");
        defaults.put(FELICA_EXTERNAL_SUPPORTS424, () -> "true");
        defaults.put(FELICA_ONLINE_DELAY_MILLIS, () -> "200");
        defaults.put(FELICA_ONLINE_NO_RESOURCES, () -> "false");
        defaults.put(FELICA_ONLINE_STATUS, () -> "0");
        defaults.put(FINGERPRINT_IDS, () -> "0");
        defaults.put(FINGERPRINT_MATCH_ID, () -> "0");
        defaults.put(FINGERPRINT_MAX_CANDIDATES, () -> "16");
        defaults.put(FONT_TYPE, () -> LaunchConfig.FontType.BITMAP.id);
        defaults.put(FONT_SCALE, () -> "0.85");
        defaults.put(FUETREK_MIX_PROFILE, () -> "0");
        defaults.put(OPEN_GLES_RENDERER, () -> OpenGlesRendererMode.SOFTWARE.id());
        defaults.put(SHOW_OPEN_GLES_FPS, () -> "false");
        defaults.put(OPEN_GLES_SUPERSAMPLE_SCALE, () -> "1");
        defaults.put(TRACE_OPEN_GLES_SYNC, () -> "false");
        defaults.put(GPS_ACCURACY, () -> Integer.toString(com.nttdocomo.device.location.Location.ACCURACY_NORMAL));
        defaults.put(GPS_ALTITUDE, () -> Integer.toString(com.nttdocomo.device.location.Location.ALTITUDE_UNKNOWN));
        defaults.put(GPS_DATUM, () -> Integer.toString(com.nttdocomo.device.location.LocationProvider.DATUM_WGS84));
        defaults.put(GPS_DELAY_MILLIS, () -> "0");
        defaults.put(GPS_FAILURE, () -> "");
        defaults.put(GPS_LATITUDE, () -> "35.681236");
        defaults.put(GPS_LONGITUDE, () -> "139.767125");
        defaults.put(GPS_MINIMAL_INTERVAL, () -> "1000");
        defaults.put(GPS_SUPPORTED, () -> "true");
        defaults.put(GPS_TRACKING_SUPPORTED, () -> "true");
        defaults.put(HOST_SCALE, () -> "1");
        defaults.put(IME_AUTOMATION_RESPONSE, () -> "");
        defaults.put(IME_AUTOMATION_DELAY_MS, () -> "0");
        defaults.put(PHONEBOOK_SELECTION_INDEX, () -> "");
        defaults.put(HTTP_OVERRIDE_DOMAIN, () -> "");
        defaults.put(MICROEDITION_PLATFORM_OVERRIDE, () -> "");
        defaults.put(TERMINAL_ID, OpenDoJaIdentity::defaultTerminalId);
        defaults.put(USER_ID, OpenDoJaIdentity::defaultUserId);
        defaults.put(INPUT_KEY_REPEAT_RELEASE_DEBOUNCE_MS, () -> "25");
        defaults.put(INPUT_BINDINGS, () -> "");
        defaults.put(INPUT_MINIMUM_SELECT_PRESS_MS, () -> "75");
        defaults.put(IRRECEIVER_DATA, () -> "IR-RECEIVE");
        defaults.put(KEEP_EXPLICIT_GC, () -> "false");
        defaults.put(LAUNCH_COMPAT_APPLIED, () -> "false");
        defaults.put(VERIFY_FALLBACK_APPLIED, () -> "false");
        defaults.put(LOG_LEVEL, () -> OpenDoJaLog.Level.OFF.name());
        defaults.put(VISUAL_PLAYER_FALLBACK_DELAY_MS, () -> "2000");
        defaults.put(MEDIAPLAYER_STATUS, () -> Integer.toString(com.nttdocomo.opt.device.MediaPlayer.STATUS_COMPLETED));
        defaults.put(MEDIAPLAYER_STOP_POSITION, () -> "0");
        defaults.put(MLD_BUFFER_FRAMES, () -> Integer.toString(MLDSynth.resolveConfigured().defaultBufferFrames));
        defaults.put(MLD_LINE_BUFFER_FRAMES, () -> Integer.toString(getInt(MLD_BUFFER_FRAMES) * 4));
        defaults.put(MLD_SAMPLE_RATE, () -> Float.toString(MLDSynth.resolveConfigured().defaultSampleRate));
        defaults.put(MLD_SYNTH, () -> MLDSynth.DEFAULT.id);
        defaults.put(OWNER_EMAIL1, () -> "");
        defaults.put(OWNER_PHONE1, () -> "");
        defaults.put(PEDOMETER_DEFAULT_HEIGHT_MM, () -> "1700");
        defaults.put(PEDOMETER_DEFAULT_WEIGHT_G, () -> "70000");
        defaults.put(PEDOMETER_DISTANCE_PER_STEP_MM, () -> "700");
        defaults.put(PEDOMETER_HEIGHT_UNIT_MM, () -> "10");
        defaults.put(PEDOMETER_HISTORY_DAYS, () -> "7");
        defaults.put(PEDOMETER_MAX_HEIGHT_MM, () -> "2500");
        defaults.put(PEDOMETER_MAX_WEIGHT_G, () -> "200000");
        defaults.put(PEDOMETER_MIN_HEIGHT_MM, () -> "500");
        defaults.put(PEDOMETER_MIN_WEIGHT_G, () -> "10000");
        defaults.put(PEDOMETER_OFF, () -> "false");
        defaults.put(PEDOMETER_TODAY_STEPS, () -> "6000");
        defaults.put(PEDOMETER_WEIGHT_UNIT_G, () -> "1000");
        defaults.put(POINTING_DEVICE_MAX_DIRECTION_Z, () -> "0");
        defaults.put(POINTING_DEVICE_UNAVAILABLE, () -> "false");
        defaults.put(PULSEMETER_RATE, () -> "72");
        defaults.put(PULSEMETER_STATUS, () -> Integer.toString(com.nttdocomo.opt.device.Pulsemeter.STATUS_NONE));
        defaults.put(PULSEMETER_WARMUP_MS, () -> "500");
        defaults.put(RADIOTUNER_AM_STATIONS, () -> "");
        defaults.put(RADIOTUNER_FM_STATIONS, () -> "");
        defaults.put(RADIOTUNER_SEEK_DELAY_MS, () -> "0");
        defaults.put(SAMPLED_CHUNK_BYTES, () -> "4096");
        defaults.put(SHORT_TIMER_MIN_TIME_INTERVAL, () -> "10");
        defaults.put(SHORT_TIMER_RESOLUTION, () -> "10");
        defaults.put(SPEECH_CODECS, () -> "audio/amr");
        defaults.put(SPEECH_FEATURE_CHUNKS, () -> "01020304;05060708;090a0b0c");
        defaults.put(SPEECH_FEATURE_INTERVAL, () -> "200");
        defaults.put(SPEECH_LEVEL, () -> "60");
        defaults.put(SPEECH_MAX_SPEECH_TIME, () -> "10000");
        defaults.put(SPEECH_NAME, () -> "openDoJa Speech Frontend/5.1");
        defaults.put(SPEECH_READY_TIME, () -> "500");
        defaults.put(SPEECH_SIGNAL_TO_NOISE_RATIO, () -> "70");
        defaults.put(SPEECH_SUPPORTED, () -> "true");
        defaults.put(SPEECH_TYPES, () -> "1");
        defaults.put(SPEECH_VOICE_ACTIVITY, () -> "1");
        defaults.put(STATUS_BAR_ICON_DEVICE, () -> LaunchConfig.DEFAULT_STATUS_BAR_ICON_DEVICE);
        defaults.put(SUB_DISPLAY_HEIGHT, () -> "0");
        defaults.put(SUB_DISPLAY_WIDTH, () -> "0");
        defaults.put(TEXT_ANTIALIAS, () -> "gasp");
        defaults.put(TRACE_AUDIO_FAILURES, () -> "false");
        defaults.put(TRACE_EVENTS, () -> "false");
        defaults.put(TRACE_FAILURES, () -> "false");
        defaults.put(UI_FIGURE_VERTEX_SCALE, () -> "0.015625");
        LinkedHashSet<String> missingDefaults = new LinkedHashSet<>(PROPERTIES);
        missingDefaults.removeAll(defaults.keySet());
        if (!missingDefaults.isEmpty()) {
            throw new IllegalStateException("Missing launch-arg defaults for: " + missingDefaults);
        }
        LinkedHashSet<String> unexpectedDefaults = new LinkedHashSet<>(defaults.keySet());
        unexpectedDefaults.removeAll(PROPERTIES);
        if (!unexpectedDefaults.isEmpty()) {
            throw new IllegalStateException("Unexpected launch-arg defaults for: " + unexpectedDefaults);
        }
        return Map.copyOf(defaults);
    }

    private static String raw(String property) {
        return System.getProperty(property);
    }

    private static int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static long parseLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static float parseFloat(String raw, float defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static double parseDouble(String raw, double defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
