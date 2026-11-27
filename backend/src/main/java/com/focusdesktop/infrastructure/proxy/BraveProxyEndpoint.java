package com.focusdesktop.infrastructure.proxy;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.Function;

/** Installation-local connection metadata. Never stores consent or routing proof. */
final class BraveProxyEndpoint implements AutoCloseable {
    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;
    private final boolean saved;

    BraveProxyEndpoint(Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.getParent());
        Path lockFile = file.resolveSibling(file.getFileName() + ".lock");
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(lockFile)) throw new IOException("Unsafe connection metadata");
        channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("Connection already owned");
        } catch (Exception exception) { channel.close(); throw new IOException("Connection already owned", exception); }
        saved = Files.exists(file);
    }

    boolean saved() { return saved; }

    WebsiteProxy start(Function<String, WebsiteProxy.Decision> gate) throws IOException {
        int port = 0;
        String token = UUID.randomUUID().toString();
        if (saved) {
            if (Files.isSymbolicLink(file) || Files.size(file) > 256) throw new IOException("Invalid connection metadata");
            try {
                String[] values = Files.readString(file).strip().split("\n", -1);
                if (values.length != 2) throw new IllegalArgumentException();
                port = Integer.parseInt(values[0]);
                token = UUID.fromString(values[1]).toString();
                if (port < 1024 || port > 65535 || !token.equals(values[1])) throw new IllegalArgumentException();
            } catch (RuntimeException exception) { throw new IOException("Invalid connection metadata", exception); }
        }
        var proxy = new WebsiteProxy(gate, port, token);
        try {
            proxy.start();
            if (!saved) {
                Path temporary = Files.createTempFile(file.getParent(), "brave-endpoint-", ".tmp");
                try {
                    Files.writeString(temporary, proxy.port() + "\n" + token + "\n");
                    try (var data = FileChannel.open(temporary, StandardOpenOption.WRITE)) { data.force(true); }
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            }
            return proxy;
        } catch (Exception exception) { proxy.close(); throw exception; }
    }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
