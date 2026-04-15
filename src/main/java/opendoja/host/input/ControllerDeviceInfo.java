package opendoja.host.input;

public record ControllerDeviceInfo(String id, String name, String productName) {
    public ControllerDeviceInfo {
        id = normalize(id);
        name = normalize(name);
        productName = normalize(productName);
    }

    public String displayName() {
        if (!productName.isBlank() && !productName.equals(name)) {
            return productName + " (" + name + ")";
        }
        return !name.isBlank() ? name : (!productName.isBlank() ? productName : "Controller");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
