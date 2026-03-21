package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Font {
    private static final float HANDSET_FONT_SCALE = Float.parseFloat(System.getProperty("opendoja.fontScale", "0.85"));
    private static final Object TEXT_ANTIALIAS_HINT = resolveTextAntialiasHint();
    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_HEADING = 1;
    public static final int FACE_SYSTEM = 0x71000000;
    public static final int FACE_MONOSPACE = 0x72000000;
    public static final int FACE_PROPORTIONAL = 0x73000000;
    public static final int STYLE_PLAIN = 0x70100000;
    public static final int STYLE_BOLD = 0x70110000;
    public static final int STYLE_ITALIC = 0x70120000;
    public static final int STYLE_BOLDITALIC = 0x70130000;
    public static final int SIZE_SMALL = 0x70000100;
    public static final int SIZE_MEDIUM = 0x70000200;
    public static final int SIZE_LARGE = 0x70000300;
    public static final int SIZE_TINY = 0x70000400;

    private static final Set<String> AVAILABLE_FAMILIES = availableFamilies();
    private static final BufferedImage METRICS_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static Font defaultFont = new Font(FACE_SYSTEM, STYLE_PLAIN, decodeSize(SIZE_TINY));

    private final java.awt.Font awtFont;
    private FontMetrics metrics;

    protected Font() {
        this(FACE_SYSTEM, STYLE_PLAIN, decodeSize(SIZE_MEDIUM));
    }

    private Font(int face, int style, int size) {
        int awtStyle = switch (style) {
            case STYLE_BOLD -> java.awt.Font.BOLD;
            case STYLE_ITALIC -> java.awt.Font.ITALIC;
            case STYLE_BOLDITALIC -> java.awt.Font.BOLD | java.awt.Font.ITALIC;
            default -> java.awt.Font.PLAIN;
        };
        java.awt.Font baseFont = resolveBaseFont(face);
        this.awtFont = baseFont.deriveFont(awtStyle, (float) resolveDesktopPointSize(face, baseFont, awtStyle, size));
    }

    public static Font getDefaultFont() {
        return defaultFont;
    }

    public static void setDefaultFont(Font font) {
        if (font != null) {
            defaultFont = font;
        }
    }

    public static Font getFont(int value) {
        if (value == TYPE_HEADING) {
            return new Font(FACE_SYSTEM, STYLE_BOLD, decodeSize(SIZE_LARGE));
        }
        if (value == TYPE_DEFAULT) {
            return getDefaultFont();
        }
        return new Font(FACE_SYSTEM, STYLE_PLAIN, decodeSize(value));
    }

    public static Font getFont(int faceAndStyle, int size) {
        int face = decodeFace(faceAndStyle);
        int style = decodeStyle(faceAndStyle);
        return new Font(face, style, decodeSize(size));
    }

    public static int[] getSupportedFontSizes() {
        return new int[]{12, 16, 24, 30};
    }

    public int getAscent() {
        return metrics().getAscent();
    }

    public int getDescent() {
        return metrics().getDescent();
    }

    public int getHeight() {
        FontMetrics metrics = metrics();
        return metrics.getAscent() + metrics.getDescent();
    }

    public int stringWidth(String text) {
        return metrics().stringWidth(text == null ? "" : text);
    }

    public int stringWidth(XString text) {
        return stringWidth(text == null ? null : text.toString());
    }

    public int stringWidth(XString text, int offset, int length) {
        String value = text == null ? "" : text.toString();
        int start = Math.max(0, offset);
        int end = Math.min(value.length(), start + Math.max(0, length));
        return stringWidth(value.substring(start, end));
    }

    public int getBBoxWidth(String text) {
        return stringWidth(text);
    }

    public int getBBoxWidth(XString text) {
        return stringWidth(text);
    }

    public int getBBoxWidth(XString text, int offset, int length) {
        return stringWidth(text, offset, length);
    }

    public int getBBoxHeight(String text) {
        return getHeight();
    }

    public int getBBoxHeight(XString text) {
        return getHeight();
    }

    public int getLineBreak(String text, int offset, int length, int width) {
        String value = text == null ? "" : text;
        int limit = Math.min(value.length(), offset + length);
        int current = offset;
        while (current < limit) {
            if (stringWidth(value.substring(offset, current + 1)) > width) {
                break;
            }
            current++;
        }
        return current - offset;
    }

    public int getLineBreak(XString text, int offset, int length, int width) {
        return getLineBreak(text == null ? "" : text.toString(), offset, length, width);
    }

    java.awt.Font awtFont() {
        return awtFont;
    }

    private FontMetrics metrics() {
        if (metrics == null) {
            synchronized (METRICS_IMAGE) {
                Graphics2D graphics = METRICS_IMAGE.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, TEXT_ANTIALIAS_HINT);
                    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                    graphics.setFont(awtFont);
                    metrics = graphics.getFontMetrics();
                } finally {
                    graphics.dispose();
                }
            }
        }
        return metrics;
    }

    private static int decodeFace(int value) {
        int faceBits = value & 0x7F000000;
        if (faceBits == FACE_MONOSPACE) {
            return FACE_MONOSPACE;
        }
        if (faceBits == FACE_PROPORTIONAL) {
            return FACE_PROPORTIONAL;
        }
        return FACE_SYSTEM;
    }

    private static int decodeStyle(int value) {
        int styleBits = value & 0x00FF0000;
        return switch (styleBits) {
            case 0x00110000 -> STYLE_BOLD;
            case 0x00120000 -> STYLE_ITALIC;
            case 0x00130000 -> STYLE_BOLDITALIC;
            default -> STYLE_PLAIN;
        };
    }

    private static int decodeSize(int value) {
        return switch (value) {
            case SIZE_TINY -> 12;
            case SIZE_SMALL -> 16;
            case SIZE_MEDIUM -> 24;
            case SIZE_LARGE -> 30;
            default -> value > 0 && value < 256 ? value : 24;
        };
    }

    private static int resolveDesktopPointSize(int face, java.awt.Font baseFont, int awtStyle, int logicalSize) {
        int targetHeight = resolveTargetHeight(face, logicalSize);
        int bestPointSize = 8;
        int bestDistance = Integer.MAX_VALUE;
        synchronized (METRICS_IMAGE) {
            Graphics2D graphics = METRICS_IMAGE.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, TEXT_ANTIALIAS_HINT);
                graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                for (int pointSize = 6; pointSize <= 32; pointSize++) {
                    java.awt.Font candidate = baseFont.deriveFont(awtStyle, (float) pointSize);
                    graphics.setFont(candidate);
                    FontMetrics metrics = graphics.getFontMetrics();
                    int renderedHeight = metrics.getAscent() + metrics.getDescent();
                    int distance = java.lang.Math.abs(renderedHeight - targetHeight);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPointSize = pointSize;
                    }
                    if (distance == 0) {
                        break;
                    }
                }
            } finally {
                graphics.dispose();
            }
        }
        return bestPointSize;
    }

    private static int resolveTargetHeight(int face, int logicalSize) {
        if (logicalSize == decodeSize(SIZE_TINY) && face != FACE_MONOSPACE) {
            return 14;
        }
        return java.lang.Math.max(8, java.lang.Math.round(logicalSize * HANDSET_FONT_SCALE));
    }

    private static java.awt.Font resolveBaseFont(int face) {
        return new java.awt.Font(resolveFamily(face), java.awt.Font.PLAIN, 12);
    }

    private static String resolveFamily(int face) {
        if (face == FACE_MONOSPACE) {
            return firstInstalled(
                    "MS Gothic",
                    "Noto Sans Mono CJK JP",
                    "Noto Sans Mono CJK SC",
                    "IPAexGothic",
                    "IPAGothic",
                    java.awt.Font.MONOSPACED,
                    java.awt.Font.DIALOG
            );
        }
        if (face == FACE_PROPORTIONAL) {
            return firstInstalled(
                    "MS UI Gothic",
                    "MS PGothic",
                    "Yu Gothic UI",
                    "Yu Gothic",
                    "Meiryo UI",
                    "Meiryo",
                    "Noto Sans CJK JP",
                    "Noto Sans JP",
                    "Noto Sans",
                    "Noto Sans CJK SC",
                    "IPAexGothic",
                    "IPAGothic",
                    java.awt.Font.DIALOG,
                    java.awt.Font.SANS_SERIF
            );
        }
        return firstInstalled(
                "MS UI Gothic",
                "MS PGothic",
                "Yu Gothic UI",
                "Yu Gothic",
                "Meiryo UI",
                "Meiryo",
                "Noto Sans CJK JP",
                "Noto Sans JP",
                "Noto Sans",
                "Noto Sans CJK SC",
                "IPAexGothic",
                "IPAGothic",
                java.awt.Font.DIALOG,
                java.awt.Font.SANS_SERIF
        );
    }

    static Object textAntialiasHint() {
        return TEXT_ANTIALIAS_HINT;
    }

    private static Object resolveTextAntialiasHint() {
        String value = System.getProperty("opendoja.textAntialias", "gasp").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "off" -> RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
            case "gasp" -> RenderingHints.VALUE_TEXT_ANTIALIAS_GASP;
            case "lcd" -> RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
            default -> RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        };
    }

    private static String firstInstalled(String... candidates) {
        for (String candidate : candidates) {
            if (AVAILABLE_FAMILIES.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return java.awt.Font.DIALOG;
    }

    private static Set<String> availableFamilies() {
        Set<String> families = new HashSet<>();
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            families.add(family.toLowerCase(Locale.ROOT));
        }
        families.add(java.awt.Font.DIALOG.toLowerCase(Locale.ROOT));
        families.add(java.awt.Font.SANS_SERIF.toLowerCase(Locale.ROOT));
        families.add(java.awt.Font.MONOSPACED.toLowerCase(Locale.ROOT));
        return families;
    }
}
