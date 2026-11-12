package opendoja.audio.mld.fuetrek;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/** SDK146e0/14850/14a90 component callback pool, including shared category slots. */
public final class FueTrekCallbackQueue {
    @FunctionalInterface
    public interface Listener {
        void invoke(int function, int context, int argument);
    }

    public static final class Registration {
        public final int category, owner, function, argument, flag;
        public int ticks, token;

        public Registration(int category, int owner, int function, int argument,
                            int ticks, int token, int flag) {
            this.category = category;
            this.owner = owner;
            this.function = function;
            this.argument = argument;
            this.ticks = ticks & 65535;
            this.token = token & 255;
            this.flag = flag & 255;
        }
    }

    private static final class Node {
        int function, argument, ticks, category, flag, owner, next;
    }

    private final Node[] pool;
    private final int[] slots, heads, serial;
    private final Listener listener;
    private final IntUnaryOperator context;
    private int freeHead, freeCount;
    private boolean active = true;

    public FueTrekCallbackQueue(int capacity, int[] categorySlots, Listener listener,
                               IntUnaryOperator context) {
        if (capacity < 0 || categorySlots.length != 32)
            throw new IllegalArgumentException("Invalid SDK callback pool");
        slots = categorySlots.clone();
        int headCount = 0;
        for (int slot : slots) {
            if (slot < 0 || slot > 32) throw new IllegalArgumentException("Invalid callback category slot");
            if (slot != 32) headCount = Math.max(headCount, slot + 1);
        }
        heads = new int[headCount];
        serial = new int[headCount];
        Arrays.fill(heads, -1);
        pool = new Node[capacity];
        for (int i = 0; i < capacity; i++) {
            pool[i] = new Node();
            pool[i].next = i + 1 < capacity ? i + 1 : -1;
        }
        freeCount = capacity;
        freeHead = capacity == 0 ? -1 : 0;
        this.listener = java.util.Objects.requireNonNull(listener);
        this.context = java.util.Objects.requireNonNull(context);
    }

    /** Original component kind3 has13 shared heads and50 callback records. */
    public static FueTrekCallbackQueue synth(Listener listener, IntUnaryOperator context) {
        return new FueTrekCallbackQueue(50, synthCategorySlots(), listener, context);
    }

    public static int[] synthCategorySlots() {
        int[] map = new int[32];
        Arrays.fill(map, 32);
        int[] pairs = {2,1,7,6,11,10,3,2,6,2,22,4,23,3,25,2,26,5,31,11,27,9,28,0,2,1,4,2,14,12};
        for (int i = 0; i < pairs.length; i += 2) map[pairs[i]] = pairs[i + 1];
        return map;
    }

    /** Original global timer component (kind11): three heads and20 records. */
    public static FueTrekCallbackQueue timers(Listener listener, IntUnaryOperator context) {
        int[] map = new int[32];
        Arrays.fill(map, 32);
        map[0] = 0;
        map[11] = 1;
        map[29] = 2;
        return new FueTrekCallbackQueue(20, map, listener, context);
    }

    public static FueTrekCallbackQueue parent(Listener listener, IntUnaryOperator context) {
        int[] map = {32,10,0,1,3,2,4,5,6,7,7,9,32,32,32,32,11,32,32,32,32,32,32,32,32,32,32,32,32,32,32,32};
        return new FueTrekCallbackQueue(50, map, listener, context);
    }

    private int slot(int category) {
        return category >= 0 && category < 32 ? slots[category] : 32;
    }

    private int allocate() {
        int id = freeHead;
        if (id >= 0) {
            freeHead = pool[id].next;
            freeCount--;
        }
        return id;
    }

    private void release(int id) {
        Node node = pool[id];
        node.function = node.argument = node.ticks = node.owner = 0;
        //145e0 retains the old category/flag bytes on free nodes.
        node.next = freeHead;
        freeHead = id;
        freeCount++;
    }

    /** Timed29 uses relative delays; ordinary categories use a wrapping16-bit serial. */
    public boolean register(Registration request) {
        int slot = slot(request.category);
        if (slot == 32) return false;
        int previous = -1, current = heads[slot];
        while (current >= 0) {
            Node node = pool[current];
            if (request.category == 29) {
                if (request.ticks < node.ticks) {
                    int id = allocate();
                    if (id >= 0) {
                        Node added = pool[id];
                        added.function = request.function;
                        added.argument = request.argument;
                        added.ticks = request.ticks;
                        added.flag = request.flag;
                        added.owner = request.owner;
                        added.next = current;
                        node.ticks = (node.ticks - request.ticks) & 65535;
                        if (previous < 0) heads[slot] = id;
                        else pool[previous].next = id;
                    }
                    // Native insertion-before neither stamps category/token nor reports success.
                    return false;
                }
                request.ticks = (request.ticks - node.ticks) & 65535;
            }
            previous = current;
            current = node.next;
        }
        int id = allocate();
        if (id < 0) return false;
        Node added = pool[id];
        added.function = request.function;
        added.argument = request.argument;
        added.ticks = request.category == 29 ? request.ticks : serial[slot];
        added.category = request.category;
        added.flag = request.flag;
        added.owner = request.owner;
        added.next = -1;
        if (previous < 0) heads[slot] = id;
        else pool[previous].next = id;
        request.token = serial[slot] & 255;
        serial[slot] = (serial[slot] + 1) & 65535;
        return true;
    }

    public boolean remove(int category, int owner, int function, int argument, int matchArgument) {
        int slot = slot(category);
        if (slot == 32) return false;
        int previous = -1, current = heads[slot];
        while (current >= 0) {
            Node node = pool[current];
            int next = node.next;
            if (node.function == function && node.owner == owner
                    && (matchArgument == 0 || matchArgument == 1 && node.argument == argument)) {
                if (category == 29 && next >= 0)
                    pool[next].ticks = (pool[next].ticks + node.ticks) & 65535;
                release(current);
                if (previous < 0) heads[slot] = next;
                else pool[previous].next = next;
                return true;
            }
            previous = current;
            current = next;
        }
        return false;
    }

    /**14920 does not repair relative delays when removing all records owned by a component. */
    public boolean removeOwner(int owner) {
        boolean valid = false;
        for (int slot : slots) {
            if (slot == 32) continue;
            valid = true;
            int previous = -1, current = heads[slot];
            while (current >= 0) {
                Node node = pool[current];
                int next = node.next;
                if (node.owner == owner) {
                    release(current);
                    if (previous < 0) heads[slot] = next;
                    else pool[previous].next = next;
                } else previous = current;
                current = next;
            }
        }
        return valid;
    }

    public boolean clear(int category) {
        int slot = slot(category);
        if (slot == 32) return false;
        while (heads[slot] >= 0) {
            int id = heads[slot], next = pool[id].next;
            release(id);
            heads[slot] = next;
        }
        return true;
    }

    public boolean has(int category) {
        int slot = slot(category);
        return slot != 32 && heads[slot] >= 0;
    }

    /**149b0 searches by callback function and owner without consuming a record. */
    public boolean contains(int category, int owner, int function) {
        int slot = slot(category);
        if (slot == 32) return false;
        for (int id = heads[slot]; id >= 0; id = pool[id].next)
            if (pool[id].owner == owner && pool[id].function == function) return true;
        return false;
    }

    int freeRecords() { return freeCount; }

    public void setActive(boolean active) { this.active = active; }

    /** Selector-1 delivers one ordinary callback; -2 selects through the last issued serial. */
    public boolean dispatch(int category, int selector) {
        int slot = slot(category);
        if (!active || slot == 32 || heads[slot] < 0) return false;
        if (selector == -2) selector = serial[slot] - 1;
        int count = 1;
        if (selector != -1) {
            if (category != 29) {
                int first = pool[heads[slot]].ticks;
                if (Integer.compareUnsigned(selector, first) != 0) {
                    selector = selector - first + 1
                            + (Integer.compareUnsigned(selector, first) < 0 ? 65536 : 0);
                    if (Integer.compareUnsigned(selector, 128) > 0) return false;
                } else selector = 1;
            }
            count = selector;
            if (count == 0) return false;
        }
        if (category == 29) {
            int ready = 0, current = heads[slot];
            while (count != 0 && current >= 0) {
                Node node = pool[current];
                if (Integer.compareUnsigned(count, node.ticks) < 0) {
                    node.ticks = (node.ticks - count) & 65535;
                    break;
                }
                count -= node.ticks;
                node.ticks = 0;
                ready++;
                current = node.next;
            }
            count = ready;
        }
        boolean called = false;
        while (count != 0 && heads[slot] >= 0) {
            Node node = pool[heads[slot]];
            if (category == 29 && node.ticks != 0) break;
            int function = node.function, owner = node.owner, argument = node.argument, flag = node.flag;
            remove(category, owner, function, 0, 0);
            listener.invoke(function, flag == 1 ? owner : context.applyAsInt(owner), argument);
            called = true;
            count--;
        }
        return called;
    }

    /** Portable pool/head snapshot: node references are one-based pool indices. */
    public int[] stateWords() {
        int[] words = new int[35 + heads.length * 2 + pool.length * 7];
        int index = 0;
        words[index++] = active ? 1 : 0;
        words[index++] = freeCount;
        words[index++] = freeHead + 1;
        for (int value : slots) words[index++] = value;
        for (int i = 0; i < heads.length; i++) {
            words[index++] = heads[i] + 1;
            words[index++] = serial[i];
        }
        for (Node node : pool) {
            words[index++] = node.function;
            words[index++] = node.argument;
            words[index++] = node.ticks;
            words[index++] = node.category;
            words[index++] = node.flag;
            words[index++] = node.owner;
            words[index++] = node.next + 1;
        }
        return words;
    }
}
