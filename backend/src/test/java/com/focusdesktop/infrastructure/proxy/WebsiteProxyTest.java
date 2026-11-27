package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class WebsiteProxyTest {
    @Test
    void closeReleasesListenerAndConnectedClientsBeforeImmediateRebind() throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (var proxy = new WebsiteProxy(host -> WebsiteProxy.Decision.allow(), true)) {
                proxy.start();
                int port = proxy.port();
                try (var browser = new Socket("127.0.0.1", port)) {
                    browser.setSoTimeout(3000);
                    proxy.close();
                    try (var replacement = new ServerSocket()) {
                        replacement.setReuseAddress(true);
                        replacement.bind(new InetSocketAddress("127.0.0.1", port));
                    }
                    // A connection racing accept must also be closed, never left queued.
                    try { assertEquals(-1, browser.getInputStream().read()); }
                    catch (SocketException expectedReset) { }
                }
            }
        }
    }
    @Test
    void deniesBlockedConnectBeforeOpeningAnyRemoteConnection() throws Exception {
        var connections = new AtomicInteger();
        var denied = new AtomicInteger();
        try (var proxy = new WebsiteProxy(host -> new WebsiteProxy.Decision(true, denied::incrementAndGet, () -> {}),
                (host, port) -> { connections.incrementAndGet(); throw new IOException(); })) {
            proxy.start();
            assertTrue(exchange(proxy, "CONNECT music.example.com:443 HTTP/1.1\r\nHost: music.example.com:443\r\n\r\n").startsWith("HTTP/1.1 403"));
            assertEquals(0, connections.get());
            assertEquals(1, denied.get());
        }
    }

    @Test
    void tunnelsBytesWithoutTlsDecryptionAndClosesExistingTunnelWhenRuleBecomesBlock() throws Exception {
        var blocked = new AtomicBoolean();
        try (var origin = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var proxy = new WebsiteProxy(host -> new WebsiteProxy.Decision(blocked.get(), () -> {}, () -> {}),
                     (host, port) -> new Socket(InetAddress.getLoopbackAddress(), origin.getLocalPort()))) {
            proxy.start();
            try (var browser = new Socket("127.0.0.1", proxy.port())) {
                browser.setSoTimeout(3000);
                browser.getOutputStream().write("CONNECT allowed.example:443 HTTP/1.1\r\nHost: allowed.example:443\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                assertTrue(readHeaders(browser.getInputStream()).startsWith("HTTP/1.1 200"));
                try (var remote = origin.accept()) {
                    remote.setSoTimeout(3000);
                    byte[] encrypted = {22, 3, 3, 0, 4, 0, (byte) 255, 3, 9};
                    browser.getOutputStream().write(encrypted);
                    assertArrayEquals(encrypted, remote.getInputStream().readNBytes(encrypted.length));
                    remote.getOutputStream().write(encrypted);
                    assertArrayEquals(encrypted, browser.getInputStream().readNBytes(encrypted.length));
                    blocked.set(true); proxy.reconcile();
                    assertEquals(-1, browser.getInputStream().read());
                }
            }
        }
    }

    @Test
    void forwardsHttpToItsValidatedAuthorityAndDoesNotForwardProxyCredentials() throws Exception {
        var request = new CompletableFuture<String>();
        try (var origin = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var proxy = new WebsiteProxy(host -> WebsiteProxy.Decision.allow(),
                     (host, port) -> new Socket(InetAddress.getLoopbackAddress(), origin.getLocalPort()))) {
            Thread remote = new Thread(() -> {
                try (var socket = origin.accept()) {
                    request.complete(readHeaders(socket.getInputStream()));
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".getBytes(StandardCharsets.US_ASCII));
                } catch (Exception error) { request.completeExceptionally(error); }
            });
            remote.setDaemon(true); remote.start(); proxy.start();
            assertTrue(exchange(proxy, "GET http://allowed.example/path?q=private HTTP/1.1\r\nHost: allowed.example\r\nProxy-Authorization: secret\r\nConnection: keep-alive\r\n\r\n").endsWith("OK"));
            String forwarded = request.get(3, TimeUnit.SECONDS);
            assertTrue(forwarded.startsWith("GET /path?q=private HTTP/1.1\r\n"));
            assertFalse(forwarded.contains("secret"));
            assertTrue(forwarded.contains("Connection: close"));
        }
    }

    @Test
    void rejectsAmbiguousFramingAndAuthoritiesWithoutOpeningRemoteSockets() throws Exception {
        var connections = new AtomicInteger();
        try (var proxy = new WebsiteProxy(host -> WebsiteProxy.Decision.allow(),
                (host, port) -> { connections.incrementAndGet(); throw new IOException(); })) {
            proxy.start();
            for (String request : new String[] {
                    "GET http://example.com/ HTTP/1.1\r\nHost: other.com\r\n\r\n",
                    "POST http://example.com/ HTTP/1.1\r\nHost: example.com\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\n",
                    "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nContent-Length: 1\r\n\r\n",
                    "GET http://user:pass@example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n",
                    "GET /api/v1/settings HTTP/1.1\r\nHost: localhost\r\n\r\n"}) {
                assertTrue(exchange(proxy, request).startsWith("HTTP/1.1 400"));
            }
            assertEquals(0, connections.get());
        }
    }

    private static String exchange(WebsiteProxy proxy, String request) throws Exception {
        try (var socket = new Socket("127.0.0.1", proxy.port())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        }
    }
    private static String readHeaders(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        while (bytes.size() < 32768) {
            int value = input.read(); if (value < 0) break; bytes.write(value);
            if (bytes.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) return bytes.toString(StandardCharsets.US_ASCII);
        }
        throw new IOException("Missing headers");
    }
}
