package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ElevatedJavaResourcesTest {
    @TempDir Path directory;
    @Test void stagesBundledRuntimeAndJarOutsideFuseAndRemovesOnlyItsOwnTemporaryFiles() throws Exception {
        Path source = directory.resolve("bundle/runtime"); Files.createDirectories(source.resolve("bin"));
        Files.createDirectories(source.resolve("lib")); Files.writeString(source.resolve("bin/java"), "bundled executable");
        Files.writeString(source.resolve("lib/modules"), "bundled modules");
        Path jar = directory.resolve("bundle/backend.jar"); Files.writeString(jar, "bundled jar");
        var command = List.of(source.resolve("bin/java").toString(), "-Dloader.main=Guardian", "-cp", jar.toString(), "Loader", "fixed-argument");
        Path stagedRuntime;
        try (var staged = ElevatedJavaResources.stage(source, jar, command, directory)) {
            stagedRuntime = Path.of(staged.command().get(0)).getParent().getParent();
            assertEquals("bundled executable", Files.readString(stagedRuntime.resolve("bin/java")));
            assertEquals("bundled modules", Files.readString(stagedRuntime.resolve("lib/modules")));
            assertEquals("bundled jar", Files.readString(Path.of(staged.command().get(3))));
            assertEquals("fixed-argument", staged.command().get(5));
            assertNotEquals(source, stagedRuntime);
        }
        assertFalse(Files.exists(stagedRuntime)); assertTrue(Files.exists(source.resolve("bin/java"))); assertTrue(Files.exists(jar));
    }
}
