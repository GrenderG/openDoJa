package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bitmap font backend loaded from extracted handset glyph dumps.
 *
 * <p>The bundled dump was extracted from a 900i-series phone and is treated as the authoritative
 * source for this renderer. Thanks to GuyPerfect for extracting and preserving the handset font
 * data used here.</p>
 *
 * <p>Each size has a {@code code-points.dat} table plus one raw glyph-bitmap file. The code-point
 * table is a list of 16-bit little-endian Unicode values in the same order as the glyphs appear in
 * the bitmap file. The glyph file name encodes the bitmap height, and the stored bitmap width is
 * the smallest multiple of eight that is at least that height. Every bit is one pixel with the
 * left-most pixel in the MSB; set bits are foreground pixels and cleared bits are transparent.</p>
 *
 * <p>The dump also distinguishes half-width code points from full-width ones, so layout here keeps
 * the handset width rules while blitting only the visible half-width columns. Baseline/ascent
 * metrics are not stored in the source files, so the vertical metrics below remain inferred from
 * the earlier handset-font prototype and retained to keep existing DoJa layout call sites stable.</p>
 */
class _BitmapFont extends Font {
    private static final String RESOURCE_ROOT = "/opendoja/fonts/bitmap/";
    private static final int[] SUPPORTED_SIZES = {12, 16, 20, 24, 30};
    private static final int QUESTION_MARK = 0x003F;
    private static final int SPACE = 0x0020;
    private static final int IDEOGRAPHIC_SPACE = 0x3000;
    private static final java.awt.Font PLACEHOLDER_FONT = new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12);
    private static final Map<Integer, Strike> STRIKES = loadStrikes();
    private static final int MAX_RENDER_CACHE_ENTRIES = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.BITMAP_FONT_CACHE_ENTRIES);
    // UI-heavy titles redraw the same handset strings every frame. Cache the rasterized result so
    // repeated dialog/menu paints do not allocate and blit glyph bitmaps from scratch each time.
    private static final Map<RenderKey, BufferedImage> RENDER_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RenderKey, BufferedImage> eldest) {
            return size() > MAX_RENDER_CACHE_ENTRIES;
        }
    };

    private final Strike strike;

    private _BitmapFont(Strike strike) {
        super(PLACEHOLDER_FONT);
        this.strike = strike;
    }

    static Font create(int face, int style, int size) {
        Strike strike = STRIKES.get(selectSize(size));
        if (strike == null) {
            return null;
        }
        // The extracted dump is a single handset font face. Preserve the public API shape, but all
        // DoJa faces/styles resolve to the same source glyphs until additional handset dumps exist.
        return new _BitmapFont(strike);
    }

    public static int[] getSupportedFontSizes() {
        return SUPPORTED_SIZES.clone();
    }

    @Override
    public int getAscent() {
        return strike.baseline;
    }

    @Override
    public int getDescent() {
        return strike.descent;
    }

    @Override
    public int getHeight() {
        return strike.lineHeight;
    }

    @Override
    public int getBBoxHeight(String text) {
        String value = Font.metricString(text);
        if (value.isEmpty()) {
            return 0;
        }
        return value.split("\\n", -1).length * strike.lineHeight;
    }

    @Override
    public int getBBoxHeight(XString text) {
        return getBBoxHeight(Font.requireXString(text, "text"));
    }

    @Override
    public int getBBoxWidth(String text) {
        String value = Font.metricString(text);
        int width = 0;
        for (String line : value.split("\\r?\\n", -1)) {
            width = Math.max(width, lineWidth(line));
        }
        return width;
    }

    @Override
    public int getBBoxWidth(XString text) {
        return getBBoxWidth(Font.requireXString(text, "text"));
    }

    @Override
    public int getBBoxWidth(XString text, int offset, int length) {
        return getBBoxWidth(Font.slice(Font.requireXString(text, "text"), offset, length));
    }

    @Override
    public int stringWidth(String text) {
        return getBBoxWidth(text);
    }

    @Override
    public int stringWidth(XString text) {
        return getBBoxWidth(text);
    }

    @Override
    public int stringWidth(XString text, int offset, int length) {
        return getBBoxWidth(text, offset, length);
    }

    @Override
    void drawString(Graphics2D graphics, String text, int x, int y, int argbColor) {
        BufferedImage image = rendered(text, argbColor);
        if (image != null) {
            graphics.drawImage(image, x, y - strike.baseline, null);
        }
    }

    private BufferedImage rendered(String value, int argbColor) {
        String text = value == null ? "" : value;
        RenderKey key = new RenderKey(strike, argbColor, text);
        synchronized (RENDER_CACHE) {
            BufferedImage cached = RENDER_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        BufferedImage image = draw(text, argbColor);
        if (image == null) {
            return null;
        }
        synchronized (RENDER_CACHE) {
            RENDER_CACHE.put(key, image);
        }
        return image;
    }

    private BufferedImage draw(String text, int argbColor) {
        String[] lines = text.split("\\r?\\n", -1);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, lineWidth(line));
        }
        int height = lines.length * strike.lineHeight;
        if (width <= 0 || height <= 0) {
            return null;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            int cursorX = 0;
            int cursorY = lineIndex * strike.lineHeight;
            int[] codePoints = lines[lineIndex].codePoints().toArray();
            for (int codePoint : codePoints) {
                int advance = advanceFor(codePoint);
                int glyphIndex = glyphIndexFor(codePoint);
                if (glyphIndex >= 0) {
                    blitGlyph(pixels, width, height, cursorX, cursorY, glyphIndex, argbColor, advance);
                }
                cursorX += advance;
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private void blitGlyph(int[] target, int targetWidth, int targetHeight, int dx, int dy, int glyphIndex, int argbColor) {
        blitGlyph(target, targetWidth, targetHeight, dx, dy, glyphIndex, argbColor, strike.fullAdvance());
    }

    private void blitGlyph(int[] target, int targetWidth, int targetHeight, int dx, int dy, int glyphIndex, int argbColor, int visibleWidth) {
        int glyphOffset = glyphIndex * strike.bytesPerGlyph;
        for (int row = 0; row < strike.height; row++) {
            int targetY = dy + row;
            if (targetY < 0 || targetY >= targetHeight) {
                continue;
            }
            int rowOffset = glyphOffset + row * strike.bytesPerRow;
            for (int byteIndex = 0; byteIndex < strike.bytesPerRow; byteIndex++) {
                int bits = strike.glyphData[rowOffset + byteIndex] & 0xFF;
                if (bits == 0) {
                    continue;
                }
                for (int bit = 0; bit < 8; bit++) {
                    if ((bits & (0x80 >>> bit)) == 0) {
                        continue;
                    }
                    int targetX = dx + byteIndex * 8 + bit;
                    if (targetX - dx >= visibleWidth) {
                        continue;
                    }
                    if (targetX < 0 || targetX >= targetWidth) {
                        continue;
                    }
                    target[targetY * targetWidth + targetX] = argbColor;
                }
            }
        }
    }

    private int lineWidth(String line) {
        int width = 0;
        for (int codePoint : line.codePoints().toArray()) {
            width += advanceFor(codePoint);
        }
        return width;
    }

    private int advanceFor(int codePoint) {
        int effectiveCodePoint = effectiveCodePoint(codePoint);
        if (effectiveCodePoint == SPACE) {
            return strike.halfAdvance();
        }
        if (effectiveCodePoint == IDEOGRAPHIC_SPACE) {
            return strike.fullAdvance();
        }
        return isHalfWidth(effectiveCodePoint) ? strike.halfAdvance() : strike.fullAdvance();
    }

    private int glyphIndexFor(int codePoint) {
        int effectiveCodePoint = effectiveCodePoint(codePoint);
        if (effectiveCodePoint == SPACE || effectiveCodePoint == IDEOGRAPHIC_SPACE) {
            return -1;
        }
        return strike.codePointToGlyph.getOrDefault(effectiveCodePoint, strike.questionMarkIndex);
    }

    private int effectiveCodePoint(int codePoint) {
        if (codePoint == SPACE || codePoint == IDEOGRAPHIC_SPACE) {
            return codePoint;
        }
        if (strike.codePointToGlyph.containsKey(codePoint)) {
            return codePoint;
        }
        return QUESTION_MARK;
    }

    private static boolean isHalfWidth(int codePoint) {
        return codePoint <= 0x00FF || (codePoint >= 0xFF61 && codePoint <= 0xFFDC);
    }

    private static int selectSize(int requestedSize) {
        if (requestedSize <= SUPPORTED_SIZES[0]) {
            return SUPPORTED_SIZES[0];
        }
        int selected = SUPPORTED_SIZES[0];
        for (int size : SUPPORTED_SIZES) {
            if (requestedSize < size) {
                break;
            }
            selected = size;
        }
        return selected;
    }

    private static Map<Integer, Strike> loadStrikes() {
        try {
            int[] codePoints = loadCodePoints();
            Map<Integer, Strike> strikes = new HashMap<>();
            for (int height : SUPPORTED_SIZES) {
                byte[] glyphData = readResource("glyphs-" + height + ".dat");
                int width = deriveWidth(height);
                int bytesPerRow = width / 8;
                int bytesPerGlyph = bytesPerRow * height;
                if (glyphData.length != codePoints.length * bytesPerGlyph) {
                    throw new IOException("Unexpected glyph file length for height " + height);
                }
                Map<Integer, Integer> codePointToGlyph = new HashMap<>(codePoints.length * 2);
                for (int i = 0; i < codePoints.length; i++) {
                    codePointToGlyph.put(codePoints[i], i);
                }
                int questionMarkIndex = codePointToGlyph.getOrDefault(QUESTION_MARK, -1);
                strikes.put(height, new Strike(
                        height,
                        width,
                        bytesPerRow,
                        bytesPerGlyph,
                        glyphData,
                        codePointToGlyph,
                        questionMarkIndex,
                        inferredBaseline(height),
                        inferredDescent(height),
                        height
                ));
            }
            return strikes;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load handset bitmap fonts", e);
        }
    }

    private static int[] loadCodePoints() throws IOException {
        byte[] data = readResource("code-points.dat");
        if ((data.length & 1) != 0) {
            throw new IOException("Invalid code-point table length");
        }
        int[] codePoints = new int[data.length / 2];
        for (int i = 0, source = 0; i < codePoints.length; i++) {
            int low = data[source++] & 0xFF;
            int high = data[source++] & 0xFF;
            codePoints[i] = low | (high << 8);
        }
        return codePoints;
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = _BitmapFont.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (in == null) {
                throw new IOException("Missing bitmap font resource: " + name);
            }
            return in.readAllBytes();
        }
    }

    private static int deriveWidth(int height) {
        return ((height + 7) / 8) * 8;
    }

    private static int inferredBaseline(int height) {
        return height - inferredDescent(height);
    }

    private static int inferredDescent(int height) {
        return 2;
    }

    private record Strike(
            int height,
            int width,
            int bytesPerRow,
            int bytesPerGlyph,
            byte[] glyphData,
            Map<Integer, Integer> codePointToGlyph,
            int questionMarkIndex,
            int baseline,
            int descent,
            int lineHeight
    ) {
        private Strike {
            glyphData = Arrays.copyOf(glyphData, glyphData.length);
            codePointToGlyph = Map.copyOf(codePointToGlyph);
        }

        int fullAdvance() {
            return height;
        }

        int halfAdvance() {
            return height / 2;
        }
    }

    private record RenderKey(Strike strike, int argbColor, String text) {
    }
}
