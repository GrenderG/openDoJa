package opendoja.probes;

import com.nttdocomo.ui.Font;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import opendoja.host.DoJaEncoding;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FontInvisibleControlProbe {
    private static final String DMC_MISSION_TITLE_HEX = "949782e8978882e988c5";
    private static final String DMC_MISSION_TITLE_PADDED_HEX =
            DMC_MISSION_TITLE_HEX + "00000000000000000000000000000000000000000000000000000000";
    private static final String DMC_MISSION_INFO_HEX =
            "88c8914f8141906c8ad48a4582c696828a4582c682aa8c7182aa82c182bd8e9e814181408140"
                    + "906c8ad48a4582c988ab968282aa97ac82ea8d9e82f182c5978882bd81428140814081408140"
                    + "82bb82cc8e9e82cc88ab968282aa96a282be8e6382c182c482a282e988d78141814081408140"
                    + "835f8393836582cd88ab96828eeb82e882f091b182af82c482a282bd81428140814081408140";

    private FontInvisibleControlProbe() {
    }

    public static void main(String[] args) {
        Font font = Font.getFont(Font.FACE_SYSTEM | Font.STYLE_PLAIN, 12);

        String rockmanVisible = "Hmm, nothing yet...";
        byte[] rockmanBytes = (rockmanVisible + "\0").getBytes(StandardCharsets.ISO_8859_1);
        String rockmanDecoded = new String(rockmanBytes, 0, 20);
        assertSameTextRender(font, "rockmanLengthPrefixedNul", rockmanVisible, rockmanDecoded);

        assertSameTextRender(font,
                "japaneseMenuNulPadding",
                "\u898b\u308b\u898b\u306a\u3044",
                "\u898b\u308b\0\0\0\u898b\u306a\u3044\0");

        assertSameTextRender(font,
                "c1Control",
                "ABC",
                "A\u0085B\u009cC");

        String dmcMissionTitleVisible = new String(hex(DMC_MISSION_TITLE_HEX), DoJaEncoding.DEFAULT_CHARSET);
        String dmcMissionTitlePadded = new String(hex(DMC_MISSION_TITLE_PADDED_HEX), DoJaEncoding.DEFAULT_CHARSET);
        assertSameTextRender(font, "dmcMissionTitleNulPadding", dmcMissionTitleVisible, dmcMissionTitlePadded);

        byte[] dmcStoryBytes = hex(DMC_MISSION_INFO_HEX);
        String dmcStoryVisible = new String(dmcStoryBytes, DoJaEncoding.DEFAULT_CHARSET);
        String dmcStoryPadded = new String(appendZeros(dmcStoryBytes, 32), DoJaEncoding.DEFAULT_CHARSET);
        assertSameTextRender(font, "dmcMissionInfoNulPadding", dmcStoryVisible, dmcStoryPadded);

        System.out.println("Font invisible control probe OK");
    }

    private static void assertSameTextRender(Font font, String label, String expected, String actual) {
        assertEquals(label + "Width", font.stringWidth(expected), font.stringWidth(actual));
        assertEquals(label + "BBoxWidth", font.getBBoxWidth(expected), font.getBBoxWidth(actual));
        assertEquals(label + "Pixels", renderHash(font, expected), renderHash(font, actual));
    }

    private static int renderHash(Font font, String text) {
        Image image = Image.createImage(256, 64);
        Graphics graphics = image.getGraphics();
        graphics.setFont(font);
        graphics.setColor(0xFFFFFFFF);
        graphics.drawString(text, 0, font.getAscent());
        int hash = 1;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 256; x++) {
                hash = 31 * hash + graphics.getPixel(x, y);
            }
        }
        return hash;
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }

    private static byte[] appendZeros(byte[] bytes, int count) {
        return Arrays.copyOf(bytes, bytes.length + count);
    }

    private static byte[] hex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex string must contain an even number of digits");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int offset = index * 2;
            bytes[index] = (byte) Integer.parseInt(value.substring(offset, offset + 2), 16);
        }
        return bytes;
    }
}
