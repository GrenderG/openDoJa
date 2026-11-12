package opendoja.audio.mld.fuetrek;

/** SDK2a530..2a610 nested state-table interpreter. */
public final class FueTrekStateMachine {
    @FunctionalInterface
    public interface Callback { int invoke(FueTrekStateMachine machine); }
    public record Transition(Callback action, int kind, int request, int next) {}
    public record State(Callback enter, Callback poll, Callback exit, Transition... transitions) {}

    private final FueTrekStateMachine child;
    private int requested, current, flags;

    public FueTrekStateMachine(FueTrekStateMachine child, int requested, int current, int flags) {
        this.child = child;
        this.requested = requested;
        this.current = current;
        this.flags = flags & 255;
    }

    public void reset() {
        if (child != null) child.reset();
        current = requested = 0;
        flags = flags & 0xfd | 1;
    }

    public void activate() {
        if (child != null) child.activate();
        flags = flags & 0xfd | 1;
    }

    public void request(int state) {
        if ((flags & 3) == 2 && current != state) {
            current = requested = state;
            flags = flags & 0xfd | 1;
            if (child != null) child.reset();
        } else {
            requested = state;
            activate();
        }
    }

    public boolean different() {
        return child != null && child.different() || current != requested;
    }

    private void resetChild() { if (child != null) child.reset(); }
    private static void call(Callback callback, FueTrekStateMachine machine) {
        if (callback != null) callback.invoke(machine);
    }

    public int run(State[] table) {
        if ((flags & 4) != 0) return 0;
        int initialFlags = flags;
        flags |= 4;
        int previousIndex = current;
        int result = 0;
        if (current >= table.length) current = 0;
        if ((initialFlags & 3) == 1) {
            State state = table[previousIndex];
            while (true) {
                Transition transition = null;
                if (state.transitions != null) {
                    for (Transition candidate : state.transitions) {
                        transition = candidate;
                        if (candidate.request == 0 || candidate.request == requested) break;
                    }
                }
                if (transition != null && transition.kind == 1) {
                    call(transition.action, this);
                    call(state.exit, this);
                    current = transition.next;
                    resetChild();
                    state = table[current];
                    call(state.enter, this);
                    continue;
                }
                if (transition != null && transition.kind == 3) {
                    result = 2;
                    flags = flags & 0xfc ^ 2;
                    break;
                }
                if (result != 0) break;
                if (state.poll != null) result = state.poll.invoke(this);
                if (result == 1) break;
                if (result == 2) {
                    flags = flags & 0xfe | 2;
                    break;
                }
                if (transition != null && transition.kind == 2) {
                    call(state.exit, this);
                    current = transition.next;
                    resetChild();
                    state = table[current];
                    call(transition.action, this);
                    call(state.enter, this);
                }
            }
            if ((flags & 3) == 1) flags &= 0xfc;
        }
        flags &= 0xfb;
        return result;
    }

    public int[] stateWords() { return new int[]{requested, current, flags}; }
}
