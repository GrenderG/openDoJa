package opendoja.host;

import com.nttdocomo.ui.IApplication;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static IApplication launch(Class<? extends IApplication> applicationClass, String... args) {
        LaunchConfig config = LaunchConfig.builder(applicationClass)
                .args(args)
                .build();
        return launch(config);
    }

    public static IApplication launch(LaunchConfig config) {
        DoJaRuntime runtime = null;
        boolean launched = false;
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        // JAM-loaded titles expect ordinary context-loader resource lookups to resolve against the app jar.
        Thread.currentThread().setContextClassLoader(config.applicationClass().getClassLoader());
        try {
            DoJaRuntime.prepareLaunch(config);
            runtime = DoJaRuntime.bootstrap(config);
            IApplication app = config.applicationClass().getDeclaredConstructor().newInstance();
            runtime.attachApplication(app);
            runtime.startApplication();
            launched = true;
            return app;
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof Error error) {
                throw error;
            }
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to launch " + config.applicationClass().getName(), target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to launch " + config.applicationClass().getName(), e);
        } finally {
            if (!launched && runtime != null) {
                runtime.abortLaunch();
            }
            DoJaRuntime.clearPreparedLaunch();
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        List<String> effectiveArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (OpenDoJaCliFlags.PHONE_MODEL.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(
                            "Usage: DesktopLauncher [" + OpenDoJaCliFlags.PHONE_MODEL + " <model>] ["
                                    + OpenDoJaCliFlags.SHOW_OPEN_GLES_FPS
                                    + "] <fully.qualified.IApplicationClass> [args...]");
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.MICROEDITION_PLATFORM_OVERRIDE, args[++i]);
                continue;
            }
            if (OpenDoJaCliFlags.SHOW_OPEN_GLES_FPS.equals(args[i])) {
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.SHOW_OPEN_GLES_FPS, Boolean.TRUE.toString());
                continue;
            }
            effectiveArgs.add(args[i]);
        }
        if (effectiveArgs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: DesktopLauncher [" + OpenDoJaCliFlags.PHONE_MODEL + " <model>] ["
                            + OpenDoJaCliFlags.SHOW_OPEN_GLES_FPS
                            + "] <fully.qualified.IApplicationClass> [args...]");
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Class<?> rawClass = contextLoader == null
                ? Class.forName(effectiveArgs.get(0), false, DesktopLauncher.class.getClassLoader())
                : Class.forName(effectiveArgs.get(0), false, contextLoader);
        if (!IApplication.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException(effectiveArgs.get(0) + " does not extend com.nttdocomo.ui.IApplication");
        }
        @SuppressWarnings("unchecked")
        Class<? extends IApplication> applicationClass = (Class<? extends IApplication>) rawClass;
        String[] appArgs = new String[Math.max(0, effectiveArgs.size() - 1)];
        if (appArgs.length > 0) {
            for (int i = 1; i < effectiveArgs.size(); i++) {
                appArgs[i - 1] = effectiveArgs.get(i);
            }
        }
        LaunchConfig config = LaunchConfig.builder(applicationClass)
                .args(appArgs)
                .exitOnShutdown(true)
                .build();
        launch(config);
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            try {
                runtime.awaitShutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.exit(0);
    }
}
