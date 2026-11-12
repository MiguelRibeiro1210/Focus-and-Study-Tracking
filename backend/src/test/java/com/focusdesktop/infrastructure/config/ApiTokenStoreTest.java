package com.focusdesktop.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiTokenStoreTest {
    @TempDir
    Path dataDirectory;

    @Test
    void generatesAndReusesTokenInLocalFile() throws Exception {
        ApiTokenStore store = new ApiTokenStore(dataDirectory);

        String first = store.loadOrCreate();
        String second = store.loadOrCreate();

        assertEquals(first, second);
        assertEquals(first, Files.readString(dataDirectory.resolve("api-token")).trim());
        assertFalse(first.isBlank());
    }

    @Test
    void restrictsTokenFileToCurrentUserWherePosixPermissionsAreAvailable() throws Exception {
        ApiTokenStore store = new ApiTokenStore(dataDirectory);
        store.loadOrCreate();
        Path tokenFile = dataDirectory.resolve("api-token");

        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(tokenFile);
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    permissions);
        } catch (UnsupportedOperationException ignored) {
            assertTrue(Files.isReadable(tokenFile));
        }
    }
}
