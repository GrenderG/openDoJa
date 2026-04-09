package opendoja.launcher;

import opendoja.audio.mld.MLDSynth;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.LaunchConfig;

record LauncherSettings(
        int hostScale,
        String synthId,
        String terminalId,
        String userId,
        String fontType,
        String httpOverrideDomain,
        boolean disableBytecodeVerification,
        boolean disableOsDpiScaling) {
    LauncherSettings {
        hostScale = normalizeHostScale(hostScale);
        synthId = normalizeSynthId(synthId);
        terminalId = OpenDoJaIdentity.normalizeTerminalId(terminalId);
        userId = OpenDoJaIdentity.normalizeUserId(userId);
        fontType = LaunchConfig.FontType.normalizeId(fontType);
        httpOverrideDomain = normalizeHttpOverrideDomain(httpOverrideDomain);
    }

    static LauncherSettings defaults() {
        return new LauncherSettings(1, MLDSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                LaunchConfig.FontType.BITMAP.id,
                "",
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
}
