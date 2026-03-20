package opendoja.demo;

import com.nttdocomo.ui.graphics3d.Object3D;

import java.io.IOException;
import java.io.InputStream;

public final class AssetProbe {
    private AssetProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: AssetProbe <resource-name> [resource-name...]");
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String name : args) {
            probe(loader, name);
        }
    }

    private static void probe(ClassLoader loader, String name) throws IOException {
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                System.out.println(name + " -> missing");
                return;
            }
            byte[] data = in.readAllBytes();
            String kind = identify(data);
            if ("Object3D".equals(kind)) {
                Object3D object = Object3D.createInstance(data);
                System.out.println(name + " -> " + (object == null ? "null" : object.getClass().getName()));
                return;
            }
            System.out.println(name + " -> " + kind + " (" + data.length + " bytes)");
        }
    }

    private static String identify(byte[] data) {
        if (data.length >= 2 && data[0] == 'M' && (data[1] == 'B' || data[1] == 'T')) {
            return "Object3D";
        }
        if (data.length >= 2 && data[0] == 'B' && data[1] == 'M') {
            return "Object3D";
        }
        if (data.length >= 4 && data[0] == 'm' && data[1] == 'e' && data[2] == 'l' && data[3] == 'o') {
            return "MLD";
        }
        return "binary";
    }
}
