package com.focusdesktop.infrastructure.proxy;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fixed privileged destinations. The control channel carries leases, never traffic or API tokens. */
public final class PrivilegedProxyGuardian {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-check")) {
            BrowserProxyPolicy.pac("http://127.0.0.1:12345/pac/00000000-0000-0000-0000-000000000001");
            System.out.println("Packaged privileged connection guardian is available."); return;
        }
        if (args.length != 5) throw new IllegalArgumentException("Invalid connection lease");
        int port = Integer.parseInt(args[0]);
        String token = GuardianCredential.read(Path.of(args[1]), Long.parseLong(args[2]), Long.parseLong(args[3]));
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid lease endpoint");
        if (!args[4].equals("RECOVER")) BrowserProxyPolicy.pac(args[4]);
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!os.contains("linux") && !os.contains("mac")) throw new IllegalArgumentException("Unsupported privileged connection");
        Path directory = Path.of(os.contains("mac") ? "/Library/Application Support/FocusDesktop/connection" : "/var/lib/focusdesktop/connection");
        // Refuse redirection into a user's paths, including pre-created writable directories.
        for (Path path = directory; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Unsafe guardian directory");
            if (Files.exists(path) && (!Files.getOwner(path).getName().equals("root")
                    || Files.getPosixFilePermissions(path).contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)))
                throw new IOException("Guardian directory must be administrator-owned");
        }
        Files.createDirectories(directory, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
        try (var control = new Socket()) {
            control.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            control.setSoTimeout(15000);
            var response = new PrintStream(control.getOutputStream(), true, StandardCharsets.UTF_8);
            response.println(token);
            byte[] auth = control.getInputStream().readNBytes(5);
            if (!Arrays.equals(auth, "AUTH\n".getBytes(StandardCharsets.US_ASCII))) throw new IOException("Unauthenticated connection lease");
            control.setSoTimeout(0);
            ProxySettingsGuardian.run(os.contains("linux") ? new LinuxBrowserPolicies() : new NativeProxySettings(),
                    new String[]{directory.resolve("settings.json").toString(), args[2], args[3], args[4]},
                    15000, control.getInputStream(), response);
        }
    }
}
