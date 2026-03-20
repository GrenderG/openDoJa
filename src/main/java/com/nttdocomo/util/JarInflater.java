package com.nttdocomo.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarInflater {
    private final Map<String, byte[]> entries = new HashMap<>();

    public JarInflater(InputStream inputStream) throws JarFormatException, IOException {
        this(readAllBytes(inputStream));
    }

    public JarInflater(byte[] data) throws JarFormatException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries.put(entry.getName(), readAllBytes(zip));
            }
        } catch (IOException e) {
            throw new JarFormatException(e.getMessage());
        }
    }

    public void close() {
        entries.clear();
    }

    public long getSize(String name) throws JarFormatException {
        byte[] data = entries.get(name);
        if (data == null) {
            throw new JarFormatException("Entry not found: " + name);
        }
        return data.length;
    }

    public InputStream getInputStream(String name) throws JarFormatException {
        byte[] data = entries.get(name);
        if (data == null) {
            throw new JarFormatException("Entry not found: " + name);
        }
        return new ByteArrayInputStream(data);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
