package opendoja.host;

import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * Generic host-side input binding descriptor. The binding model already distinguishes the
 * input source type and source id so controller providers can plug into the same profile
 * structure later without redesigning launcher persistence.
 */
public record HostInputBinding(SourceType sourceType, String sourceId, String controlId) {
    public HostInputBinding {
        sourceType = sourceType == null ? SourceType.KEYBOARD : sourceType;
        sourceId = normalizeToken(sourceId, true);
        controlId = normalizeToken(controlId, false);
    }

    public static HostInputBinding keyboard(int awtKeyCode) {
        if (!isBindableKeyboardKeyCode(awtKeyCode)) {
            throw new IllegalArgumentException("Unsupported keyboard key code: " + awtKeyCode);
        }
        return new HostInputBinding(SourceType.KEYBOARD, "", Integer.toString(awtKeyCode));
    }

    public static HostInputBinding parse(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }
        String[] parts = serialized.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        SourceType sourceType = SourceType.fromId(parts[0]);
        if (sourceType == null) {
            return null;
        }
        try {
            HostInputBinding binding = new HostInputBinding(sourceType, parts[1], parts[2]);
            Integer keyboardKeyCode = binding.keyboardKeyCode();
            if (sourceType == SourceType.KEYBOARD && (keyboardKeyCode == null || !isBindableKeyboardKeyCode(keyboardKeyCode))) {
                return null;
            }
            return binding;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean isKeyboard() {
        return sourceType == SourceType.KEYBOARD;
    }

    public Integer keyboardKeyCode() {
        if (!isKeyboard()) {
            return null;
        }
        try {
            return Integer.parseInt(controlId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public String displayLabel() {
        if (isKeyboard()) {
            Integer keyCode = keyboardKeyCode();
            return keyCode == null ? "Unknown key" : KeyEvent.getKeyText(keyCode);
        }
        if (sourceId.isBlank()) {
            return sourceType.displayName() + " " + controlId;
        }
        return sourceType.displayName() + " " + sourceId + " " + controlId;
    }

    public String serialize() {
        return sourceType.id() + ":" + sourceId + ":" + controlId;
    }

    public static boolean isBindableKeyboardKeyCode(int awtKeyCode) {
        return switch (awtKeyCode) {
            case KeyEvent.VK_UNDEFINED,
                    KeyEvent.VK_SHIFT,
                    KeyEvent.VK_CONTROL,
                    KeyEvent.VK_ALT,
                    KeyEvent.VK_ALT_GRAPH,
                    KeyEvent.VK_META,
                    KeyEvent.VK_WINDOWS,
                    KeyEvent.VK_CAPS_LOCK,
                    KeyEvent.VK_NUM_LOCK,
                    KeyEvent.VK_SCROLL_LOCK -> false;
            default -> awtKeyCode > 0;
        };
    }

    private static String normalizeToken(String value, boolean allowBlank) {
        String normalized = value == null ? "" : value.trim();
        if (!allowBlank && normalized.isBlank()) {
            throw new IllegalArgumentException("Binding token must not be blank");
        }
        if (normalized.indexOf(':') >= 0
                || normalized.indexOf(',') >= 0
                || normalized.indexOf(';') >= 0
                || normalized.indexOf('=') >= 0) {
            throw new IllegalArgumentException("Binding token contains a reserved separator: " + normalized);
        }
        return normalized;
    }

    public enum SourceType {
        KEYBOARD("keyboard", "Keyboard"),
        CONTROLLER("controller", "Controller");

        private final String id;
        private final String displayName;

        SourceType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        static SourceType fromId(String id) {
            if (id == null) {
                return null;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (SourceType value : values()) {
                if (value.id.equals(normalized)) {
                    return value;
                }
            }
            return null;
        }
    }
}
