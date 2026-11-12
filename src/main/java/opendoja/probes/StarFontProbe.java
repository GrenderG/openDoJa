package opendoja.probes;

import java.util.Arrays;

/** STAR defaults use the shared DoJa font; explicit font sizes remain independent. */
public final class StarFontProbe {
    private static final String TEXT = "TOBAL Main Menu あいう";

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        var nativeFont = com.nttdocomo.ui.Font.getDefaultFont();
        var starFont = com.docomostar.ui.Font.getDefaultFont();
        check(starFont.getAscent() == nativeFont.getAscent() && starFont.getHeight() == nativeFont.getHeight(),
                "STAR starts with the shared backend's default font metrics");
        check(starFont.stringWidth(TEXT) == nativeFont.stringWidth(TEXT), "Default text widths agree");

        var nativeImage = com.nttdocomo.ui.Image.createImage(320, 64);
        var starImage = com.docomostar.ui.Image.createImage(320, 64);
        var nativeGraphics = nativeImage.getGraphics();
        var starGraphics = starImage.getGraphics();
        nativeGraphics.setColor(0xffffffff); starGraphics.setColor(0xffffffff);
        nativeGraphics.drawString(TEXT, 0, 32); starGraphics.drawString(TEXT, 0, 32);
        int[] nativePixels = new int[320*64], starPixels = new int[320*64];
        nativeGraphics.getRGBPixels(0,0,320,64,nativePixels,0);
        starGraphics.getRGBPixels(0,0,320,64,starPixels,0);
        for (int i = 0; i < nativePixels.length; i++) nativePixels[i] &= 0x00ffffff;
        check(Arrays.equals(nativePixels, starPixels), "Default STAR text renders identically to DoJa");

        int type = com.docomostar.ui.Font.FACE_SYSTEM | com.docomostar.ui.Font.STYLE_PLAIN;
        var explicit = com.docomostar.ui.Font.getFont(type,24);
        var nativeExplicit = com.nttdocomo.ui.Font.getFont(type,24);
        check(explicit.stringWidth(TEXT) == nativeExplicit.stringWidth(TEXT), "Explicit 24-dot fonts retain their metrics");
        check(explicit.stringWidth(TEXT) > starFont.stringWidth(TEXT), "Explicit size remains distinct from the default");
        com.docomostar.ui.Font.setDefaultFont(explicit);
        check(com.docomostar.ui.Font.getDefaultFont() == explicit, "Application-selected STAR default is respected");
        check(com.nttdocomo.ui.Font.getDefaultFont() == nativeFont, "Setting the STAR default does not change the DoJa default");
        nativeGraphics.dispose(); ((com.nttdocomo.ui.Graphics)starGraphics.hostGraphicsOGL()).dispose();
        System.out.println("star-font-probe-ok");
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
