package opendoja.host;

public enum OpenGlesRendererMode {
    SOFTWARE("software", "Software"),
    HARDWARE("hardware", "Hardware");

    private final String id;
    private final String label;

    OpenGlesRendererMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static OpenGlesRendererMode fromId(String candidate) {
        if (candidate != null) {
            for (OpenGlesRendererMode mode : values()) {
                if (mode.id.equalsIgnoreCase(candidate.trim())) {
                    return mode;
                }
            }
        }
        return SOFTWARE;
    }
}
