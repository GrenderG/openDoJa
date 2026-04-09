package opendoja.probes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class Xf2RecordProbe {
    private Xf2RecordProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Xf2RecordProbe <xf2-resource> [name-filter]");
        }
        String resource = args[0];
        String filter = args.length == 2 ? args[1] : null;
        byte[] data = readResource(resource);
        int offset = 0;
        while (offset + 12 <= data.length) {
            int type = readUnsignedShort(data, offset);
            int id = readUnsignedShort(data, offset + 2);
            int parent = readInt(data, offset + 4);
            int size = readInt(data, offset + 8);
            int payloadStart = offset + 12;
            int end = payloadStart + size;
            if (size < 0 || end > data.length) {
                throw new IOException("Malformed XF2 record at offset " + offset);
            }
            if ((type == 101 || type == 103) && size >= 4) {
                String name = readCountedString(data, payloadStart + 4);
                if (name != null && (filter == null || name.contains(filter))) {
                    int transformOffset = payloadStart + 5 + name.length();
                    if (transformOffset + 40 <= end) {
                        System.out.println("type=" + type
                                + " id=" + id
                                + " parent=" + parent
                                + " name=" + name
                                + " rawParentRef=" + readInt(data, payloadStart));
                        System.out.println("  pos="
                                + readFloat(data, transformOffset) + ","
                                + readFloat(data, transformOffset + 4) + ","
                                + readFloat(data, transformOffset + 8));
                        System.out.println("  q="
                                + readFloat(data, transformOffset + 12) + ","
                                + readFloat(data, transformOffset + 16) + ","
                                + readFloat(data, transformOffset + 20) + ","
                                + readFloat(data, transformOffset + 24));
                        System.out.println("  scale="
                                + readFloat(data, transformOffset + 28) + ","
                                + readFloat(data, transformOffset + 32) + ","
                                + readFloat(data, transformOffset + 36));
                    }
                }
            }
            offset = end;
        }
    }

    private static byte[] readResource(String resource) throws IOException {
        try (InputStream stream = Xf2RecordProbe.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing resource: " + resource);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) >= 0) {
                if (read > 0) {
                    out.write(chunk, 0, read);
                }
            }
            return out.toByteArray();
        }
    }

    private static String readCountedString(byte[] data, int offset) {
        if (offset < 0 || offset >= data.length) {
            return null;
        }
        int length = data[offset] & 0xFF;
        int start = offset + 1;
        int end = start + length;
        if (length == 0 || end > data.length) {
            return null;
        }
        return new String(data, start, length, StandardCharsets.US_ASCII);
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | (data[offset + 3] << 24);
    }

    private static float readFloat(byte[] data, int offset) {
        return Float.intBitsToFloat(readInt(data, offset));
    }
}
