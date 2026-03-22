package opendoja.demo;

import com.nttdocomo.ui.Font;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;

public final class BitmapFontProbe {
    private BitmapFontProbe() {
    }

    public static void main(String[] args) {
        DemoLog.enableInfoLogging();
        Font font12 = Font.getFont(Font.FACE_SYSTEM | Font.STYLE_PLAIN, 12);
        Font font20 = Font.getFont(Font.FACE_SYSTEM | Font.STYLE_PLAIN, 20);

        DemoLog.info(BitmapFontProbe.class, () -> "font12Class=" + font12.getClass().getName());
        DemoLog.info(BitmapFontProbe.class, () -> "font20Class=" + font20.getClass().getName());
        DemoLog.info(BitmapFontProbe.class, () -> "sizes=" + java.util.Arrays.toString(Font.getSupportedFontSizes()));
        DemoLog.info(BitmapFontProbe.class, () -> "asciiWidth=" + font12.stringWidth("ABC"));
        DemoLog.info(BitmapFontProbe.class, () -> "halfKanaWidth=" + font12.stringWidth("\uFF71\uFF72\uFF73"));
        DemoLog.info(BitmapFontProbe.class, () -> "fullWidthWidth=" + font12.stringWidth("\u6F22\u5B57"));
        DemoLog.info(BitmapFontProbe.class, () -> "questBracketWidth=" + font12.stringWidth("\u300A\u300B"));
        DemoLog.info(BitmapFontProbe.class, () -> "spaceWidth=" + (font12.stringWidth("A A") - font12.stringWidth("AA")));
        DemoLog.info(BitmapFontProbe.class, () -> "fallbackWidth=" + font12.stringWidth("\uFFFD") + " questionWidth=" + font12.stringWidth("?"));
        DemoLog.info(BitmapFontProbe.class, () -> "asciiPixels=" + renderPixels(font20, "ABC"));
        DemoLog.info(BitmapFontProbe.class, () -> "halfKanaPixels=" + renderPixels(font20, "\uFF71\uFF72\uFF73"));
        DemoLog.info(BitmapFontProbe.class, () -> "fullWidthPixels=" + renderPixels(font20, "\u6F22\u5B57"));
        DemoLog.info(BitmapFontProbe.class, () -> "questBracketPixels=" + renderPixels(font20, "\u300A\u300B"));
        DemoLog.info(BitmapFontProbe.class, () -> "spacePixels=" + renderPixels(font20, "   "));
        DemoLog.info(BitmapFontProbe.class, () -> "questionPixels=" + renderPixels(font20, "?") + " fallbackPixels=" + renderPixels(font20, "\uFFFD"));
        DemoLog.info(BitmapFontProbe.class, () -> "pictogramPixels=" + renderPixels(font20, "\uE63E"));
    }

    private static int renderPixels(Font font, String text) {
        Image image = Image.createImage(256, 64);
        Graphics graphics = image.getGraphics();
        graphics.setFont(font);
        graphics.setColor(0xFFFFFFFF);
        graphics.drawString(text, 0, font.getAscent());
        int litPixels = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 256; x++) {
                if ((graphics.getPixel(x, y) >>> 24) != 0) {
                    litPixels++;
                }
            }
        }
        return litPixels;
    }
}
