package com.focusdesktop.infrastructure.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Compare-and-restore ownership journal. Native commands are supplied by a fixed adapter. */
final class OwnedProxySettings implements AutoCloseable {
    interface Store {
        List<Change> prepare(String pacUrl);
        String read(String key);
        void write(String key, String value);
    }
    record Change(String key, String before, String applied) { }
    private final Store store;
    private final Path journal;
    private final ObjectMapper mapper = new ObjectMapper();
    private final FileChannel channel;
    private final java.nio.channels.FileLock lock;
    private boolean closed;
    OwnedProxySettings(Store store, Path journal) throws IOException {
        this.store = store; this.journal = journal;
        Files.createDirectories(journal.getParent());
        if (Files.isSymbolicLink(journal) || Files.isSymbolicLink(journal.resolveSibling(journal.getFileName() + ".lock")))
            throw new IOException("Unsafe settings journal");
        channel = FileChannel.open(journal.resolveSibling(journal.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try { lock = channel.tryLock(); if (lock == null) throw new IOException("Settings already owned"); }
        catch (Exception exception) { channel.close(); throw exception; }
    }
    void apply(String pacUrl) throws IOException {
        restore();
        List<Change> changes = store.prepare(pacUrl);
        if (changes.isEmpty()) throw new IOException("No supported proxy settings");
        var temporary = Files.createTempFile(journal.getParent(), "proxy-settings-", ".tmp");
        try {
            Files.write(temporary, mapper.writeValueAsBytes(changes));
            try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE)) { file.force(true); }
            Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        for (Change change : changes) store.write(change.key(), change.applied());
        for (Change change : changes)
            if (!Objects.equals(store.read(change.key()), change.applied())) throw new IOException("Settings not confirmed");
    }
    synchronized void restore() throws IOException {
        if (closed) return;
        if (!Files.exists(journal)) return;
        if (Files.isSymbolicLink(journal) || Files.size(journal) > 65536) throw new IOException("Unsafe settings journal");
        List<Change> changes = Arrays.asList(mapper.readValue(Files.readAllBytes(journal), Change[].class));
        // Proxy mode and URL form one configuration. Restoring only the mode after
        // a user changes the URL could disable their replacement proxy.
        for (Change change : changes) {
            String current = store.read(change.key());
            if (!Objects.equals(current, change.before()) && !Objects.equals(current, change.applied())) {
                Files.deleteIfExists(journal); return;
            }
        }
        RuntimeException failure = null;
        for (int index = changes.size() - 1; index >= 0; index--) {
            Change change = changes.get(index);
            try {
                if (Objects.equals(store.read(change.key()), change.applied())) {
                    store.write(change.key(), change.before());
                    if (!Objects.equals(store.read(change.key()), change.before())) throw new IllegalStateException("Restoration not confirmed");
                }
            } catch (RuntimeException exception) { failure = exception; }
        }
        if (failure != null) throw new IOException("Proxy settings still need restoration", failure);
        Files.deleteIfExists(journal);
    }
    synchronized boolean isApplied() throws IOException {
        if (closed || !Files.exists(journal) || Files.isSymbolicLink(journal) || Files.size(journal) > 65536) return false;
        for (Change change : mapper.readValue(Files.readAllBytes(journal), Change[].class))
            if (!Objects.equals(store.read(change.key()), change.applied())) return false;
        return true;
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        try { restore(); } finally { closed = true; lock.release(); channel.close(); }
    }
}
