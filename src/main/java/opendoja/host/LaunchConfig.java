package opendoja.host;

import com.nttdocomo.ui.IApplication;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LaunchConfig {
    private final Class<? extends IApplication> applicationClass;
    private final int width;
    private final int height;
    private final String title;
    private final String[] args;
    private final int launchType;
    private final String sourceUrl;
    private final Map<String, String> parameters;
    private final Path scratchpadRoot;
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

    public boolean exitOnShutdown() {
        return exitOnShutdown;
    }

    public static final class Builder {
        private final Class<? extends IApplication> applicationClass;
        private int width = 240;
        private int height = 240;
        private String title;
        private String[] args;
        private int launchType = IApplication.LAUNCHED_FROM_MENU;
        private String sourceUrl = "resource:///";
        private final Map<String, String> parameters = new HashMap<>();
        private Path scratchpadRoot;
        private boolean exitOnShutdown;

        private Builder(Class<? extends IApplication> applicationClass) {
            this.applicationClass = applicationClass;
            this.title = applicationClass.getSimpleName();
            this.scratchpadRoot = Paths.get(".opendoja", applicationClass.getName());
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

        public Builder exitOnShutdown(boolean exitOnShutdown) {
            this.exitOnShutdown = exitOnShutdown;
            return this;
        }

        public LaunchConfig build() {
            return new LaunchConfig(this);
        }
    }
}
