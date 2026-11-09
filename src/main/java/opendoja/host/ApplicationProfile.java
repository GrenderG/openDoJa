package opendoja.host;

import java.util.Map;
import java.util.Properties;

/** The JAM's AppType field distinguishes STAR applications from DoJa applications. */
public enum ApplicationProfile {
    DOJA, STAR;

    public static ApplicationProfile fromParameters(Map<String, String> parameters) {
        return parameters != null && parameters.keySet().stream().anyMatch("AppType"::equalsIgnoreCase)
                ? STAR : DOJA;
    }

    public static ApplicationProfile fromJamProperties(Properties properties) {
        return properties != null && properties.stringPropertyNames().stream().anyMatch("AppType"::equalsIgnoreCase)
                ? STAR : DOJA;
    }

    public static ApplicationProfile current() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            return runtime.applicationProfile();
        }
        LaunchConfig prepared = DoJaRuntime.peekPreparedLaunch();
        return prepared == null ? DOJA : prepared.applicationProfile();
    }
}
