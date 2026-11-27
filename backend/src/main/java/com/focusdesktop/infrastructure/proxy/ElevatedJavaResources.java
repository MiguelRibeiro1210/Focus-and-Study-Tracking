package com.focusdesktop.infrastructure.proxy;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** FUSE AppImage mounts may exclude root. Stage only this app's bundled Java/JAR. */
final class ElevatedJavaResources implements AutoCloseable {
    private final Path directory;
    private final List<String> command;
    private ElevatedJavaResources(Path directory, List<String> command) { this.directory = directory; this.command = List.copyOf(command); }
    List<String> command() { return command; }
    static ElevatedJavaResources prepare(List<String> command, Path data) throws IOException {
        Path javaHome = Path.of(System.getProperty("java.home"));
        int cp = command.indexOf("-cp");
        String classpath = cp >= 0 ? command.get(cp + 1) : "";
        Path jar = classpath.endsWith(".jar") && !classpath.contains(File.pathSeparator) ? Path.of(classpath) : null;
        boolean fuse = Files.getFileStore(javaHome).type().toLowerCase(Locale.ROOT).contains("fuse")
                || jar != null && Files.getFileStore(jar).type().toLowerCase(Locale.ROOT).contains("fuse");
        if (!fuse) return new ElevatedJavaResources(null, command);
        if (jar == null) throw new IOException("A bundled backend JAR is required for privileged AppImage setup");
        return stage(javaHome, jar, command, data);
    }
    static ElevatedJavaResources stage(Path javaHome, Path jar, List<String> sourceCommand, Path data) throws IOException {
        Path temporary = Files.createTempDirectory(data, "connection-helper-");
        var staged = new ElevatedJavaResources(temporary, sourceCommand);
        try {
            Path runtime = temporary.resolve("runtime");
            try (var files = Files.walk(javaHome)) {
                for (Path source : files.toList()) {
                    Path target = runtime.resolve(javaHome.relativize(source));
                    if (Files.isSymbolicLink(source)) {
                        Path resolved = source.getParent().resolve(Files.readSymbolicLink(source)).normalize();
                        if (!resolved.startsWith(javaHome)) throw new IOException("External runtime symlink");
                        Files.createSymbolicLink(target, target.getParent().relativize(runtime.resolve(javaHome.relativize(resolved))));
                    } else if (Files.isDirectory(source)) Files.createDirectories(target);
                    else Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                }
            }
            Path backend = temporary.resolve("backend.jar"); Files.copy(jar, backend);
            var command = new ArrayList<>(sourceCommand);
            command.set(0, runtime.resolve("bin/java").toString());
            command.set(command.indexOf("-cp") + 1, backend.toString());
            return new ElevatedJavaResources(temporary, command);
        } catch (IOException | RuntimeException exception) { staged.close(); throw exception; }
    }
    @Override public void close() throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
