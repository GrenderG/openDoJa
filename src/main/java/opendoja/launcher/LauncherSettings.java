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
        boolean disableOsDpiScaling,
        int openGlesSupersampleScale,
        HostKeybindConfiguration keybindConfiguration) {
    LauncherSettings {
        hostScale = HostScale.normalizeId(hostScale);
        synthId = normalizeSynthId(synthId);
        terminalId = OpenDoJaIdentity.normalizeTerminalId(terminalId);
        userId = OpenDoJaIdentity.normalizeUserId(userId);
        fontType = LaunchConfig.FontType.normalizeId(fontType);
        httpOverrideDomain = normalizeHttpOverrideDomain(httpOverrideDomain);
        fileEncodingOverride = normalizeFreeformOverride(fileEncodingOverride);
        microeditionPlatformOverride = OpenDoJaLaunchArgs.normalizeMicroeditionPlatformOverride(microeditionPlatformOverride);
        openGlesRendererMode = openGlesRendererMode == null ? OpenGlesRendererMode.SOFTWARE : openGlesRendererMode;
        openGlesSupersampleScale = OpenDoJaLaunchArgs.normalizeOpenGlesSupersampleScale(openGlesSupersampleScale);
        keybindConfiguration = keybindConfiguration == null ? HostKeybindConfiguration.defaults() : keybindConfiguration;
    }

    static LauncherSettings defaults() {
        return new LauncherSettings(HostScale.DEFAULT_ID, MLDSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                LaunchConfig.FontType.BITMAP.id,
                "",
                "",
                "",
                OpenGlesRendererMode.SOFTWARE,
                false,
                false,
                false,
                1,
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
        this(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain, fileEncodingOverride,
                microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, 1, HostKeybindConfiguration.defaults());
    }

    LauncherSettings(int hostScale,
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
        this(Integer.toString(hostScale), synthId, terminalId, userId, fontType, httpOverrideDomain, fileEncodingOverride,
                microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, 1, HostKeybindConfiguration.defaults());
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
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withHostScale(int hostScale) {
        return withHostScale(Integer.toString(hostScale));
    }

    LauncherSettings withSynthId(String synthId) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withTerminalId(String terminalId) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withUserId(String userId) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withFontType(String fontType) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withHttpOverrideDomain(String httpOverrideDomain) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withFileEncodingOverride(String fileEncodingOverride) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withMicroeditionPlatformOverride(String microeditionPlatformOverride) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withOpenGlesRendererMode(OpenGlesRendererMode openGlesRendererMode) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withShowOpenGlesFps(boolean showOpenGlesFps) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withDisableBytecodeVerification(boolean disableBytecodeVerification) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withDisableOsDpiScaling(boolean disableOsDpiScaling) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withOpenGlesSupersampleScale(int openGlesSupersampleScale) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }

    LauncherSettings withKeybindConfiguration(HostKeybindConfiguration keybindConfiguration) {
        return new LauncherSettings(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling, openGlesSupersampleScale, keybindConfiguration);
    }
}
