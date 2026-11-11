package opendoja.launcher;

import opendoja.audio.mld.MLDSynth;
import opendoja.host.HostKeybindConfiguration;
import opendoja.host.HostScale;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenGlesRendererMode;

record LauncherSettings(
        String hostScale,
        String displayRotation,
        String synthId,
        String terminalId,
        String userId,
        String uimId,
        String fontType,
        String systemFontOverride,
        String httpOverrideDomain,
        String fileEncodingOverride,
        String microeditionPlatformOverride,
        OpenGlesRendererMode openGlesRendererMode,
        boolean showOpenGlesFps,
        boolean disableBytecodeVerification,
        boolean disableOsDpiScaling,
        int openGlesSupersampleScale,
        String launchType,
        HostKeybindConfiguration keybindConfiguration) {
    LauncherSettings {
        hostScale = HostScale.normalizeId(hostScale);
        displayRotation = OpenDoJaLaunchArgs.normalizeDisplayRotation(displayRotation);
        synthId = normalizeSynthId(synthId);
        terminalId = OpenDoJaIdentity.normalizeTerminalId(terminalId);
        userId = OpenDoJaIdentity.normalizeUserId(userId);
        uimId = OpenDoJaIdentity.normalizeUimId(uimId);
        fontType = LaunchConfig.FontType.normalizeId(fontType);
        systemFontOverride = OpenDoJaLaunchArgs.normalizeSystemFontOverride(systemFontOverride);
        httpOverrideDomain = normalizeHttpOverrideDomain(httpOverrideDomain);
        fileEncodingOverride = normalizeFreeformOverride(fileEncodingOverride);
        microeditionPlatformOverride = OpenDoJaLaunchArgs.normalizeMicroeditionPlatformOverride(microeditionPlatformOverride);
        openGlesRendererMode = openGlesRendererMode == null ? OpenGlesRendererMode.SOFTWARE : openGlesRendererMode;
        openGlesSupersampleScale = OpenDoJaLaunchArgs.normalizeOpenGlesSupersampleScale(openGlesSupersampleScale);
        launchType = OpenDoJaLaunchArgs.normalizeLaunchType(launchType);
        keybindConfiguration = keybindConfiguration == null ? HostKeybindConfiguration.defaults() : keybindConfiguration;
    }

    static LauncherSettings defaults() {
        return new LauncherSettings(
                HostScale.DEFAULT_ID,
                OpenDoJaLaunchArgs.DISPLAY_ROTATION_NONE,
                MLDSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                OpenDoJaIdentity.defaultUimId(),
                LaunchConfig.FontType.BITMAP.id,
                "",
                "",
                "",
                "",
                OpenGlesRendererMode.SOFTWARE,
                false,
                false,
                false,
                1,
                LaunchConfig.LaunchTypeOption.NORMAL.id,
                HostKeybindConfiguration.defaults());
    }

    LauncherSettings(String hostScale,
                     String synthId,
                     String terminalId,
                     String userId,
                     String fontType,
                     String httpOverrideDomain,
                     String fileEncodingOverride,
                     String microeditionPlatformOverride,
                     OpenGlesRendererMode openGlesRendererMode,
                     boolean showOpenGlesFps,
                     boolean disableBytecodeVerification,
                     boolean disableOsDpiScaling) {
        this(hostScale, synthId, terminalId, userId, fontType, "", httpOverrideDomain, fileEncodingOverride,
                microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling);
    }

    LauncherSettings(String hostScale,
                     String synthId,
                     String terminalId,
                     String userId,
                     String fontType,
                     String systemFontOverride,
                     String httpOverrideDomain,
                     String fileEncodingOverride,
                     String microeditionPlatformOverride,
                     OpenGlesRendererMode openGlesRendererMode,
                     boolean showOpenGlesFps,
                     boolean disableBytecodeVerification,
                     boolean disableOsDpiScaling) {
        this(hostScale, OpenDoJaLaunchArgs.DISPLAY_ROTATION_NONE, synthId, terminalId, userId, 
                OpenDoJaIdentity.defaultUimId(), fontType, systemFontOverride,
                httpOverrideDomain, fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, 1,
                LaunchConfig.LaunchTypeOption.NORMAL.id, HostKeybindConfiguration.defaults());
    }

    private LauncherSettings copy(String hostScale,
                                  String displayRotation,
                                  String synthId,
                                  String terminalId,
                                  String userId,
                                  String uimId,
                                  String fontType,
                                  String systemFontOverride,
                                  String httpOverrideDomain,
                                  String fileEncodingOverride,
                                  String microeditionPlatformOverride,
                                  OpenGlesRendererMode openGlesRendererMode,
                                  boolean showOpenGlesFps,
                                  boolean disableBytecodeVerification,
                                  boolean disableOsDpiScaling,
                                  int openGlesSupersampleScale,
                                  String launchType,
                                  HostKeybindConfiguration keybindConfiguration) {
        return new LauncherSettings(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride,
                httpOverrideDomain, fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    private static String normalizeSynthId(String candidate) {
        MLDSynth synth = MLDSynth.fromId(candidate);
        return synth == null ? MLDSynth.DEFAULT.id : synth.id;
    }

    private static String normalizeHttpOverrideDomain(String candidate) {
        if (candidate == null) {
            return "";
        }
        return candidate.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeFreeformOverride(String candidate) {
        return candidate == null ? "" : candidate.trim();
    }

    LauncherSettings withHostScale(String hostScale) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withHostScale(int hostScale) {
        return withHostScale(Integer.toString(hostScale));
    }

    LauncherSettings withSynthId(String synthId) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withTerminalId(String terminalId) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withUserId(String userId) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withUimId(String uimId) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withFontType(String fontType) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withSystemFontOverride(String systemFontOverride) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withHttpOverrideDomain(String httpOverrideDomain) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withFileEncodingOverride(String fileEncodingOverride) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withMicroeditionPlatformOverride(String microeditionPlatformOverride) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withOpenGlesRendererMode(OpenGlesRendererMode openGlesRendererMode) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withShowOpenGlesFps(boolean showOpenGlesFps) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withDisableBytecodeVerification(boolean disableBytecodeVerification) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withDisableOsDpiScaling(boolean disableOsDpiScaling) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withOpenGlesSupersampleScale(int openGlesSupersampleScale) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withLaunchType(String launchType) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withDisplayRotation(String displayRotation) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }

    LauncherSettings withKeybindConfiguration(HostKeybindConfiguration keybindConfiguration) {
        return copy(hostScale, displayRotation, synthId, terminalId, userId, uimId, fontType, systemFontOverride, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, launchType, keybindConfiguration);
    }
}
