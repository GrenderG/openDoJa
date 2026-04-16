package opendoja.probes;

import com.nttdocomo.ui.MApplication;
import opendoja.host.DesktopLauncher;
import opendoja.host.DoJaRuntime;

public final class MApplicationLaunchActivationProbe {
    private MApplicationLaunchActivationProbe() {
    }

    public static void main(String[] args) {
        StandbyActivationApp.reset();
        try {
            DesktopLauncher.launch(StandbyActivationApp.class);
            if (!StandbyActivationApp.started) {
                throw new IllegalStateException("start() was not called");
            }
            if (!StandbyActivationApp.modeChanged) {
                throw new IllegalStateException("MODE_CHANGED_EVENT was not delivered after startup deactivate()");
            }
            if (!StandbyActivationApp.activeAfterModeChange) {
                throw new IllegalStateException("MApplication did not become active before MODE_CHANGED_EVENT");
            }
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime == null || !(runtime.application() instanceof StandbyActivationApp app) || !app.isActive()) {
                throw new IllegalStateException("runtime application should be active after launch activation");
            }
            System.out.println("MApplication launch activation probe OK");
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
    }

    public static final class StandbyActivationApp extends MApplication {
        private static boolean started;
        private static boolean modeChanged;
        private static boolean activeAfterModeChange;

        static void reset() {
            started = false;
            modeChanged = false;
            activeAfterModeChange = false;
        }

        @Override
        public void start() {
            started = true;
            deactivate();
        }

        @Override
        public void processSystemEvent(int type, int param) {
            if (type == MODE_CHANGED_EVENT) {
                modeChanged = true;
                activeAfterModeChange = isActive();
            }
        }
    }
}
