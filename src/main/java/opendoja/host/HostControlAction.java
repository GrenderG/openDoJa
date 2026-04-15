package opendoja.host;

import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;

import java.awt.event.KeyEvent;
import java.util.List;

public enum HostControlAction {
    DIGIT_0("digit0", "Digit 0", DispatchKind.DOJA_KEY, Display.KEY_0,
            HostInputBinding.keyboard(KeyEvent.VK_0),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD0)),
    DIGIT_1("digit1", "Digit 1", DispatchKind.DOJA_KEY, Display.KEY_1,
            HostInputBinding.keyboard(KeyEvent.VK_1),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD1)),
    DIGIT_2("digit2", "Digit 2", DispatchKind.DOJA_KEY, Display.KEY_2,
            HostInputBinding.keyboard(KeyEvent.VK_2),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD2)),
    DIGIT_3("digit3", "Digit 3", DispatchKind.DOJA_KEY, Display.KEY_3,
            HostInputBinding.keyboard(KeyEvent.VK_3),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD3)),
    DIGIT_4("digit4", "Digit 4", DispatchKind.DOJA_KEY, Display.KEY_4,
            HostInputBinding.keyboard(KeyEvent.VK_4),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD4)),
    DIGIT_5("digit5", "Digit 5", DispatchKind.DOJA_KEY, Display.KEY_5,
            HostInputBinding.keyboard(KeyEvent.VK_5),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD5)),
    DIGIT_6("digit6", "Digit 6", DispatchKind.DOJA_KEY, Display.KEY_6,
            HostInputBinding.keyboard(KeyEvent.VK_6),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD6)),
    DIGIT_7("digit7", "Digit 7", DispatchKind.DOJA_KEY, Display.KEY_7,
            HostInputBinding.keyboard(KeyEvent.VK_7),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD7)),
    DIGIT_8("digit8", "Digit 8", DispatchKind.DOJA_KEY, Display.KEY_8,
            HostInputBinding.keyboard(KeyEvent.VK_8),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD8)),
    DIGIT_9("digit9", "Digit 9", DispatchKind.DOJA_KEY, Display.KEY_9,
            HostInputBinding.keyboard(KeyEvent.VK_9),
            HostInputBinding.keyboard(KeyEvent.VK_NUMPAD9)),
    ASTERISK("asterisk", "Asterisk", DispatchKind.DOJA_KEY, Display.KEY_ASTERISK,
            HostInputBinding.keyboard(KeyEvent.VK_ASTERISK),
            HostInputBinding.keyboard(KeyEvent.VK_MULTIPLY)),
    POUND("pound", "Pound", DispatchKind.DOJA_KEY, Display.KEY_POUND,
            HostInputBinding.keyboard(KeyEvent.VK_NUMBER_SIGN),
            HostInputBinding.keyboard(KeyEvent.VK_DIVIDE)),
    MOVE_LEFT("moveLeft", "Move left", DispatchKind.DOJA_KEY, Display.KEY_LEFT,
            HostInputBinding.keyboard(KeyEvent.VK_LEFT)),
    MOVE_UP("moveUp", "Move up", DispatchKind.DOJA_KEY, Display.KEY_UP,
            HostInputBinding.keyboard(KeyEvent.VK_UP)),
    MOVE_RIGHT("moveRight", "Move right", DispatchKind.DOJA_KEY, Display.KEY_RIGHT,
            HostInputBinding.keyboard(KeyEvent.VK_RIGHT)),
    MOVE_DOWN("moveDown", "Move down", DispatchKind.DOJA_KEY, Display.KEY_DOWN,
            HostInputBinding.keyboard(KeyEvent.VK_DOWN)),
    SELECT("select", "Select", DispatchKind.DOJA_KEY, Display.KEY_SELECT,
            HostInputBinding.keyboard(KeyEvent.VK_ENTER),
            HostInputBinding.keyboard(KeyEvent.VK_SPACE)),
    CLEAR("clear", "Clear", DispatchKind.DOJA_KEY, Display.KEY_CLEAR,
            HostInputBinding.keyboard(KeyEvent.VK_ESCAPE),
            HostInputBinding.keyboard(KeyEvent.VK_BACK_SPACE)),
    SOFT_KEY_LEFT("softKeyLeft", "Soft key left", DispatchKind.HOST_SOFT_KEY, Frame.SOFT_KEY_1,
            HostInputBinding.keyboard(KeyEvent.VK_F1),
            HostInputBinding.keyboard(KeyEvent.VK_A)),
    SOFT_KEY_RIGHT("softKeyRight", "Soft key right", DispatchKind.HOST_SOFT_KEY, Frame.SOFT_KEY_2,
            HostInputBinding.keyboard(KeyEvent.VK_F2),
            HostInputBinding.keyboard(KeyEvent.VK_S)),
    MENU("menu", "Menu", DispatchKind.DOJA_KEY, Display.KEY_MENU,
            HostInputBinding.keyboard(KeyEvent.VK_M)),
    CAMERA("camera", "Camera", DispatchKind.DOJA_KEY, Display.KEY_CAMERA,
            HostInputBinding.keyboard(KeyEvent.VK_C));

    private final String id;
    private final String displayName;
    private final DispatchKind dispatchKind;
    private final int dispatchCode;
    private final List<HostInputBinding> defaultBindings;

    HostControlAction(String id, String displayName, DispatchKind dispatchKind, int dispatchCode,
                      HostInputBinding... defaultBindings) {
        this.id = id;
        this.displayName = displayName;
        this.dispatchKind = dispatchKind;
        this.dispatchCode = dispatchCode;
        this.defaultBindings = List.of(defaultBindings);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public DispatchKind dispatchKind() {
        return dispatchKind;
    }

    public int dispatchCode() {
        return dispatchCode;
    }

    public List<HostInputBinding> defaultBindings() {
        return defaultBindings;
    }

    public static HostControlAction fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim();
        for (HostControlAction action : values()) {
            if (action.id.equalsIgnoreCase(normalized)) {
                return action;
            }
        }
        return null;
    }

    public enum DispatchKind {
        DOJA_KEY,
        HOST_SOFT_KEY
    }
}
