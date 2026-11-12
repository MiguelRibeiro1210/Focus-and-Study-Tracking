package com.focusdesktop.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ApiTokenStore {
    private static final Set<AclEntryPermission> OWNER_PERMISSIONS = EnumSet.of(
            AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA,
            AclEntryPermission.APPEND_DATA, AclEntryPermission.READ_ATTRIBUTES,
            AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.READ_NAMED_ATTRS,
            AclEntryPermission.WRITE_NAMED_ATTRS, AclEntryPermission.READ_ACL,
            AclEntryPermission.WRITE_ACL, AclEntryPermission.SYNCHRONIZE);
    private final Path tokenFile;
    private final SecureRandom random;

    public ApiTokenStore(Path dataDirectory) {
        this(dataDirectory, new SecureRandom());
    }

    ApiTokenStore(Path dataDirectory, SecureRandom random) {
        this.tokenFile = Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("api-token");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized String loadOrCreate() {
        try {
            Files.createDirectories(tokenFile.getParent());
            if (Files.exists(tokenFile)) {
                restrictPermissions();
                String value = Files.readString(tokenFile).trim();
                if (!value.isEmpty()) {
                    return value;
                }
                byte[] bytes = new byte[32];
                random.nextBytes(bytes);
                value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                Files.writeString(tokenFile, value + System.lineSeparator());
                restrictPermissions();
                return value;
            }
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            createRestricted(value);
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load or create the local API token", exception);
        }
    }

    private void createRestricted(String value) throws IOException {
        try {
            Files.createFile(tokenFile, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException exception) {
            Files.createFile(tokenFile);
        }
        restrictPermissions();
        Files.writeString(tokenFile, value + System.lineSeparator());
    }

    private void restrictPermissions() throws IOException {
        try {
            Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-------"));
            return;
        } catch (UnsupportedOperationException ignored) {
            // Windows uses an ACL instead of POSIX mode bits.
        }
        AclFileAttributeView view = Files.getFileAttributeView(tokenFile, AclFileAttributeView.class);
        if (view != null) {
            var owner = Files.getOwner(tokenFile);
            AclEntry ownerEntry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(OWNER_PERMISSIONS)
                    .build();
            view.setAcl(List.of(ownerEntry));
        }
    }
}
