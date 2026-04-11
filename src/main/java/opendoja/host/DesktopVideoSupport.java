package opendoja.host;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class DesktopVideoSupport {
    private static final int BOX_HEADER_BYTES = 8;
    private static final int EXTENDED_BOX_HEADER_BYTES = 16;

    private DesktopVideoSupport() {
    }

    public static boolean looksLikeVideo(byte[] data) {
        return probe(data).isVideo();
    }

    public static VideoMetadata probe(byte[] data) {
        if (data == null || data.length < 12) {
            return VideoMetadata.notVideo();
        }
        int ftypOffset = indexOfAscii(data, "ftyp", 0, Math.min(64, data.length));
        if (ftypOffset < 0 || ftypOffset + 8 > data.length) {
            return VideoMetadata.notVideo();
        }
        String brand = new String(data, ftypOffset + 4, 4, StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
        boolean video = brand.contains("3gp") || brand.contains("mp4") || brand.contains("isom") || brand.contains("m4v");
        if (!video) {
            return VideoMetadata.notVideo();
        }
        byte[] isolated = isolateContainer(data);
        long durationMillis = parseMovieDurationMillis(isolated);
        String extension = brand.contains("3gp") ? "3gp" : "mp4";
        return new VideoMetadata(true, isolated, extension, durationMillis);
    }

    public static byte[] isolateContainer(byte[] data) {
        if (data == null) {
            return new byte[0];
        }
        int containerLength = detectContainerLength(data);
        if (containerLength <= 0 || containerLength >= data.length) {
            return data.clone();
        }
        return Arrays.copyOf(data, containerLength);
    }

    private static int detectContainerLength(byte[] data) {
        int offset = 0;
        int lastEnd = 0;
        boolean sawMovieBox = false;
        boolean sawMediaData = false;
        while (offset + BOX_HEADER_BYTES <= data.length) {
            long boxSize = readUInt32(data, offset);
            int headerBytes = BOX_HEADER_BYTES;
            if (boxSize == 1L) {
                if (offset + EXTENDED_BOX_HEADER_BYTES > data.length) {
                    break;
                }
                boxSize = readUInt64(data, offset + BOX_HEADER_BYTES);
                headerBytes = EXTENDED_BOX_HEADER_BYTES;
            } else if (boxSize == 0L) {
                lastEnd = data.length;
                break;
            }
            if (boxSize < headerBytes) {
                break;
            }
            long end = offset + boxSize;
            if (end > data.length) {
                break;
            }
            String type = new String(data, offset + 4, 4, StandardCharsets.US_ASCII);
            lastEnd = (int) end;
            if ("mdat".equals(type)) {
                sawMediaData = true;
            } else if ("moov".equals(type)) {
                sawMovieBox = true;
                if (sawMediaData) {
                    break;
                }
            }
            offset = (int) end;
        }
        return sawMovieBox ? lastEnd : 0;
    }

    private static long parseMovieDurationMillis(byte[] data) {
        Box mvhd = findBox(data, 0, data.length, "moov", "mvhd");
        if (mvhd == null) {
            return 0L;
        }
        int payloadOffset = mvhd.payloadOffset();
        int payloadLength = mvhd.payloadLength();
        if (payloadLength < 20) {
            return 0L;
        }
        int version = data[payloadOffset] & 0xFF;
        if (version == 0) {
            if (payloadLength < 20) {
                return 0L;
            }
            long timescale = readUInt32(data, payloadOffset + 12);
            long duration = readUInt32(data, payloadOffset + 16);
            return toMillis(duration, timescale);
        }
        if (version == 1) {
            if (payloadLength < 32) {
                return 0L;
            }
            long timescale = readUInt32(data, payloadOffset + 20);
            long duration = readUInt64(data, payloadOffset + 24);
            return toMillis(duration, timescale);
        }
        return 0L;
    }

    private static long toMillis(long duration, long timescale) {
        if (duration <= 0L || timescale <= 0L) {
            return 0L;
        }
        double millis = (duration * 1000.0d) / timescale;
        return Math.max(0L, Math.round(millis));
    }

    private static Box findBox(byte[] data, int start, int end, String... path) {
        if (path.length == 0) {
            return null;
        }
        int cursor = start;
        while (cursor + BOX_HEADER_BYTES <= end) {
            long size = readUInt32(data, cursor);
            int headerBytes = BOX_HEADER_BYTES;
            if (size == 1L) {
                if (cursor + EXTENDED_BOX_HEADER_BYTES > end) {
                    return null;
                }
                size = readUInt64(data, cursor + BOX_HEADER_BYTES);
                headerBytes = EXTENDED_BOX_HEADER_BYTES;
            } else if (size == 0L) {
                size = end - cursor;
            }
            if (size < headerBytes) {
                return null;
            }
            long next = cursor + size;
            if (next > end) {
                return null;
            }
            String type = new String(data, cursor + 4, 4, StandardCharsets.US_ASCII);
            if (path[0].equals(type)) {
                if (path.length == 1) {
                    return new Box(cursor, headerBytes, (int) size);
                }
                Box nested = findBox(data, cursor + headerBytes, (int) next, Arrays.copyOfRange(path, 1, path.length));
                if (nested != null) {
                    return nested;
                }
            }
            cursor = (int) next;
        }
        return null;
    }

    private static long readUInt32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFFL);
    }

    private static long readUInt64(byte[] data, int offset) {
        long high = readUInt32(data, offset);
        long low = readUInt32(data, offset + 4);
        return (high << 32) | low;
    }

    private static int indexOfAscii(byte[] data, String needle, int start, int end) {
        byte[] target = needle.getBytes(StandardCharsets.US_ASCII);
        int limit = Math.max(start, end - target.length + 1);
        for (int i = Math.max(0, start); i < limit; i++) {
            boolean matches = true;
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i;
            }
        }
        return -1;
    }

    private record Box(int offset, int headerBytes, int size) {
        private int payloadOffset() {
            return offset + headerBytes;
        }

        private int payloadLength() {
            return size - headerBytes;
        }
    }

    public record VideoMetadata(boolean isVideo, byte[] data, String extension, long durationMillis) {
        public static VideoMetadata notVideo() {
            return new VideoMetadata(false, new byte[0], "3gp", 0L);
        }
    }
}
