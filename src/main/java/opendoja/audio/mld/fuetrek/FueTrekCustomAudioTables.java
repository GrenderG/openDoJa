package opendoja.audio.mld.fuetrek;

/** Byte-quantized custom audio curves at SDK4c170/4c1f0 (not synth curves). */
final class FueTrekCustomAudioTables {
    private static final int[] LEVEL = {
        0,0,0,0,0,0,0,0,1,1,1,1,2,2,3,3,4,4,5,5,6,6,7,8,9,9,10,11,12,13,14,15,
        16,17,18,19,20,21,22,24,25,26,27,29,30,32,33,34,36,37,39,41,42,44,46,47,
        49,51,53,55,56,58,60,62,64,66,68,70,73,75,77,79,81,84,86,88,91,93,96,98,
        101,103,106,108,111,114,116,119,122,125,128,130,133,136,139,142,145,148,
        151,154,158,161,164,167,171,174,177,181,184,187,191,194,198,201,205,209,
        212,216,220,223,227,231,235,239,243,247,251,255
    };
    private static final int[] PAN = {
        255,254,254,254,254,254,254,254,253,253,253,252,252,251,251,250,250,249,
        248,247,247,246,245,244,243,242,241,240,239,238,237,236,235,234,232,231,
        230,228,227,225,224,222,221,219,218,216,214,213,211,209,207,205,204,202,
        200,198,196,194,192,190,187,185,183,181,179,176,174,172,170,167,165,162,
        160,157,155,152,150,147,145,142,140,137,134,132,129,126,123,121,118,115,
        112,109,106,104,101,98,95,92,89,86,83,80,77,74,71,68,65,62,59,56,53,50,
        47,43,40,37,34,31,28,25,22,18,15,12,9,6,3,0
    };

    private FueTrekCustomAudioTables() {}

    static int outputPanQ12(int level, int pan, int masterPan, boolean right) {
        // Native1d2b0 replaces zero pan with1 before multiplying byte tables.
        pan = Math.max(1, Math.min(127, pan));
        masterPan = Math.max(1, Math.min(127, masterPan));
        int a = right ? PAN[128 - pan] : PAN[pan];
        int b = right ? PAN[128 - masterPan] : PAN[masterPan];
        return a * b * LEVEL[Math.max(0, Math.min(127, level))] / 65025;
    }
}
