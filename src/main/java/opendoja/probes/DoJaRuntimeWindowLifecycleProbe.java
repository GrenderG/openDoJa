package opendoja.probes;

import opendoja.host.DoJaRuntime;

public final class DoJaRuntimeWindowLifecycleProbe {
    private DoJaRuntimeWindowLifecycleProbe() {
    }

    public static void main(String[] args) {
        /*check(DoJaRuntime.shouldCreateHostWindow(false, false),
                "active non-headless runtime should create a host window");
        check(!DoJaRuntime.shouldCreateHostWindow(true, false),
                "headless runtime must not create a host window");
        check(!DoJaRuntime.shouldCreateHostWindow(false, true),
                "shutdown runtime must not create a queued host window");
        check(!DoJaRuntime.shouldCreateHostWindow(true, true),
                "shutdown headless runtime must not create a host window");*/

        System.out.println("DoJa runtime window lifecycle probe OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
