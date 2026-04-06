package opendoja.probes;

import opendoja.audio.mld.MLD;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MldTrackDump {
    private MldTrackDump() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 4) {
            throw new IllegalArgumentException("usage: MldTrackDump <mld-file> [track] [start] [count]");
        }
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        MLD mld = new MLD(source);
        Object[] tracks = (Object[]) field(MLD.class, "tracks").get(mld);
        int trackIndex = args.length >= 2 ? Integer.parseInt(args[1]) : 0;
        int start = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
        int count = args.length >= 4 ? Integer.parseInt(args[3]) : 32;
        if (trackIndex < 0 || trackIndex >= tracks.length) {
            throw new IllegalArgumentException("invalid track: " + trackIndex);
        }
        @SuppressWarnings("unchecked")
        java.util.List<Object> events = (java.util.List<Object>) tracks[trackIndex];
        int end = Math.min(events.size(), start + count);
        for (int i = start; i < end; i++) {
            Object event = events.get(i);
            Class<?> type = event.getClass();
            int eventType = field(type, "type").getInt(event);
            String extra = "";
            if (eventType == 0) {
                extra = String.format(
                    " key=%d keyNum=%d octaveShift=%d velocity=%.6f gate=%d",
                    field(type, "key").getInt(event),
                    field(type, "keyNumber").getInt(event),
                    field(type, "octaveShift").getInt(event),
                    field(type, "velocity").getDouble(event),
                    field(type, "gateTime").getInt(event));
            }
            System.out.printf(
                "#%d delta=%d type=%d id=0x%02x param=0x%02x chIdx=%d ch=%d raw=%d..%d%s%n",
                i,
                field(type, "delta").getInt(event),
                eventType,
                field(type, "id").getInt(event) & 0xff,
                field(type, "param").getInt(event) & 0xff,
                field(type, "channelIndex").getInt(event),
                field(type, "channel").getInt(event),
                field(type, "offset").getInt(event),
                field(type, "endOffset").getInt(event),
                extra);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
