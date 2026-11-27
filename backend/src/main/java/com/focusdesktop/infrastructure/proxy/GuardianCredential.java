package com.focusdesktop.infrastructure.proxy;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

final class GuardianCredential {
    static String read(Path file, long parent, long started) throws IOException {
        var process = ProcessHandle.of(parent).filter(ProcessHandle::isAlive).orElseThrow(() -> new IOException("Parent unavailable"));
        var info = process.info();
        if (info.startInstant().map(time -> time.toEpochMilli() != started).orElse(true)
                || Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > 64 || !info.user().filter(Files.getOwner(file).getName()::equals).isPresent())
            throw new IOException("Invalid guardian credential ownership");
        var allowed = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        if (!allowed.containsAll(Files.getPosixFilePermissions(file))) throw new IOException("Guardian credential must be private");
        String token = Files.readString(file).strip();
        try { if (!UUID.fromString(token).toString().equals(token)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid guardian credential", exception); }
        return token;
    }
}
