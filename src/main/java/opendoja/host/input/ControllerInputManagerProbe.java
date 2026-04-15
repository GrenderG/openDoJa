package opendoja.host.input;

public final class ControllerInputManagerProbe {
    private ControllerInputManagerProbe() {
    }

    public static void main(String[] args) {
        verifyBidirectionalAxesStayBidirectional();
        verifyNegativeRestTriggerAxesNormalizeFromRestToPress();
        verifyZeroRestTriggerAxesNormalizeFromRestToPress();
        verifyCanonicalTriggerAxesStayOneSided();
        verifyGenericZAxesAreNotTreatedAsTriggers();
        System.out.println("Controller input manager probe OK");
    }

    private static void verifyBidirectionalAxesStayBidirectional() {
        check(ControllerInputManager.resolveAxisMode("LEFT_THUMB_X", 0f, null)
                        == ControllerInputManager.AxisMode.BIDIRECTIONAL,
                "left stick X should remain bidirectional");
        float normalized = ControllerInputManager.normalizeBidirectionalAxis(-0.75f);
        check(normalized < -0.5f, "negative bidirectional axis should stay negative");
    }

    private static void verifyNegativeRestTriggerAxesNormalizeFromRestToPress() {
        check(ControllerInputManager.resolveAxisMode("LEFT_TRIGGER", -1f, null)
                        == ControllerInputManager.AxisMode.ONE_SIDED_NEGATIVE_REST,
                "negative-rest trigger axes should be classified correctly");
        check(ControllerInputManager.normalizeOneSidedAxis(-1f,
                        ControllerInputManager.AxisMode.ONE_SIDED_NEGATIVE_REST) == 0f,
                "negative-rest trigger idle should normalize to zero");
        check(ControllerInputManager.normalizeOneSidedAxis(1f,
                        ControllerInputManager.AxisMode.ONE_SIDED_NEGATIVE_REST) > 0.95f,
                "negative-rest trigger full press should normalize near one");
    }

    private static void verifyZeroRestTriggerAxesNormalizeFromRestToPress() {
        check(ControllerInputManager.resolveAxisMode("LEFT_TRIGGER", 0f, null)
                        == ControllerInputManager.AxisMode.ONE_SIDED_ZERO_REST,
                "zero-rest trigger axes should be classified correctly");
        check(ControllerInputManager.normalizeOneSidedAxis(0f,
                        ControllerInputManager.AxisMode.ONE_SIDED_ZERO_REST) == 0f,
                "zero-rest trigger idle should normalize to zero");
        check(ControllerInputManager.normalizeOneSidedAxis(1f,
                        ControllerInputManager.AxisMode.ONE_SIDED_ZERO_REST) > 0.95f,
                "zero-rest trigger full press should normalize near one");
    }

    private static void verifyCanonicalTriggerAxesStayOneSided() {
        check(ControllerInputManager.resolveAxisMode("LEFT_AXIS_Z", -1f, null)
                        == ControllerInputManager.AxisMode.ONE_SIDED_NEGATIVE_REST,
                "left trigger Z axis should be treated as one-sided");
        check(ControllerInputManager.resolveAxisMode("LEFT_AXIS_Z", 0f,
                        ControllerInputManager.AxisMode.ONE_SIDED_NEGATIVE_REST)
                        == ControllerInputManager.AxisMode.ONE_SIDED_NEGATIVE_REST,
                "one-sided axis mode should stay stable after initialization");
    }

    private static void verifyGenericZAxesAreNotTreatedAsTriggers() {
        check(ControllerBindingDescriptor.triggerButtonId("AXIS_Z") == null,
                "generic AXIS_Z should not be treated as a live trigger axis");
        check(ControllerInputManager.resolveAxisMode("AXIS_Z", 0f, null)
                        == ControllerInputManager.AxisMode.ONE_SIDED_ZERO_REST,
                "unmapped generic AXIS_Z should fall back to raw-value heuristics");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
