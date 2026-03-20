package opendoja.host;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ScratchpadStreams {
    private ScratchpadStreams() {
    }

    public static InputStream openInput(Path path, long offset) throws IOException {
        return openInput(path, offset, -1L);
    }

    public static InputStream openInput(Path path, long offset, long length) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
        file.seek(Math.max(0L, offset));
        InputStream stream = new RandomAccessFileInputStream(file);
        if (length >= 0L) {
            stream = new BoundedInputStream(stream, length);
        }
        return new FilterInputStream(stream) {
            @Override
            public void close() throws IOException {
                super.close();
                file.close();
            }
        };
    }

    public static OutputStream openOutput(Path path, long offset) throws IOException {
        return openOutput(path, offset, -1L);
    }

    public static OutputStream openOutput(Path path, long offset, long length) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
        file.seek(Math.max(0L, offset));
        OutputStream stream = new RandomAccessFileOutputStream(file);
        if (length >= 0L) {
            stream = new BoundedOutputStream(stream, length);
        }
        return new FilterOutputStream(stream) {
            @Override
            public void close() throws IOException {
                super.close();
                file.close();
            }
        };
    }

    private static final class RandomAccessFileInputStream extends InputStream {
        private final RandomAccessFile file;

        private RandomAccessFileInputStream(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public int read() throws IOException {
            return file.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return file.read(b, off, len);
        }
    }

    private static final class RandomAccessFileOutputStream extends OutputStream {
        private final RandomAccessFile file;

        private RandomAccessFileOutputStream(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public void write(int b) throws IOException {
            file.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            file.write(b, off, len);
        }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = Math.max(0L, remaining);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int read = delegate.read(b, off, (int) Math.min(len, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long remaining;

        private BoundedOutputStream(OutputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = Math.max(0L, remaining);
        }

        @Override
        public void write(int b) throws IOException {
            requireCapacity(1);
            delegate.write(b);
            remaining--;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            requireCapacity(len);
            delegate.write(b, off, len);
            remaining -= len;
        }

        private void requireCapacity(int amount) throws IOException {
            if (amount < 0 || remaining < amount) {
                throw new IOException("Scratchpad write exceeds declared length");
            }
        }
    }
}
