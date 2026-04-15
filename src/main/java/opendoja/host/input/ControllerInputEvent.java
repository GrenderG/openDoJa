package opendoja.host.input;

import opendoja.host.HostInputBinding;

public record ControllerInputEvent(
        ControllerDeviceInfo device,
        ControllerBindingDescriptor bindingDescriptor,
        float value,
        boolean active,
        long timestampNanos) {
    public ControllerInputEvent {
        device = device == null ? new ControllerDeviceInfo("", "", "") : device;
        bindingDescriptor = bindingDescriptor == null
                ? ControllerBindingDescriptor.button("BUTTON_0")
                : bindingDescriptor;
        value = Float.isFinite(value) ? value : 0f;
        timestampNanos = Math.max(0L, timestampNanos);
    }

    public HostInputBinding genericBinding() {
        return HostInputBinding.controller("", bindingDescriptor);
    }

    public HostInputBinding deviceBinding() {
        return HostInputBinding.controller(device.id(), bindingDescriptor);
    }

    public String displayLabel() {
        return bindingDescriptor.displayLabel();
    }
}
