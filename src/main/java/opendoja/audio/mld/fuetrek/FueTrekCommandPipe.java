package opendoja.audio.mld.fuetrek;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Native18fa0 command stage, relative job queue and18e70 producer yield checks. */
public final class FueTrekCommandPipe {
    public static final class Command {
        public int id, channel, ticks, argument;

        public Command(int id, int channel, int ticks, int argument) {
            this.id = id; this.channel = channel; this.ticks = ticks; this.argument = argument;
        }

        public Command copy() { return new Command(id, channel, ticks, argument); }
    }

    private static final class Job {
        int ticks;
        final int owner, tag;
        final Command command;
        final Runnable callback;

        Job(int ticks, int owner, int tag, Command command, Runnable callback) {
            this.ticks = ticks; this.owner = owner; this.tag = tag;
            this.command = command; this.callback = callback;
        }
    }

    private final FueTrekCommandSerializer serializer;
    private final List<Job> jobs = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private final Predicate<Command>[] filters = new Predicate[12];
    @SuppressWarnings("unchecked")
    private final Predicate<Command>[] interceptors = new Predicate[12];
    @SuppressWarnings("unchecked")
    private final Consumer<Command>[] observers = new Consumer[12];
    private final Runnable yieldCallback;
    private int parentFreeSlots, minimumParentSlots, minimumJobSlots;
    private int freeJobSlots;
    private int ownerWord;
    private boolean preview;
    private int previewCredit, previewPending, previewDivisor = 100;

    public FueTrekCommandPipe(FueTrekCommandSerializer serializer, int jobCapacity,
                              int parentFreeSlots, int minimumParentSlots,
                              int minimumJobSlots, Runnable yieldCallback) {
        if (jobCapacity < 0) throw new IllegalArgumentException("Negative command-job capacity");
        this.serializer = java.util.Objects.requireNonNull(serializer);
        this.freeJobSlots = jobCapacity;
        this.parentFreeSlots = parentFreeSlots;
        this.minimumParentSlots = minimumParentSlots;
        this.minimumJobSlots = minimumJobSlots;
        this.yieldCallback = java.util.Objects.requireNonNull(yieldCallback);
    }

    public void setFilter(int slot, Predicate<Command> filter) { filters[slot] = filter; }
    public void setInterceptor(int slot, Predicate<Command> interceptor) { interceptors[slot] = interceptor; }
    public void setObserver(int slot, Consumer<Command> observer) { observers[slot] = observer; }

    public void setParentFreeSlots(int slots) { parentFreeSlots = slots; }
    int ownerWord() { return ownerWord; }
    public void setMinimumJobSlots(int slots) { minimumJobSlots = slots; }
    public void setPreview(boolean enabled, int credit, int pending, int divisor) {
        preview = enabled; previewCredit = credit; previewPending = pending;
        previewDivisor = divisor & 65535;
    }

    /** Native19580: absolute delay from the current producer position; FIFO ordering on ties. */
    public boolean scheduleCommand(int ticks, int owner, int tag, Command command, boolean optional) {
        return insert(new Job(ticks, owner, tag, command.copy(), null), optional);
    }

    public boolean scheduleCallback(int ticks, int owner, int tag, Runnable callback, boolean optional) {
        return insert(new Job(ticks, owner, tag, null, java.util.Objects.requireNonNull(callback)), optional);
    }

    private boolean insert(Job job, boolean optional) {
        if (optional && Integer.compareUnsigned(freeJobSlots, minimumJobSlots * 2) <= 0) return false;
        if (freeJobSlots == 0) return false;
        freeJobSlots--;
        int index = 0;
        while (index < jobs.size() && Integer.compareUnsigned(job.ticks, jobs.get(index).ticks) >= 0) {
            job.ticks -= jobs.get(index).ticks;
            index++;
        }
        if (index < jobs.size()) jobs.get(index).ticks -= job.ticks;
        jobs.add(index, job);
        return true;
    }

    /** Native19650 removes the first matching owner/tag and repairs the next relative delay. */
    public int cancel(int owner, int tag) {
        int elapsed = 0;
        for (int index = 0; index < jobs.size(); index++) {
            Job job = jobs.get(index);
            elapsed += job.ticks;
            if (job.owner != owner || job.tag != tag) continue;
            if (index + 1 < jobs.size()) jobs.get(index + 1).ticks += job.ticks;
            jobs.remove(index);
            freeJobSlots++;
            return elapsed;
        }
        return 0;
    }

    /** Return status from the same points as native18fa0; mutates the caller's remaining delta. */
    public int submit(Command command) {
        int status = serializer.bufferStatus();
        while (!jobs.isEmpty()) {
            Job job = jobs.getFirst();
            if (Integer.compareUnsigned(job.ticks, command.ticks) > 0) {
                job.ticks -= command.ticks;
                break;
            }
            int issued = job.ticks;
            command.ticks -= issued;
            if (job.command != null) {
                job.command.ticks = issued;
                if (accepted(job.command)) serialize(job.command);
                else serializer.serialize(24, 0, job.command.ticks, 0);
            } else {
                serializer.serialize(24, 0, issued, 0);
                job.callback.run();
            }
            // Native19630 pops the current head after the callback returns.
            jobs.removeFirst();
            freeJobSlots++;
        }
        if (!accepted(command)) {
            serializer.serialize(24, 0, command.ticks, 0);
        } else {
            boolean consumed = false;
            for (Predicate<Command> interceptor : interceptors) {
                if (interceptor == null) break;
                if (interceptor.test(command)) consumed = true;
            }
            if (!consumed) status = serialize(command);
            for (Consumer<Command> observer : observers) {
                if (observer == null) break;
                observer.accept(command.copy());
            }
        }
        if (yieldReason() != 0) yieldCallback.run();
        return status;
    }

    public int submit(int id, int channel, int ticks, int argument) {
        return submit(new Command(id, channel, ticks, argument));
    }

    private boolean accepted(Command command) {
        for (Predicate<Command> filter : filters) {
            if (filter == null) break;
            if (!filter.test(command)) return false;
        }
        return true;
    }

    private int serialize(Command command) {
        return serializer.serialize(command.id, command.channel, command.ticks, command.argument);
    }

    /** Native18e70 includes unsigned preview arithmetic and owner-word side effects. */
    public int yieldReason() {
        ownerWord = 0;
        if (!preview) {
            int status = serializer.bufferStatus();
            if (status == 2 || status == 3) { ownerWord = 1; return 1; }
            if (Integer.compareUnsigned(parentFreeSlots, minimumParentSlots) < 0) { ownerWord = 1; return 2; }
            if (Integer.compareUnsigned(freeJobSlots, minimumJobSlots) < 0) { ownerWord = 1; return 3; }
        } else {
            if (previewCredit == 0) return 4;
            int amount = Integer.divideUnsigned(previewCredit * 100, previewDivisor);
            if (Integer.compareUnsigned(amount, previewPending) < 0) {
                previewCredit = 0;
                previewPending -= amount;
                return 4;
            }
            previewCredit -= Integer.divideUnsigned(previewPending * previewDivisor, 100);
            previewPending = 0;
        }
        return 0;
    }

    /** Pointer-free owner/preview/job state for native compatibility checks. */
    public int[] stateWords() {
        int[] state = new int[7 + jobs.size() * 8];
        state[0] = freeJobSlots; state[1] = ownerWord;
        state[2] = previewCredit; state[3] = previewPending;
        state[4] = previewDivisor; state[5] = preview ? 1 : 0; state[6] = jobs.size();
        int index = 7;
        for (Job job : jobs) {
            state[index++] = job.ticks; state[index++] = job.owner;
            state[index++] = job.command == null ? 1 : 0; state[index++] = job.tag;
            Command command = job.command;
            state[index++] = command == null ? 0 : command.id;
            state[index++] = command == null ? 0 : command.channel;
            state[index++] = command == null ? 0 : command.ticks;
            state[index++] = command == null ? 0 : command.argument;
        }
        return state;
    }
}
