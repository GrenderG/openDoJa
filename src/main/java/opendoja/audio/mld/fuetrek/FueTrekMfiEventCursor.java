package opendoja.audio.mld.fuetrek;

/** Classic24a90/24b10 track merge; callers may yield between individual events. */
public final class FueTrekMfiEventCursor {
    public record Event(int track, int index, int delta) {}

    private final int[][] deltas;
    private final int[] positions;
    private final int[] remaining;
    private final boolean[] finished;

    public FueTrekMfiEventCursor(int[][] deltas) {
        if (deltas.length > 4) throw new IllegalArgumentException("Classic MFi has at most four tracks");
        this.deltas = new int[deltas.length][];
        positions = new int[deltas.length];
        remaining = new int[deltas.length];
        finished = new boolean[deltas.length];
        for (int track = 0; track < deltas.length; track++) {
            this.deltas[track] = deltas[track].clone();
            for (int delta : deltas[track]) {
                if (delta < 0 || delta > 65535) throw new IllegalArgumentException("Invalid classic track delta");
            }
            finished[track] = deltas[track].length == 0;
            if (!finished[track]) remaining[track] = deltas[track][0];
        }
    }

    public void finishTrack(int track) { finished[track] = true; }

    public Event next() {
        int chosen = -1;
        for (int track = 0; track < deltas.length; track++) {
            if (!finished[track] && (chosen < 0 || remaining[track] < remaining[chosen])) chosen = track;
        }
        if (chosen < 0) return null;
        int delta = remaining[chosen];
        for (int track = 0; track < deltas.length; track++) {
            if (!finished[track]) remaining[track] = (remaining[track] - delta) & 65535;
        }
        int index = positions[chosen]++;
        if (positions[chosen] == deltas[chosen].length) finished[chosen] = true;
        else remaining[chosen] = deltas[chosen][positions[chosen]];
        return new Event(chosen, index, delta);
    }
}
