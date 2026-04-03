package com.nttdocomo.fs;

import com.nttdocomo.device.StorageDevice;
import opendoja.host.storage.DoJaStorageHost;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Defines a folder.
 * Instances of this class hold the path name to a folder and perform operations
 * on that folder in the storage device at the time each method is called.
 */
public class Folder {
    private final StorageDevice storageDevice;
    private final AccessToken accessToken;
    private final String path;
    private final Path backingPath;

    /**
     * Creates a folder instance.
     *
     * @param storageDevice the source storage device
     * @param accessToken the access right
     * @param path the absolute path name
     */
    protected Folder(StorageDevice storageDevice, AccessToken accessToken, String path) {
        this(storageDevice, accessToken, path, resolveBackingPath(accessToken));
    }

    Folder(StorageDevice storageDevice, AccessToken accessToken, String path, Path backingPath) {
        this.storageDevice = storageDevice;
        this.accessToken = accessToken;
        this.path = path;
        this.backingPath = backingPath;
    }

    /**
     * Performs root.
     */
    public static Folder root(StorageDevice storageDevice, AccessToken accessToken, Path backingPath) {
        return new Folder(storageDevice, accessToken, "/", backingPath);
    }

    /**
     * Gets the source storage-device instance of this folder.
     *
     * @return the source storage-device instance of this folder
     */
    public StorageDevice getStorageDevice() {
        return storageDevice;
    }

    /**
     * Gets the access right to this folder.
     *
     * @return the access right to this folder
     */
    public AccessToken getAccessToken() {
        return accessToken;
    }

    /**
     * Gets the absolute path name of this folder.
     *
     * @return the absolute path name of this folder
     */
    public String getPath() {
        return path;
    }

    /**
     * Gets the free space of this folder in bytes.
     *
     * @return the free space of this folder in bytes
     * @throws IOException if an I/O error occurs
     */
    public long getFreeSize() throws IOException {
        return DoJaStorageHost.getUsableSpace(backingPath);
    }

    /**
     * Gets whether a file attribute is supported for files in this folder.
     *
     * @param clazz the class object representing the file attribute
     * @return {@code true} if the file attribute is supported, otherwise
     *         {@code false}
     * @throws NullPointerException if {@code clazz} is {@code null}
     * @throws IllegalArgumentException if {@code clazz} does not represent a
     *         subtype of {@link FileAttribute}
     */
    public boolean isFileAttributeSupported(Class clazz) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        if (!FileAttribute.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Not a FileAttribute subtype: " + clazz.getName());
        }
        return false;
    }

    /**
     * Creates a file.
     * This is equivalent to calling {@code createFile(fileName, null)}.
     *
     * @param fileName the name of the file to create
     * @return the created file instance
     * @throws IOException if file creation fails
     */
    public File createFile(String fileName) throws IOException {
        return createFile(fileName, null);
    }

    /**
     * Creates a file and optionally specifies file attributes.
     *
     * @param fileName the name of the file to create
     * @param attributes the file attributes to apply, or {@code null} if no
     *        file attribute is specified
     * @return the created file instance
     * @throws IOException if file creation fails
     */
    public File createFile(String fileName, FileAttribute[] attributes) throws IOException {
        DoJaStorageHost.validateFileName(fileName);
        validateAttributes(attributes);
        Path filePath = backingPath.resolve(fileName);
        try {
            DoJaStorageHost.ensureNamespaceExists(backingPath);
            Files.createFile(filePath);
        } catch (IOException exception) {
            throw DoJaStorageHost.translateCreateFailure(exception);
        }
        return File.of(this, fileName, filePath);
    }

    /**
     * Gets instances of all files contained in this folder.
     *
     * @return file instances for all files contained in this folder; if this
     *         folder contains no files, a zero-length array is returned
     * @throws IOException if an I/O error occurs
     */
    public File[] getFiles() throws IOException {
        DoJaStorageHost.ensureNamespaceExists(backingPath);
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backingPath)) {
            for (Path candidate : stream) {
                if (Files.isRegularFile(candidate)) {
                    files.add(candidate);
                }
            }
        } catch (IOException exception) {
            throw DoJaStorageHost.translateExistingPathFailure(exception);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        File[] result = new File[files.size()];
        for (int i = 0; i < files.size(); i++) {
            String fileName = files.get(i).getFileName().toString();
            result[i] = File.of(this, fileName, files.get(i));
        }
        return result;
    }

    /**
     * Gets the file instance with the specified file name.
     *
     * @param fileName the name of the file to obtain
     * @return the file instance
     * @throws IOException if the file cannot be accessed
     */
    public File getFile(String fileName) throws IOException {
        DoJaStorageHost.validateFileName(fileName);
        Path filePath = backingPath.resolve(fileName);
        if (!Files.exists(filePath)) {
            throw new FileNotAccessibleException(FileNotAccessibleException.NOT_FOUND,
                    "File not found: " + fileName);
        }
        if (!Files.isRegularFile(filePath)) {
            throw new FileNotAccessibleException(FileNotAccessibleException.ACCESS_DENIED,
                    "Path does not refer to a file: " + fileName);
        }
        return File.of(this, fileName, filePath);
    }

    Path backingPath() {
        return backingPath;
    }

    private static Path resolveBackingPath(AccessToken accessToken) {
        if (accessToken instanceof DoJaAccessToken token) {
            return DoJaStorageHost.resolveNamespaceRoot(token);
        }
        return DoJaStorageHost.deviceRoot();
    }

    private void validateAttributes(FileAttribute[] attributes) {
        if (attributes == null) {
            return;
        }
        for (FileAttribute attribute : attributes) {
            if (attribute == null) {
                throw new NullPointerException("attributes contains null");
            }
            if (!isFileAttributeSupported(attribute.getClass())) {
                throw new IllegalArgumentException("Unsupported file attribute: " + attribute.getClass().getName());
            }
        }
    }
}
