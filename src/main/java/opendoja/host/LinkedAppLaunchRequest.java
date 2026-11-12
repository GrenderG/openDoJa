package opendoja.host;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Transfers linked-launch strings without command-line quoting or encoding loss. */
public record LinkedAppLaunchRequest(Path jamPath, Map<String, String> parameters) {
    private static final int MAGIC = 0x4f444c31; // ODL1

    public LinkedAppLaunchRequest {
        jamPath = jamPath.toAbsolutePath().normalize();
        parameters = Map.copyOf(parameters);
    }

    public Path write() throws IOException {
        Path file = Files.createTempFile("opendoja-linked-launch-", ".bin");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.writeInt(MAGIC);
            output.writeUTF(jamPath.toString());
            output.writeInt(parameters.size());
            for (var entry : parameters.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeUTF(entry.getValue());
            }
        } catch (IOException exception) {
            Files.deleteIfExists(file);
            throw exception;
        }
        return file;
    }

    public static LinkedAppLaunchRequest read(Path file, Path expectedJam) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid linked-launch request");
            }
            Path jam = Path.of(input.readUTF()).toAbsolutePath().normalize();
            if (!jam.equals(expectedJam.toAbsolutePath().normalize())) {
                throw new IOException("Linked-launch request belongs to a different JAM");
            }
            int count = input.readInt();
            if (count < 0 || count > 16) {
                throw new IOException("Invalid linked-launch parameter count: " + count);
            }
            Map<String, String> parameters = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                parameters.put(input.readUTF(), input.readUTF());
            }
            return new LinkedAppLaunchRequest(jam, parameters);
        }
    }
}
