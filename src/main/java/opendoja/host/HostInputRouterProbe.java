package opendoja.host;

import opendoja.host.input.ControllerBindingDescriptor;
import opendoja.host.input.ControllerDeviceInfo;
import opendoja.host.input.ControllerInputEvent;

import java.awt.event.KeyEvent;

public final class HostInputRouterProbe {
    private HostInputRouterProbe() {
    }

    public static void main(String[] args) {
        verifyKeyboardRouting();
        verifyGenericControllerRouting();
        verifyDeviceSpecificControllerRouting();
        System.out.println("Host input router probe OK");
    }

    private static void verifyKeyboardRouting() {
        HostInputRouter router = new HostInputRouter(HostKeybindProfile.defaults());
        check(router.keyboardAction(KeyEvent.VK_ENTER) == HostControlAction.SELECT,
                "default keyboard routing should keep Select on Enter");
    }

    private static void verifyGenericControllerRouting() {
        HostInputBinding controllerBinding = HostInputBinding.controller(
                "",
                ControllerBindingDescriptor.button("A"));
        HostKeybindProfile profile = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.SELECT, 0, controllerBinding)
                .withoutBinding(HostControlAction.SELECT, 1);
        HostInputRouter router = new HostInputRouter(profile);
        ControllerInputEvent event = new ControllerInputEvent(
                new ControllerDeviceInfo("PAD.ONE", "Pad One", "Pad One"),
                ControllerBindingDescriptor.button("A"),
                1f,
                true,
                System.nanoTime());
        check(router.controllerAction(event) == HostControlAction.SELECT,
                "generic controller binding should resolve for matching control input");
    }

    private static void verifyDeviceSpecificControllerRouting() {
        HostInputBinding controllerBinding = HostInputBinding.controller(
                "PAD.TWO",
                ControllerBindingDescriptor.pov(ControllerBindingDescriptor.Direction.LEFT));
        HostKeybindProfile profile = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.MOVE_LEFT, 0, controllerBinding)
                .withoutBinding(HostControlAction.MOVE_LEFT, 1);
        HostInputRouter router = new HostInputRouter(profile);

        ControllerInputEvent matchedEvent = new ControllerInputEvent(
                new ControllerDeviceInfo("PAD.TWO", "Pad Two", "Pad Two"),
                ControllerBindingDescriptor.pov(ControllerBindingDescriptor.Direction.LEFT),
                1f,
                true,
                System.nanoTime());
        ControllerInputEvent mismatchedEvent = new ControllerInputEvent(
                new ControllerDeviceInfo("PAD.THREE", "Pad Three", "Pad Three"),
                ControllerBindingDescriptor.pov(ControllerBindingDescriptor.Direction.LEFT),
                1f,
                true,
                System.nanoTime());

        check(router.controllerAction(matchedEvent) == HostControlAction.MOVE_LEFT,
                "device-specific controller binding should resolve for the configured controller");
        check(router.controllerAction(mismatchedEvent) == null,
                "device-specific controller binding should not resolve for a different controller");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
