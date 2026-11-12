package com.docomostar.ui;

public class Font {
    public static final int FACE_SYSTEM = com.nttdocomo.ui.Font.FACE_SYSTEM;
    public static final int FACE_MONOSPACE = com.nttdocomo.ui.Font.FACE_MONOSPACE;
    public static final int FACE_PROPORTIONAL = com.nttdocomo.ui.Font.FACE_PROPORTIONAL;
    public static final int STYLE_PLAIN = com.nttdocomo.ui.Font.STYLE_PLAIN;
    public static final int STYLE_BOLD = com.nttdocomo.ui.Font.STYLE_BOLD;
    public static final int STYLE_ITALIC = com.nttdocomo.ui.Font.STYLE_ITALIC;
    public static final int STYLE_BOLDITALIC = com.nttdocomo.ui.Font.STYLE_BOLDITALIC;
    private static volatile Font defaultFont;
    final com.nttdocomo.ui.Font delegate;

    protected Font() { this(com.nttdocomo.ui.Font.getDefaultFont()); }
    private Font(com.nttdocomo.ui.Font delegate) { this.delegate = delegate; }
    public static Font getFont(int type, int size) { return new Font(com.nttdocomo.ui.Font.getFont(type, size)); }
    public static Font getDefaultFont() {
        Font font = defaultFont;
        return font == null ? new Font(com.nttdocomo.ui.Font.getDefaultFont()) : font;
    }
    public static void setDefaultFont(Font font) { defaultFont = java.util.Objects.requireNonNull(font); }
    // STAR starts at 12 dots; expose the bitmap strikes available in the host.
    public static int[] getSupportedFontSizes() { return new int[]{12, 16, 20, 24, 30}; }
    public int getAscent() { return delegate.getAscent(); }
    public int getDescent() { return delegate.getDescent(); }
    public int getHeight() { return delegate.getHeight(); }
    public int stringWidth(String text) { return delegate.stringWidth(text); }
    public int stringWidth(com.docomostar.lang.XString text) {
        return delegate.stringWidth(com.docomostar.lang._XStringSupport.unwrap(text));
    }
    public int stringWidth(com.docomostar.lang.XString text, int offset, int length) {
        return delegate.stringWidth(com.docomostar.lang._XStringSupport.unwrap(text), offset, length);
    }
    public int getBBoxWidth(String text) { return delegate.getBBoxWidth(text); }
    public int getBBoxWidth(com.docomostar.lang.XString text) {
        return delegate.getBBoxWidth(com.docomostar.lang._XStringSupport.unwrap(text));
    }
    public int getBBoxWidth(com.docomostar.lang.XString text, int offset, int length) {
        return delegate.getBBoxWidth(com.docomostar.lang._XStringSupport.unwrap(text), offset, length);
    }
    public int getBBoxHeight(String text) { return delegate.getBBoxHeight(text); }
    public int getBBoxHeight(com.docomostar.lang.XString text) {
        return delegate.getBBoxHeight(com.docomostar.lang._XStringSupport.unwrap(text));
    }
    public int getLineBreak(String text, int offset, int length, int width) {
        return delegate.getLineBreak(text, offset, length, width);
    }
    public int getLineBreak(com.docomostar.lang.XString text, int offset, int length, int width) {
        return delegate.getLineBreak(com.docomostar.lang._XStringSupport.unwrap(text), offset, length, width);
    }
}
