package opendoja.host.input;

import java.util.List;

public interface ControllerInputListener {
    default void onDevicesChanged(List<ControllerDeviceInfo> devices) {
    }

    default void onInput(ControllerInputEvent event) {
    }
}
