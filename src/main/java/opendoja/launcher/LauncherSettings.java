package opendoja.launcher;

import opendoja.audio.mld.MLDSynth;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenGlesRendererMode;

record LauncherSettings(
        int hostScale,
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
    LauncherSettings {
        hostScale = normalizeHostScale(hostScale);
        synthId = normalizeSynthId(synthId);
        terminalId = OpenDoJaIdentity.normalizeTerminalId(terminalId);
        userId = OpenDoJaIdentity.normalizeUserId(userId);
        fontType = LaunchConfig.FontType.normalizeId(fontType);
        httpOverrideDomain = normalizeHttpOverrideDomain(httpOverrideDomain);
        fileEncodingOverride = normalizeFreeformOverride(fileEncodingOverride);
        microeditionPlatformOverride = OpenDoJaLaunchArgs.normalizeMicroeditionPlatformOverride(microeditionPlatformOverride);
        openGlesRendererMode = openGlesRendererMode == null ? OpenGlesRendererMode.SOFTWARE : openGlesRendererMode;
    }

    static LauncherSettings defaults() {
        return new LauncherSettings(1, MLDSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                LaunchConfig.FontType.BITMAP.id,
                "",
                "",
                "",
                OpenGlesRendererMode.SOFTWARE,
                false,
                false,
                false);
    }

    private static int normalizeHostScale(int candidate) {
        return Math.max(1, Math.min(4, candidate));
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
}
