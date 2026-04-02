package opendoja.host;

import com.nttdocomo.ui.IApplication;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class LaunchConfig {
    public static final String DEFAULT_STATUS_BAR_ICON_DEVICE = "n900i";
    public static final int DEFAULT_VIEWPORT_WIDTH = 240;
    public static final int DEFAULT_VIEWPORT_HEIGHT = 240;

    private final Class<? extends IApplication> applicationClass;
    private final int width;
    private final int height;
    private final String title;
    private final String[] args;
    private final int launchType;
    private final String sourceUrl;
    private final Map<String, String> parameters;
    private final Path scratchpadRoot;
    private final Path scratchpadPackedFile;
    private final int[] scratchpadSizes;
    private final String statusBarIconDevice;
    private final IAppliType iAppliType;
    private final boolean externalFrameEnabled;
    private final boolean exitOnShutdown;

    private LaunchConfig(Builder builder) {
        this.applicationClass = builder.applicationClass;
        this.width = builder.width;
        this.height = builder.height;
        this.title = builder.title;
        this.args = builder.args;
        this.launchType = builder.launchType;
        this.sourceUrl = builder.sourceUrl;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(builder.parameters));
        this.scratchpadRoot = builder.scratchpadRoot;
        this.scratchpadPackedFile = builder.scratchpadPackedFile;
        this.scratchpadSizes = builder.scratchpadSizes == null ? new int[0] : builder.scratchpadSizes.clone();
        this.statusBarIconDevice = builder.statusBarIconDevice;
        this.iAppliType = builder.iAppliType;
        this.externalFrameEnabled = builder.externalFrameEnabled;
        this.exitOnShutdown = builder.exitOnShutdown;
    }

    public static Builder builder(Class<? extends IApplication> applicationClass) {
        return new Builder(applicationClass);
    }

    public Class<? extends IApplication> applicationClass() {
        return applicationClass;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String title() {
        return title;
    }

    public String[] args() {
        return args == null ? null : args.clone();
    }

    public int launchType() {
        return launchType;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public Map<String, String> parameters() {
        return parameters;
    }

    public Path scratchpadRoot() {
        return scratchpadRoot;
    }

    public Path scratchpadPackedFile() {
        return scratchpadPackedFile;
    }

    public int[] scratchpadSizes() {
        return scratchpadSizes.clone();
    }

    public String statusBarIconDevice() {
        return statusBarIconDevice;
    }

    public IAppliType iAppliType() {
        return iAppliType;
    }

    public boolean externalFrameEnabled() {
        return externalFrameEnabled;
    }

    public boolean exitOnShutdown() {
        return exitOnShutdown;
    }

    public static final class Builder {
        private final Class<? extends IApplication> applicationClass;
        private int width = DEFAULT_VIEWPORT_WIDTH;
        private int height = DEFAULT_VIEWPORT_HEIGHT;
        private String title;
        private String[] args;
        private int launchType = IApplication.LAUNCHED_FROM_MENU;
        private String sourceUrl = "resource:///";
        private final Map<String, String> parameters = new HashMap<>();
        private Path scratchpadRoot;
        private Path scratchpadPackedFile;
        private int[] scratchpadSizes = new int[0];
        private String statusBarIconDevice = resolveDefaultStatusBarIconDevice();
        private IAppliType iAppliType = IAppliType.I_APPLI;
        private boolean externalFrameEnabled = true;
        private boolean exitOnShutdown;

        private Builder(Class<? extends IApplication> applicationClass) {
            this.applicationClass = applicationClass;
            this.title = applicationClass.getSimpleName();
        }

        public Builder viewport(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder args(String[] args) {
            this.args = args == null ? null : args.clone();
            return this;
        }

        public Builder launchType(int launchType) {
            this.launchType = launchType;
            return this;
        }

        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public Builder parameter(String key, String value) {
            this.parameters.put(key, value);
            return this;
        }

        public Builder parameters(Map<String, String> parameters) {
            this.parameters.clear();
            this.parameters.putAll(parameters);
            return this;
        }

        public Builder scratchpadRoot(Path scratchpadRoot) {
            this.scratchpadRoot = scratchpadRoot;
            return this;
        }

        public Builder scratchpadPackedFile(Path scratchpadPackedFile) {
            this.scratchpadPackedFile = scratchpadPackedFile;
            return this;
        }

        public Builder scratchpadSizes(int[] scratchpadSizes) {
            this.scratchpadSizes = scratchpadSizes == null ? new int[0] : scratchpadSizes.clone();
            return this;
        }

        public Builder statusBarIconDevice(String statusBarIconDevice) {
            if (statusBarIconDevice == null || statusBarIconDevice.isBlank()) {
                this.statusBarIconDevice = DEFAULT_STATUS_BAR_ICON_DEVICE;
                return this;
            }
            this.statusBarIconDevice = statusBarIconDevice.trim();
            return this;
        }

        public Builder iAppliType(IAppliType iAppliType) {
            this.iAppliType = iAppliType == null ? IAppliType.I_APPLI : iAppliType;
            return this;
        }

        public Builder externalFrameEnabled(boolean externalFrameEnabled) {
            this.externalFrameEnabled = externalFrameEnabled;
            return this;
        }

        public Builder exitOnShutdown(boolean exitOnShutdown) {
            this.exitOnShutdown = exitOnShutdown;
            return this;
        }

        public LaunchConfig build() {
            return new LaunchConfig(this);
        }
    }

    private static String resolveDefaultStatusBarIconDevice() {
        String raw = OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.STATUS_BAR_ICON_DEVICE, null);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_STATUS_BAR_ICON_DEVICE;
        }
        return raw.trim();
    }

    // Nested host-config enums start here. Keep them with LaunchConfig so the
    // small configuration vocabularies stay attached to the main host launch
    // model instead of being split into separate top-level files.

    /**
     * Selects which host text renderer backs DoJa {@code com.nttdocomo.ui.Font}:
     * the extracted handset bitmap font path or the desktop system-font fallback.
     */
    public enum FontType {
        BITMAP("bitmap", "Bitmap"),
        SYSTEM("system", "System");

        public final String id;
        public final String label;

        FontType(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public static FontType fromId(String candidate) {
            if (candidate == null) {
                return null;
            }
            String normalized = candidate.trim().toLowerCase(Locale.ROOT);
            for (FontType type : values()) {
                if (type.id.equals(normalized)) {
                    return type;
                }
            }
            return null;
        }

        public static String normalizeId(String candidate) {
            FontType type = fromId(candidate);
            return type == null ? BITMAP.id : type.id;
        }

        public static FontType resolveConfigured() {
            FontType configured = fromId(OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.FONT_TYPE));
            return configured == null ? BITMAP : configured;
        }
    }

    /**
     * Distinguishes standard i-appli jars from i-appli DX launches so host UI
     * chrome and runtime behavior can follow the title's packaged capability.
     */
    public enum IAppliType {
        I_APPLI,
        I_APPLI_DX;

        static IAppliType fromJamProperties(Properties properties) {
            if (properties == null) {
                return I_APPLI;
            }
            return properties.containsKey("TrustedAPID") ? I_APPLI_DX : I_APPLI;
        }
    }
}
