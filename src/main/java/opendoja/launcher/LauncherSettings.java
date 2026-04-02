package opendoja.launcher;

import opendoja.audio.mld.MldSynth;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.LaunchConfig;

record LauncherSettings(
        int hostScale,
        String synthId,
        String terminalId,
        String userId,
        String fontType,
        boolean disableBytecodeVerification,
        boolean disableOsDpiScaling) {
    LauncherSettings {
        hostScale = normalizeHostScale(hostScale);
        synthId = normalizeSynthId(synthId);
        terminalId = OpenDoJaIdentity.normalizeTerminalId(terminalId);
        userId = OpenDoJaIdentity.normalizeUserId(userId);
        fontType = LaunchConfig.FontType.normalizeId(fontType);
    }

    static LauncherSettings defaults() {
        return new LauncherSettings(1, MldSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                LaunchConfig.FontType.BITMAP.id,
                false,
                false);
    }

    private static int normalizeHostScale(int candidate) {
        return Math.max(1, Math.min(4, candidate));
    }

    private static String normalizeSynthId(String candidate) {
        MldSynth synth = MldSynth.fromId(candidate);
        return synth == null ? MldSynth.DEFAULT.id : synth.id;
    }
}
