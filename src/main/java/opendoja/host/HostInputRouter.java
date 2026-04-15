package opendoja.host;

import opendoja.host.input.ControllerInputEvent;

import java.util.Map;

public final class HostInputRouter {
    private final Map<Integer, HostControlAction> keyboardActions;
    private final Map<HostInputBinding, HostControlAction> controllerActions;

    public HostInputRouter(HostKeybindProfile profile) {
        HostKeybindProfile resolved = profile == null ? HostKeybindProfile.defaults() : profile;
        this.keyboardActions = resolved.keyboardActionsByKeyCode();
        this.controllerActions = resolved.controllerActionsByBinding();
    }

    public HostControlAction keyboardAction(int awtKeyCode) {
        return keyboardActions.get(awtKeyCode);
    }

    public HostControlAction controllerAction(ControllerInputEvent event) {
        if (event == null) {
            return null;
        }
        HostControlAction action = controllerActions.get(event.deviceBinding());
        if (action != null) {
            return action;
        }
        return controllerActions.get(event.genericBinding());
    }
}
