package opendoja.host;

import opendoja.host.input.ControllerBindingDescriptor;

import java.awt.event.KeyEvent;
import java.util.List;

public final class HostKeybindProfileProbe {
    private HostKeybindProfileProbe() {
    }

    public static void main(String[] args) {
        verifyDefaultsPreserveLegacyBindings();
        verifySerializationRoundTrip();
        verifyControllerBindingRoundTrip();
        verifyLaunchPropertyOverride();
        verifyConfigurationNamingAndDeletionFallback();
        System.out.println("Host keybind profile probe OK");
    }

    private static void verifyDefaultsPreserveLegacyBindings() {
        HostKeybindProfile defaults = HostKeybindProfile.defaults();
        check(defaults.keyboardActionsByKeyCode().get(KeyEvent.VK_ENTER) == HostControlAction.SELECT,
                "Enter should map to Select by default");
        check(defaults.keyboardActionsByKeyCode().get(KeyEvent.VK_A) == HostControlAction.SOFT_KEY_LEFT,
                "A should map to left soft key by default");
        check(defaults.keyboardActionsByKeyCode().get(KeyEvent.VK_S) == HostControlAction.SOFT_KEY_RIGHT,
                "S should map to right soft key by default");
        check(defaults.keyboardActionsByKeyCode().get(KeyEvent.VK_NUMPAD7) == HostControlAction.DIGIT_7,
                "NumPad 7 should map to Digit 7 by default");
    }

    private static void verifySerializationRoundTrip() {
        HostKeybindProfile updated = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.MOVE_LEFT, 1, HostInputBinding.keyboard(KeyEvent.VK_Z))
                .withoutBinding(HostControlAction.SOFT_KEY_LEFT, 1);
        HostKeybindProfile roundTripped = HostKeybindProfile.deserialize(updated.serialize());
        check(updated.equals(roundTripped),
                "serialized keybind profile should round-trip: original=" + updated + " roundTripped=" + roundTripped);
        check(roundTripped.bindingAt(HostControlAction.MOVE_LEFT, 1).equals(HostInputBinding.keyboard(KeyEvent.VK_Z)),
                "custom binding should survive round-trip");
    }

    private static void verifyLaunchPropertyOverride() {
        HostKeybindProfile customProfile = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.SELECT, 0, HostInputBinding.keyboard(KeyEvent.VK_Z))
                .withoutBinding(HostControlAction.SELECT, 1);
        String previous = System.getProperty(OpenDoJaLaunchArgs.INPUT_BINDINGS);
        try {
            System.setProperty(OpenDoJaLaunchArgs.INPUT_BINDINGS, customProfile.serialize());
            HostKeybindProfile loaded = HostKeybindProfile.fromLaunchArgs();
            check(loaded.equals(customProfile), "launch property should load the serialized active profile");
        } finally {
            if (previous == null) {
                System.clearProperty(OpenDoJaLaunchArgs.INPUT_BINDINGS);
            } else {
                System.setProperty(OpenDoJaLaunchArgs.INPUT_BINDINGS, previous);
            }
        }
    }

    private static void verifyControllerBindingRoundTrip() {
        HostInputBinding controllerBinding = HostInputBinding.controller(
                "",
                ControllerBindingDescriptor.axis("LEFT_THUMB_X", ControllerBindingDescriptor.Direction.NEGATIVE));
        HostKeybindProfile updated = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.MOVE_LEFT, 0, controllerBinding)
                .withoutBinding(HostControlAction.MOVE_LEFT, 1);
        HostKeybindProfile roundTripped = HostKeybindProfile.deserialize(updated.serialize());
        check(roundTripped != null, "controller binding profile should deserialize");
        check(controllerBinding.equals(roundTripped.bindingAt(HostControlAction.MOVE_LEFT, 0)),
                "controller binding should survive round-trip");
        check(roundTripped.controllerActionsByBinding().get(controllerBinding) == HostControlAction.MOVE_LEFT,
                "controller binding lookup should resolve the owning action");

        HostInputBinding migratedStandardTriggerBinding = HostInputBinding.parse("controller::axis.LEFT_AXIS_Z.neg");
        check(migratedStandardTriggerBinding != null, "standard input4j trigger-axis binding should still parse");
        check("button.LEFT_TRIGGER".equals(migratedStandardTriggerBinding.controlId()),
                "standard input4j trigger-axis binding should canonicalize to a trigger control");
        HostInputBinding rightThumbBinding = HostInputBinding.parse("controller::axis.RIGHT_THUMB_X.neg");
        check(rightThumbBinding != null, "right stick X binding should still parse");
        check("axis.RIGHT_THUMB_X.neg".equals(rightThumbBinding.controlId()),
                "right stick X binding must not be misclassified as a trigger");
    }

    private static void verifyConfigurationNamingAndDeletionFallback() {
        HostKeybindProfile customProfile = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.MENU, 0, HostInputBinding.keyboard(KeyEvent.VK_F3));
        HostKeybindConfiguration configuration = new HostKeybindConfiguration(
                List.of(HostKeybindProfile.defaults(), customProfile),
                List.of("ignored", "Arcade"),
                1);
        check(HostKeybindConfiguration.DEFAULT_PROFILE_NAME.equals(configuration.profileLabel(0)),
                "profile 1 should always be named Default");
        check("Arcade".equals(configuration.profileLabel(1)),
                "custom profile names should be preserved");
        HostKeybindConfiguration afterDeletion = configuration.deleteProfile(1);
        check(afterDeletion.profiles().size() == 1, "active profile deletion should keep one remaining profile");
        check(afterDeletion.activeProfileIndex() == 0, "active profile deletion should fall back to profile 1");
        check(!afterDeletion.canDeleteProfile(0),
                "Default profile should never be deletable");
        HostKeybindConfiguration added = afterDeletion.addProfile("Custom");
        check("Custom".equals(added.profileLabel(1)),
                "new profile should keep the requested name");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
