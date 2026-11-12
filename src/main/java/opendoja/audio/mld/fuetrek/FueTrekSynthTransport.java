package opendoja.audio.mld.fuetrek;

/** Host09d50/09de0 ring refill into the ordinary emulated synth-port register bank. */
public final class FueTrekSynthTransport {
    public interface Port {
        int readRegister(int address);
        void writeBytes(byte[] bytes, int offset, int length);
    }

    private FueTrekSynthTransport() {}

    public static int refill(FueTrekCommandSerializer source, Port port) {
        int total = 0;
        boolean sent = false;
        while (true) {
            int free = port.readRegister(0x14);
            if (free != 0) free--;
            int available = source.readableBytes();
            if ((sent && free < 31) || available == 0) return total;
            int amount = Math.min(free, available);
            if (amount == 0) return total;
            total += source.writeTo(port, amount);
            sent = true;
        }
    }
}
