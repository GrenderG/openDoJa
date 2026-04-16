package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;
import opendoja.host.DoJaProfile;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLaunchArgs;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Defines character types.
 * The font class handles fonts and font metrics.
 *
 * <p>There are two ways to specify a font to {@code getFont}.
 * One way is to specify a type.
 * The other way is to specify the font face, style, and size.
 * If the specified font is not supported by the device, an alternative font
 * object is returned.
 * Depending on the system, font objects may be created statically, and the
 * same object may be returned when the same font type is specified.</p>
 *
 * <p>Since DoJa-3.0 (505i), the initial default font is
 * {@code FACE_SYSTEM|SIZE_TINY|STYLE_PLAIN}.
 * {@link #getDefaultFont()} returns the font set by
 * {@link #setDefaultFont(Font)}; if no font has been set, it returns the
 * initial default font.</p>
 *
 * <p>Since DoJa-5.0 (903i), font size can be specified in dots by
 * {@link #getFont(int, int)}.</p>
 */
public class Font {
    static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final float HANDSET_FONT_SCALE = OpenDoJaLaunchArgs.getFloat(OpenDoJaLaunchArgs.FONT_SCALE);
    private static final boolean BITMAP_FONT_ENABLED = LaunchConfig.FontType.resolveConfigured() == LaunchConfig.FontType.BITMAP;
    private static final Object TEXT_ANTIALIAS_HINT = resolveTextAntialiasHint();
    /**
     * A font type that represents the default font ({@code =0x00000000}).
     * Since DoJa-3.0 (505i), it means the initial default font
     * {@code FACE_SYSTEM|SIZE_TINY|STYLE_PLAIN}.
     */
    public static final int TYPE_DEFAULT = 0;
    /**
     * A font type that represents a heading font ({@code =0x00000001}).
     * This may not be supported depending on the handset.
     */
    public static final int TYPE_HEADING = 1;
    /**
     * The system-font face ({@code =0x71000000}).
     */
    public static final int FACE_SYSTEM = 0x71000000;
    /**
     * The monospace-font face ({@code =0x72000000}).
     * This may not be supported depending on the handset.
     */
    public static final int FACE_MONOSPACE = 0x72000000;
    /**
     * The proportional-font face ({@code =0x73000000}).
     * This may not be supported depending on the handset.
     */
    public static final int FACE_PROPORTIONAL = 0x73000000;
    /**
     * The plain font style ({@code =0x70100000}).
     */
    public static final int STYLE_PLAIN = 0x70100000;
    /**
     * The bold font style ({@code =0x70110000}).
     * This may not be supported depending on the handset.
     */
    public static final int STYLE_BOLD = 0x70110000;
    /**
     * The italic font style ({@code =0x70120000}).
     * This may not be supported depending on the handset.
     */
    public static final int STYLE_ITALIC = 0x70120000;
    /**
     * The bold-italic font style ({@code =0x70130000}).
     * This may not be supported depending on the handset.
     */
    public static final int STYLE_BOLDITALIC = 0x70130000;
    /**
     * The small font size ({@code =0x70000100}).
     * Since DoJa-4.0 (901i), this is a required font size.
     */
    public static final int SIZE_SMALL = 0x70000100;
    /**
     * The medium font size ({@code =0x70000200}).
     */
    public static final int SIZE_MEDIUM = 0x70000200;
    /**
     * The large font size ({@code =0x70000300}).
     * Since DoJa-4.0 (901i), this is a required font size.
     */
    public static final int SIZE_LARGE = 0x70000300;
    /**
     * The tiny font size ({@code =0x70000400}).
     */
    public static final int SIZE_TINY = 0x70000400;

    private static final BufferedImage METRICS_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static volatile Set<String> availableFamilies;
    private static volatile Font defaultFont;
    private static volatile int defaultFontLogicalSize;
    private static volatile boolean defaultFontCustomized;

    private final java.awt.Font awtFont;
    private java.awt.FontMetrics metrics;

    /**
     * Applications cannot directly create instances of this class.
     */
    protected Font() {
        this(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12));
    }

    /**
     * Creates a DoJa font wrapper around a desktop font.
     * This constructor is for host-side integration and is not part of the
     * official DoJa API surface.
     *
     * @param awtFont the wrapped desktop font
     */
    protected Font(java.awt.Font awtFont) {
        this.awtFont = awtFont == null ? new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12) : awtFont;
    }

    private Font(int face, int style, int size) {
        this(resolveBaseFont(face).deriveFont(resolveAwtStyle(style), (float) resolveDesktopPointSize(face, resolveBaseFont(face), resolveAwtStyle(style), size)));
    }

    /**
     * Gets the default font.
     * Since DoJa-3.0 (505i), this returns the font object set by
     * {@link #setDefaultFont(Font)}.
     * Initially, it returns {@code FACE_SYSTEM|SIZE_TINY|STYLE_PLAIN}.
     *
     * @return the default-font object
     */
    public static Font getDefaultFont() {
        if (defaultFontCustomized) {
            return defaultFont;
        }
        int logicalSize = decodeSize(defaultLogicalSizeConstant());
        Font current = defaultFont;
        if (current != null && defaultFontLogicalSize == logicalSize) {
            return current;
        }
        synchronized (Font.class) {
            if (!defaultFontCustomized && (defaultFont == null || defaultFontLogicalSize != logicalSize)) {
                defaultFont = createFont(FACE_SYSTEM, STYLE_PLAIN, logicalSize);
                defaultFontLogicalSize = logicalSize;
            }
            return defaultFont;
        }
    }

    /**
     * Sets the default font.
     * Except for the initial state, {@link #getDefaultFont()} returns the font
     * object set by this method.
     *
     * @param font the font object to set as the default font
     * @throws NullPointerException if {@code font} is {@code null}
     */
    public static void setDefaultFont(Font font) {
        if (font == null) {
            throw new NullPointerException("font");
        }
        defaultFont = font;
        defaultFontCustomized = true;
    }

    /**
     * Gets the font object specified by the argument.
     * The font can be specified either by a type or by the bitwise OR of face,
     * style, and size constants.
     * If a font of the specified type is not supported, an alternative font is
     * returned.
     *
     * @param value the font type
     * @return the font object
     * @throws IllegalArgumentException if {@code value} is invalid
     */
    public static Font getFont(int value) {
        if (value == TYPE_HEADING) {
            return createFont(FACE_SYSTEM, STYLE_BOLD, decodeSize(SIZE_LARGE));
        }
        if (value == TYPE_DEFAULT) {
            return getDefaultFont();
        }
        validateTypeBits(value);
        return createFont(decodeFace(value), decodeStyle(value), decodeSize(value));
    }

    /**
     * Gets a font object from the specified font type and the font size
     * specified in dots.
     * The size part in {@code type} is ignored.
     * Unlike {@link #getFont(int)}, {@code TYPE_DEFAULT} and
     * {@code TYPE_HEADING} cannot be specified in {@code type}.
     *
     * @param faceAndStyle the font type
     * @param size the font size in dots
     * @return the font object
     * @throws UnsupportedOperationException if the handset does not support
     *         this method
     * @throws IllegalArgumentException if {@code size} is unsupported or if
     *         {@code faceAndStyle} is invalid
     */
    public static Font getFont(int faceAndStyle, int size) {
        if (faceAndStyle == TYPE_DEFAULT || faceAndStyle == TYPE_HEADING) {
            throw new IllegalArgumentException("faceAndStyle");
        }
        validateTypeBits(faceAndStyle);
        if (size <= 0) {
            throw new IllegalArgumentException("size");
        }
        return createFont(decodeFace(faceAndStyle), decodeStyle(faceAndStyle), decodeSize(size));
    }

    /**
     * Gets an array of the font sizes, in dots, supported by this handset.
     * The returned array is a copy of the array held internally by this
     * object.
     *
     * @return an array of the supported font sizes in ascending order
     * @throws UnsupportedOperationException if the handset does not support
     *         this method
     */
    public static int[] getSupportedFontSizes() {
        return _BitmapFont.getSupportedFontSizes();
    }

    /**
     * Gets the font ascent, the distance from the baseline to the upper edge.
     *
     * @return the font ascent
     */
    public int getAscent() {
        return metrics().getAscent();
    }

    /**
     * Gets the font descent, the distance from the baseline to the lower edge.
     *
     * @return the font descent
     */
    public int getDescent() {
        return metrics().getDescent();
    }

    /**
     * Gets the font height.
     * This value is equal to the sum of the ascent and the descent.
     *
     * @return the font height
     */
    public int getHeight() {
        java.awt.FontMetrics metrics = metrics();
        return metrics.getAscent() + metrics.getDescent();
    }

    /**
     * Gets the width of the specified string.
     *
     * @param text the string
     * @return the width required to display the string
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public int stringWidth(String text) {
        return metrics().stringWidth(metricString(text));
    }

    /**
     * Gets the width of the string in the specified {@code XString}.
     *
     * @param text the {@code XString}
     * @return the width required to display the string
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public int stringWidth(XString text) {
        return stringWidth(requireXString(text, "text"));
    }

    /**
     * Gets the width of part of the string in the specified {@code XString}.
     *
     * @param text the {@code XString}
     * @param offset the offset of the string to calculate
     * @param length the length of the string to calculate
     * @return the width required to display the substring
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws StringIndexOutOfBoundsException if {@code offset} is negative, if
     *         {@code length} is negative, or if {@code offset} or
     *         {@code offset + length} exceeds the string length
     */
    public int stringWidth(XString text, int offset, int length) {
        return stringWidth(slice(requireXString(text, "text"), offset, length));
    }

    /**
     * Gets the width of the bounding box of the specified string.
     *
     * @param text the string
     * @return the width
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public int getBBoxWidth(String text) {
        return stringWidth(text);
    }

    /**
     * Gets the width of the bounding box of the string in the specified
     * {@code XString}.
     *
     * @param text the {@code XString}
     * @return the width
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public int getBBoxWidth(XString text) {
        return stringWidth(text);
    }

    /**
     * Gets the width of the bounding box of part of the string in the
     * specified {@code XString}.
     *
     * @param text the {@code XString}
     * @param offset the offset of the string to calculate
     * @param length the length of the string to calculate
     * @return the width
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws StringIndexOutOfBoundsException if {@code offset} is negative, if
     *         {@code length} is negative, or if {@code offset} or
     *         {@code offset + length} exceeds the string length
     */
    public int getBBoxWidth(XString text, int offset, int length) {
        return stringWidth(text, offset, length);
    }

    /**
     * Gets the height of the bounding box of the specified string.
     * Since DoJa-3.0 (505i), if {@code text} is the empty string
     * ({@code ""}), {@code 0} is returned.
     *
     * @param text the string
     * @return the height
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public int getBBoxHeight(String text) {
        return metricString(text).isEmpty() ? 0 : getHeight();
    }

    /**
     * Gets the height of the bounding box of the string in the specified
     * {@code XString}.
     * If the {@code XString} contains the empty string, {@code 0} is returned.
     *
     * @param text the {@code XString}
     * @return the height
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public int getBBoxHeight(XString text) {
        return getBBoxHeight(requireXString(text, "text"));
    }

    /**
     * Gets the line-break position of the specified string.
     * If the width of the substring of length {@code length} from
     * {@code offset} is greater than {@code width}, this returns the position
     * of the string that becomes the beginning of the next line.
     * If the width is less than or equal to {@code width}, {@code offset + length}
     * is returned.
     *
     * @param text the string
     * @param offset the offset of the string to calculate
     * @param length the length of the string to calculate
     * @param width the line width in pixels
     * @return the offset of the next line-break position
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws StringIndexOutOfBoundsException if {@code offset} is negative, if
     *         {@code length} is negative, or if {@code offset} or
     *         {@code offset + length} exceeds the string length
     * @throws IllegalArgumentException if {@code width} is negative
     */
    public int getLineBreak(String text, int offset, int length, int width) {
        String value = requireString(text, "text");
        validateSubstringRange(value, offset, length);
        if (width < 0) {
            throw new IllegalArgumentException("width");
        }
        int limit = offset + length;
        int current = offset;
        while (current < limit) {
            if (stringWidth(value.substring(offset, current + 1)) > width) {
                break;
            }
            current++;
        }
        return current;
    }

    /**
     * Gets the line-break position of the string in the specified
     * {@code XString}.
     *
     * @param text the {@code XString}
     * @param offset the offset of the string to calculate
     * @param length the length of the string to calculate
     * @param width the line width in pixels
     * @return the offset of the next line-break position
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws StringIndexOutOfBoundsException if {@code offset} is negative, if
     *         {@code length} is negative, or if {@code offset} or
     *         {@code offset + length} exceeds the string length
     * @throws IllegalArgumentException if {@code width} is negative
     */
    public int getLineBreak(XString text, int offset, int length, int width) {
        return getLineBreak(requireXString(text, "text"), offset, length, width);
    }

    java.awt.Font awtFont() {
        return awtFont;
    }

    void drawString(Graphics2D graphics, String text, int x, int y, int argbColor) {
        String value = metricString(text);
        if (value.isEmpty()) {
            return;
        }
        graphics.setFont(awtFont);
        graphics.setColor(new Color(argbColor, true));
        graphics.drawString(value, x, y);
    }

    /**
     * Draws text using this DoJa font from host-side integration code.
     * This is a host helper and not part of the original DoJa API surface.
     *
     * @param graphics the destination graphics
     * @param text the text to draw
     * @param x the left position
     * @param y the baseline position
     * @param argbColor the text color
     */
    public final void drawHostString(Graphics2D graphics, String text, int x, int y, int argbColor) {
        drawString(graphics, text, x, y, argbColor);
    }

    static Object textAntialiasHint() {
        return TEXT_ANTIALIAS_HINT;
    }

    private java.awt.FontMetrics metrics() {
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

    private static Font createFont(int face, int style, int size) {
        if (BITMAP_FONT_ENABLED) {
            Font bitmap = _BitmapFont.create(face, style, size);
            if (bitmap != null) {
                return bitmap;
            }
        }
        return new Font(face, style, size);
    }

    static String requireString(String text, String name) {
        if (text == null) {
            throw new NullPointerException(name);
        }
        return text;
    }

    static String metricString(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // Some titles reveal Shift_JIS/DoJa text by byte count. If the current frame ends on a
        // dangling lead byte, the host decoder surfaces a trailing U+FFFD that should stay invisible
        // until the next byte completes the character.
        // See https://github.com/GrenderG/openDoJa/issues/17
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == REPLACEMENT_CHARACTER) {
            end--;
        }
        return end == text.length() ? text : text.substring(0, end);
    }

    static String requireXString(XString text, String name) {
        return _XStringSupport.value(text, name);
    }

    static String slice(String text, int offset, int length) {
        validateSubstringRange(text, offset, length);
        return text.substring(offset, offset + length);
    }

    static void validateSubstringRange(String text, int offset, int length) {
        if (offset < 0 || length < 0 || offset > text.length() || offset + length > text.length()) {
            throw new StringIndexOutOfBoundsException();
        }
    }

    private static void validateTypeBits(int value) {
        if ((value & 0x70000000) != 0x70000000) {
            throw new IllegalArgumentException("type");
        }
    }

    private static int resolveAwtStyle(int style) {
        return switch (style) {
            case STYLE_BOLD -> java.awt.Font.BOLD;
            case STYLE_ITALIC -> java.awt.Font.ITALIC;
            case STYLE_BOLDITALIC -> java.awt.Font.BOLD | java.awt.Font.ITALIC;
            default -> java.awt.Font.PLAIN;
        };
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
        int normalized = (value & 0x0000FF00) == 0 ? value : (0x70000000 | (value & 0x0000FF00));
        return switch (normalized) {
            case SIZE_TINY, SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE -> resolveLogicalSize(normalized);
            default -> value > 0 && value < 256 ? value : resolveLogicalSize(defaultLogicalSizeConstant());
        };
    }

    private static int defaultLogicalSizeConstant() {
        return useLegacyBitmapLogicalSizes() ? SIZE_MEDIUM : SIZE_TINY;
    }

    private static int resolveLogicalSize(int logicalSize) {
        if (useLegacyBitmapLogicalSizes()) {
            return resolveLegacyLogicalSize(logicalSize);
        }
        return switch (logicalSize) {
            case SIZE_TINY -> 12;
            case SIZE_SMALL -> 16;
            case SIZE_MEDIUM -> 24;
            case SIZE_LARGE -> 30;
            default -> 24;
        };
    }

    private static int resolveLegacyLogicalSize(int logicalSize) {
        // Compatibility heuristic for pre-DoJa-3 profiles:
        // - The official DoJa 3.0 guide says DoJa-1.0/2.0 support SIZE_MEDIUM as the default size,
        //   with DoJa-2.0 low-level default text at 12 dots and DoJa-1.0/default-plus-other-font
        //   support varying by manufacturer:
        //   https://www.nttdocomo.co.jp/english/binary/pdf/service/developer/make/content/iappli/technical_data/doja/jguideforDoJa3_0_E.pdf
        //   (page 67, font notes).
        // - https://web.archive.org/web/20041101013339/http://www.nttdocomo.co.jp/p_s/imode/spec/info.html shows 12-dot default fonts across the
        //   shipped DoJa-2.x examples and many DoJa-1.0 examples, but it does not define one
        //   universal SMALL/MEDIUM/LARGE ladder.
        // To stay game- and device-agnostic while still exposing three distinct rungs for older
        // titles, openDoJa uses 10/12/16 here as an emulator policy rather than a literal DoJa
        // guarantee.
        return switch (logicalSize) {
            case SIZE_SMALL -> 10;
            case SIZE_MEDIUM -> 12;
            case SIZE_LARGE -> 16;
            // TINY is a DoJa-3-era addition, so older profiles fall back to the documented
            // default medium size instead of inventing a smaller legacy rung.
            case SIZE_TINY -> 12;
            default -> 12;
        };
    }

    private static boolean useLegacyBitmapLogicalSizes() {
        if (!BITMAP_FONT_ENABLED) {
            return false;
        }
        DoJaProfile profile = DoJaProfile.current();
        return profile.isBefore(3, 0);
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
                    java.awt.FontMetrics metrics = graphics.getFontMetrics();
                    int renderedHeight = metrics.getAscent() + metrics.getDescent();
                    int distance = Math.abs(renderedHeight - targetHeight);
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
        return Math.max(8, Math.round(logicalSize * HANDSET_FONT_SCALE));
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

    private static Object resolveTextAntialiasHint() {
        String value = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.TEXT_ANTIALIAS).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "off" -> RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
            case "gasp" -> RenderingHints.VALUE_TEXT_ANTIALIAS_GASP;
            case "lcd" -> RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
            default -> RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        };
    }

    private static String firstInstalled(String... candidates) {
        Set<String> families = availableFamilies();
        for (String candidate : candidates) {
            if (families.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return java.awt.Font.DIALOG;
    }

    private static Set<String> availableFamilies() {
        Set<String> cached = availableFamilies;
        if (cached != null) {
            return cached;
        }
        Set<String> families = new HashSet<>();
        try {
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                families.add(family.toLowerCase(Locale.ROOT));
            }
        } catch (HeadlessException | AWTError ignored) {
            // Bitmap fonts are the primary path; keep fallback font resolution resilient when no
            // desktop font environment is available.
        }
        families.add(java.awt.Font.DIALOG.toLowerCase(Locale.ROOT));
        families.add(java.awt.Font.SANS_SERIF.toLowerCase(Locale.ROOT));
        families.add(java.awt.Font.MONOSPACED.toLowerCase(Locale.ROOT));
        availableFamilies = families;
        return families;
    }
}
