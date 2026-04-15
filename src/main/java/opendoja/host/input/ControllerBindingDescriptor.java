package opendoja.host.input;

import java.util.Locale;
import java.util.Objects;

public record ControllerBindingDescriptor(Kind kind, String componentId, Direction direction) {
    private static final String BUTTON_PREFIX = "button.";
    private static final String AXIS_PREFIX = "axis.";
    private static final String POV_PREFIX = "pov.";

    public ControllerBindingDescriptor {
        kind = kind == null ? Kind.BUTTON : kind;
        componentId = normalizeToken(componentId, kind != Kind.POV);
        direction = normalizeDirection(kind, direction);
    }

    public static ControllerBindingDescriptor button(String componentId) {
        return new ControllerBindingDescriptor(Kind.BUTTON, componentId, null);
    }

    public static ControllerBindingDescriptor axis(String componentId, Direction direction) {
        return new ControllerBindingDescriptor(Kind.AXIS, componentId, direction);
    }

    public static ControllerBindingDescriptor pov(Direction direction) {
        return new ControllerBindingDescriptor(Kind.POV, "", direction);
    }

    public static ControllerBindingDescriptor parse(String controlId) {
        if (controlId == null || controlId.isBlank()) {
            return null;
        }
        if (controlId.startsWith(BUTTON_PREFIX)) {
            String componentId = controlId.substring(BUTTON_PREFIX.length()).trim();
            return componentId.isEmpty() ? null : button(componentId);
        }
        if (controlId.startsWith(AXIS_PREFIX)) {
            String remainder = controlId.substring(AXIS_PREFIX.length()).trim();
            int separatorIndex = remainder.lastIndexOf('.');
            if (separatorIndex <= 0 || separatorIndex >= remainder.length() - 1) {
                return null;
            }
            String componentId = remainder.substring(0, separatorIndex);
            String triggerButtonId = triggerButtonId(componentId);
            if (triggerButtonId != null) {
                return button(triggerButtonId);
            }
            Direction direction = Direction.fromToken(remainder.substring(separatorIndex + 1));
            if (direction == null || !direction.isAxisDirection()) {
                return null;
            }
            return axis(componentId, direction);
        }
        if (controlId.startsWith(POV_PREFIX)) {
            Direction direction = Direction.fromToken(controlId.substring(POV_PREFIX.length()));
            if (direction == null || !direction.isPovDirection()) {
                return null;
            }
            return pov(direction);
        }
        return null;
    }

    public String controlId() {
        return switch (kind) {
            case BUTTON -> BUTTON_PREFIX + componentId;
            case AXIS -> AXIS_PREFIX + componentId + "." + direction.token();
            case POV -> POV_PREFIX + direction.token();
        };
    }

    public String displayLabel() {
        return switch (kind) {
            case BUTTON -> prettyButtonLabel(componentId);
            case AXIS -> prettyAxisLabel(componentId, direction);
            case POV -> "D-pad " + direction.displayName();
        };
    }

    public static String triggerButtonId(String componentId) {
        String normalized = normalizeToken(componentId, false);
        if (normalized.isEmpty()) {
            return null;
        }
        return switch (normalized) {
            case "LEFT_TRIGGER", "LEFT_AXIS_Z" -> "LEFT_TRIGGER";
            case "RIGHT_TRIGGER", "RIGHT_AXIS_Z" -> "RIGHT_TRIGGER";
            default -> null;
        };
    }

    private static Direction normalizeDirection(Kind kind, Direction direction) {
        return switch (kind) {
            case BUTTON -> null;
            case AXIS -> {
                if (direction == null || !direction.isAxisDirection()) {
                    throw new IllegalArgumentException("Axis binding requires a signed direction");
                }
                yield direction;
            }
            case POV -> {
                if (direction == null || !direction.isPovDirection()) {
                    throw new IllegalArgumentException("POV binding requires a D-pad direction");
                }
                yield direction;
            }
        };
    }

    private static String normalizeToken(String value, boolean required) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (required && normalized.isEmpty()) {
            throw new IllegalArgumentException("Controller component id must not be blank");
        }
        if (normalized.indexOf(':') >= 0
                || normalized.indexOf(',') >= 0
                || normalized.indexOf(';') >= 0
                || normalized.indexOf('=') >= 0) {
            throw new IllegalArgumentException("Controller component id contains a reserved separator: " + normalized);
        }
        return normalized;
    }

    private static String prettyButtonLabel(String componentId) {
        String normalized = normalizeToken(componentId, true);
        return switch (normalized) {
            case "A", "B", "X", "Y", "BACK", "START" -> normalized;
            case "LEFT_SHOULDER" -> "Left shoulder";
            case "RIGHT_SHOULDER" -> "Right shoulder";
            case "LEFT_TRIGGER" -> "Left trigger";
            case "RIGHT_TRIGGER" -> "Right trigger";
            case "LEFT_THUMB" -> "Left stick press";
            case "RIGHT_THUMB" -> "Right stick press";
            case "DPAD_UP" -> "D-pad Up";
            case "DPAD_RIGHT" -> "D-pad Right";
            case "DPAD_DOWN" -> "D-pad Down";
            case "DPAD_LEFT" -> "D-pad Left";
            default -> humanize(normalized);
        };
    }

    private static String prettyAxisLabel(String componentId, Direction direction) {
        String normalized = normalizeToken(componentId, true);
        if (normalized.endsWith("_X")) {
            return axisBaseLabel(normalized) + " " + (direction == Direction.NEGATIVE ? "Left" : "Right");
        }
        if (normalized.endsWith("_Y")) {
            return axisBaseLabel(normalized) + " " + (direction == Direction.NEGATIVE ? "Up" : "Down");
        }
        if (normalized.contains("TRIGGER")) {
            if (direction == Direction.POSITIVE) {
                return prettyButtonLabel(normalized);
            }
            return prettyButtonLabel(normalized) + " -";
        }
        return axisBaseLabel(normalized) + " " + (direction == Direction.NEGATIVE ? "-" : "+");
    }

    private static String axisBaseLabel(String componentId) {
        return switch (componentId) {
            case "LEFT_AXIS_X", "LEFT_AXIS_Y", "LEFT_THUMB_X", "LEFT_THUMB_Y", "AXIS_X", "AXIS_Y" -> "Left stick";
            case "RIGHT_AXIS_X", "RIGHT_AXIS_Y", "RIGHT_THUMB_X", "RIGHT_THUMB_Y", "AXIS_RX", "AXIS_RY" -> "Right stick";
            case "LEFT_TRIGGER", "LEFT_AXIS_Z" -> "Left trigger";
            case "RIGHT_TRIGGER", "RIGHT_AXIS_Z" -> "Right trigger";
            default -> humanize(componentId);
        };
    }

    private static String humanize(String token) {
        StringBuilder builder = new StringBuilder(token.length());
        boolean upper = true;
        for (int i = 0; i < token.length(); i++) {
            char current = token.charAt(i);
            if (current == '_' || current == '-' || current == '.') {
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(current) : Character.toLowerCase(current));
            upper = false;
        }
        return builder.toString().trim();
    }

    public enum Kind {
        BUTTON,
        AXIS,
        POV
    }

    public enum Direction {
        NEGATIVE("neg", "Negative"),
        POSITIVE("pos", "Positive"),
        UP("up", "Up"),
        RIGHT("right", "Right"),
        DOWN("down", "Down"),
        LEFT("left", "Left");

        private final String token;
        private final String displayName;

        Direction(String token, String displayName) {
            this.token = token;
            this.displayName = displayName;
        }

        public String token() {
            return token;
        }

        public String displayName() {
            return displayName;
        }

        public boolean isAxisDirection() {
            return this == NEGATIVE || this == POSITIVE;
        }

        public boolean isPovDirection() {
            return this == UP || this == RIGHT || this == DOWN || this == LEFT;
        }

        static Direction fromToken(String token) {
            if (token == null) {
                return null;
            }
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            for (Direction value : values()) {
                if (Objects.equals(value.token, normalized)) {
                    return value;
                }
            }
            return null;
        }
    }
}
