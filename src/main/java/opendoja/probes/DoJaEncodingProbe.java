package opendoja.probes;

import opendoja.host.DoJaEncoding;

import java.nio.charset.Charset;

public final class DoJaEncodingProbe {
    private DoJaEncodingProbe() {
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: DoJaEncodingProbe [expected-charset]");
        }
        String resolved = DoJaEncoding.defaultCharsetName();
        String runtimeDefault = Charset.defaultCharset().name();
        check(resolved.equals(DoJaEncoding.defaultCharset().name()),
                "DoJaEncoding.defaultCharset() and defaultCharsetName() should agree");
        if (args.length == 1) {
            check(args[0].equals(resolved),
                    "DoJaEncoding should resolve the explicitly launched file.encoding value");
            check(runtimeDefault.equals(resolved),
                    "DoJaEncoding should match Charset.defaultCharset() when file.encoding was set on launch");
        }
        System.out.println("DoJa encoding probe OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message
                    + " (resolved=" + DoJaEncoding.defaultCharsetName()
                    + ", runtimeDefault=" + Charset.defaultCharset().name() + ")");
        }
    }
}
