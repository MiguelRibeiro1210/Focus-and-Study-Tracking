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
        default boolean independentSettings() { return false; }
        default Map<String, String> readAll(List<String> keys) {
            var values = new LinkedHashMap<String, String>(); keys.forEach(key -> values.put(key, read(key))); return values;
        }
        default void writeAll(Map<String, String> values) { values.forEach(this::write); }
        default void restore(List<Change> changes) {
            RuntimeException failure = null;
            for (int index = changes.size() - 1; index >= 0; index--) {
                Change change = changes.get(index);
                try {
                    if (Objects.equals(read(change.key()), change.applied())) {
                        write(change.key(), change.before());
                        if (!Objects.equals(read(change.key()), change.before())) throw new IllegalStateException("Restoration not confirmed");
                    }
                } catch (RuntimeException exception) { failure = exception; }
            }
            if (failure != null) throw failure;
        }
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
        var applied = new LinkedHashMap<String, String>(); changes.forEach(change -> applied.put(change.key(), change.applied()));
        store.writeAll(applied);
        var current = store.readAll(changes.stream().map(Change::key).toList());
        for (Change change : changes)
            if (!Objects.equals(current.get(change.key()), change.applied())) throw new IOException("Settings not confirmed");
    }
    synchronized void restore() throws IOException {
        if (closed) return;
        if (!Files.exists(journal)) return;
        if (Files.isSymbolicLink(journal) || Files.size(journal) > 65536) throw new IOException("Unsafe settings journal");
        List<Change> changes = Arrays.asList(mapper.readValue(Files.readAllBytes(journal), Change[].class));
        // Proxy mode and URL form one configuration. Restoring only the mode after
        // a user changes the URL could disable their replacement proxy.
        for (Change change : store.independentSettings() ? List.<Change>of() : changes) {
            String current = store.read(change.key());
            if (!Objects.equals(current, change.before()) && !Objects.equals(current, change.applied())) {
                Files.deleteIfExists(journal); return;
            }
        }
        try { store.restore(changes); }
        catch (RuntimeException exception) { throw new IOException("Proxy settings still need restoration", exception); }
        Files.deleteIfExists(journal);
    }
    synchronized boolean isApplied() throws IOException {
        if (closed || !Files.exists(journal) || Files.isSymbolicLink(journal) || Files.size(journal) > 65536) return false;
        List<Change> changes = Arrays.asList(mapper.readValue(Files.readAllBytes(journal), Change[].class));
        var current = store.readAll(changes.stream().map(Change::key).toList());
        for (Change change : changes)
            if (!Objects.equals(current.get(change.key()), change.applied())) return false;
        return true;
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        try { restore(); } finally { closed = true; lock.release(); channel.close(); }
    }
}
