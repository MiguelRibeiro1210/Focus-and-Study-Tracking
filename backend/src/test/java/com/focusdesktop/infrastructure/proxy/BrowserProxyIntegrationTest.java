package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "FOCUSDESKTOP_FIREFOX", matches = ".+")
class BrowserProxyIntegrationTest {
    @TempDir Path directory;
    @Test void firefoxUsesPacForVerificationAllowedHttpAndBlockedHttpWithoutRemoteTestTraffic() throws Exception {
        var connections = new AtomicInteger(); var denials = new AtomicInteger();
        try (var origin = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
             var proxy = new WebsiteProxy(host -> new WebsiteProxy.Decision(host.equals("blocked.example"), denials::incrementAndGet, () -> {}),
                     (host, port) -> {
                         if (!host.equals("allowed.example")) throw new IOException("Only the controlled origin is available");
                         connections.incrementAndGet(); return new Socket(InetAddress.getLoopbackAddress(), origin.getLocalPort());
                     })) {
            var responder = new Thread(() -> {
                while (!origin.isClosed()) {
                    try (var socket = origin.accept()) {
                        socket.setSoTimeout(3000);
                        var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        while (!input.readLine().isEmpty()) { }
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 7\r\nCache-Control: no-store\r\nConnection: close\r\n\r\nALLOWED".getBytes(StandardCharsets.US_ASCII));
                    } catch (IOException ignored) { }
                }
            }); responder.setDaemon(true); responder.start(); proxy.start();
            Path profile = Files.createDirectory(directory.resolve("profile"));
            Files.writeString(profile.resolve("user.js"), "user_pref(\"network.proxy.type\", 2);\n"
                    + "user_pref(\"network.proxy.autoconfig_url\", \"" + proxy.pacUrl() + "\");\n"
                    + "user_pref(\"network.proxy.failover_direct\", false);\n"
                    + "user_pref(\"browser.cache.disk.enable\", false);\nuser_pref(\"browser.cache.memory.enable\", false);\n"
                    + "user_pref(\"browser.shell.checkDefaultBrowser\", false);\nuser_pref(\"browser.startup.homepage_override.mstone\", \"ignore\");\n"
                    + "user_pref(\"datareporting.healthreport.uploadEnabled\", false);\nuser_pref(\"datareporting.policy.dataSubmissionEnabled\", false);\n");
            browse(profile, proxy.verificationUrl(), "verified"); assertTrue(proxy.routingObserved());
            browse(profile, "http://allowed.example/", "allowed"); assertTrue(connections.get() > 0);
            int before = connections.get(); browse(profile, "http://blocked.example/", "blocked");
            assertTrue(denials.get() > 0); assertEquals(before, connections.get());
            int previousDenials = denials.get();
            var https = new ProcessBuilder(System.getenv("FOCUSDESKTOP_FIREFOX"), "--headless", "--no-remote", "--profile", profile.toString(),
                    "--screenshot", directory.resolve("https-blocked.png").toString(), "https://blocked.example/")
                    .redirectErrorStream(true).redirectOutput(directory.resolve("https-blocked.log").toFile()).start();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (denials.get() == previousDenials && https.isAlive() && System.nanoTime() < deadline) Thread.sleep(25);
                assertTrue(denials.get() > previousDenials, "Firefox HTTPS must reach the CONNECT denial");
                assertEquals(before, connections.get(), "Blocked HTTPS must not reach the origin");
            } finally { https.descendants().forEach(ProcessHandle::destroyForcibly); https.destroyForcibly(); https.waitFor(5, TimeUnit.SECONDS); }
        }
    }
    private void browse(Path profile, String url, String name) throws Exception {
        var process = new ProcessBuilder(System.getenv("FOCUSDESKTOP_FIREFOX"), "--headless", "--no-remote", "--profile", profile.toString(),
                "--screenshot", directory.resolve(name + ".png").toString(), url)
                .redirectErrorStream(true).redirectOutput(directory.resolve(name + ".log").toFile()).start();
        try { assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Owned Firefox fixture must finish"); assertEquals(0, process.exitValue()); }
        finally { process.destroyForcibly(); }
    }
}
