package com.focusdesktop.infrastructure.proxy;

import com.focusdesktop.domain.model.WebsiteTarget;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Loopback-only, bounded, request-driven transport. Never decrypts or stores TLS bytes. */
public final class WebsiteProxy implements AutoCloseable {
    public record Decision(boolean block, Runnable denied, Runnable failed) {
        public static Decision allow() { return new Decision(false, () -> {}, () -> {}); }
    }
    @FunctionalInterface public interface Connector { Socket connect(String host, int port) throws IOException; }
    private static final int MAX_HEADERS = 32768;
    private final Function<String, Decision> gate;
    private final Connector connector;
    private final boolean directFallback;
    private final ExecutorService workers = new ThreadPoolExecutor(0, 160, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> { var thread = new Thread(task, "website-proxy"); thread.setDaemon(true); return thread; });
    private final Semaphore slots = new Semaphore(64);
    private final Map<Socket, String> active = new ConcurrentHashMap<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final String pacToken;
    private final String verificationToken = UUID.randomUUID().toString();
    private final int requestedPort;
    private volatile boolean running;
    private volatile boolean routingObserved;
    private ServerSocket listener;

    public WebsiteProxy(Function<String, Decision> gate) { this(gate, WebsiteProxy::connectPublic); }
    WebsiteProxy(Function<String, Decision> gate, Connector connector) { this(gate, connector, false); }
    WebsiteProxy(Function<String, Decision> gate, boolean directFallback) { this(gate, WebsiteProxy::connectPublic, directFallback); }
    WebsiteProxy(Function<String, Decision> gate, Connector connector, boolean directFallback) {
        this(gate, connector, directFallback, 0, UUID.randomUUID().toString());
    }
    WebsiteProxy(Function<String, Decision> gate, Connector connector, boolean directFallback, int port, String token) {
        this.gate = gate; this.connector = connector; this.directFallback = directFallback;
        this.requestedPort = port; this.pacToken = UUID.fromString(token).toString();
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid proxy port");
    }
    WebsiteProxy(Function<String, Decision> gate, int port, String token) {
        this(gate, WebsiteProxy::connectPublic, true, port, token);
    }
    public synchronized void start() throws IOException {
        if (running) return;
        listener = new ServerSocket();
        listener.setReuseAddress(directFallback);
        try { listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), requestedPort), 16); }
        catch (IOException exception) { listener.close(); workers.shutdownNow(); throw exception; }
        running = true;
        workers.execute(() -> {
            while (running) {
                try {
                    Socket socket = listener.accept();
                    socket.setSoTimeout(60000);
                    if (!slots.tryAcquire()) { socket.close(); continue; }
                    sockets.add(socket);
                    try { workers.execute(() -> serve(socket)); }
                    catch (RejectedExecutionException exception) { close(socket); sockets.remove(socket); slots.release(); }
                } catch (IOException exception) { if (running) close(); }
            }
        });
    }
    public int port() { return listener == null ? 0 : listener.getLocalPort(); }
    public String pacUrl() { return "http://127.0.0.1:" + port() + "/pac/" + pacToken; }
    public boolean routingObserved() { return routingObserved; }
    void resetRoutingProof() { routingObserved = false; }
    public String verificationUrl() { return "http://focusdesktop.invalid/verify/" + verificationToken; }

    public void reconcile() {
        active.forEach((socket, host) -> {
            Decision decision;
            try { decision = gate.apply(host); } catch (RuntimeException exception) { return; }
            if (decision.block()) {
                close(socket);
                try { decision.denied().run(); } catch (RuntimeException ignored) { }
            }
        });
    }

    private void serve(Socket browser) {
        Socket upstream = null;
        try (browser) {
            var input = new BufferedInputStream(browser.getInputStream());
            var output = browser.getOutputStream();
            Request request;
            try { request = parse(input); }
            catch (IllegalArgumentException exception) { respond(output, 400, "Invalid proxy request"); return; }
            if (request.method().equals("GET") && request.target().equals("/pac/" + pacToken)) {
                String script = "function FindProxyForURL(url, host) { "
                        + "if (isPlainHostName(host) || host === 'localhost' || host === '::1' || "
                        + "shExpMatch(host, '127.*') || dnsDomainIs(host, '.localhost')) return 'DIRECT'; "
                        + "return 'PROXY 127.0.0.1:" + port() + (directFallback ? "; DIRECT" : "") + "'; }";
                respond(output, 200, script, "application/x-ns-proxy-autoconfig"); return;
            }
            Endpoint endpoint;
            try { endpoint = endpoint(request); }
            catch (IllegalArgumentException exception) { respond(output, 400, "Invalid proxy authority or framing"); return; }
            if (endpoint.host().equals("focusdesktop.invalid") && request.method().equals("GET")
                    && endpoint.path().equals("/verify/" + verificationToken)) {
                routingObserved = true;
                respond(output, 200, "FocusDesktop browser routing verified. Return to FocusDesktop."); return;
            }
            active.put(browser, endpoint.host());
            Decision decision = gate.apply(endpoint.host());
            if (decision.block()) {
                try { respond(output, 403, "Website blocked during focus time."); decision.denied().run(); }
                catch (Exception exception) { decision.failed().run(); }
                return;
            }
            upstream = connector.connect(endpoint.host(), endpoint.port());
            upstream.setSoTimeout(60000); sockets.add(upstream);
            if (request.method().equals("CONNECT")) {
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); output.flush();
                Socket remote = upstream;
                workers.execute(() -> { try { copy(remote.getInputStream(), output); } catch (IOException ignored) { }
                    finally { close(browser); close(remote); } });
                copy(input, upstream.getOutputStream());
            } else {
                var remote = upstream.getOutputStream();
                StringBuilder headers = new StringBuilder(request.method() + " " + endpoint.path() + " HTTP/1.1\r\n");
                Set<String> hop = new HashSet<>(Set.of("connection", "proxy-connection", "proxy-authorization", "proxy-authenticate",
                        "keep-alive", "te", "trailer", "transfer-encoding", "upgrade", "host"));
                for (String value : request.headers().getOrDefault("connection", List.of()))
                    for (String token : value.split(",")) hop.add(token.trim().toLowerCase(Locale.ROOT));
                request.headers().forEach((name, values) -> {
                    if (!hop.contains(name)) for (String value : values) headers.append(name).append(": ").append(value).append("\r\n");
                });
                headers.append("Host: ").append(endpoint.authority()).append("\r\nConnection: close\r\n\r\n");
                remote.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
                long remaining = endpoint.bodyLength(); byte[] buffer = new byte[8192];
                while (remaining > 0) {
                    int count = input.read(buffer, 0, (int) Math.min(remaining, buffer.length));
                    if (count < 0) throw new EOFException(); remote.write(buffer, 0, count); remaining -= count;
                }
                remote.flush();
                copy(upstream.getInputStream(), output);
            }
        } catch (Exception ignored) {
            // Raw errors may include destinations/URLs. Never log request or payload details.
        } finally {
            active.remove(browser); sockets.remove(browser);
            if (upstream != null) { sockets.remove(upstream); close(upstream); }
            slots.release();
        }
    }

    private static Request parse(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream(); int matched = 0;
        byte[] end = {'\r', '\n', '\r', '\n'};
        while (matched < 4) {
            int value = input.read();
            if (value < 0 || bytes.size() >= MAX_HEADERS || value > 126 || (value < 32 && value != '\r' && value != '\n'))
                throw new IllegalArgumentException("Invalid headers");
            bytes.write(value); matched = value == end[matched] ? matched + 1 : value == '\r' ? 1 : 0;
        }
        String[] lines = bytes.toString(StandardCharsets.US_ASCII).split("\r\n");
        String[] first = lines[0].split(" ", -1);
        if (first.length != 3 || !first[0].matches("[A-Z]{3,12}") || !first[2].equals("HTTP/1.1"))
            throw new IllegalArgumentException("Invalid request line");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int index = 1; index < lines.length; index++) {
            int colon = lines[index].indexOf(':');
            if (colon < 1 || !lines[index].substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))
                throw new IllegalArgumentException("Invalid header");
            String name = lines[index].substring(0, colon).toLowerCase(Locale.ROOT);
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(lines[index].substring(colon + 1).trim());
        }
        return new Request(first[0], first[1], headers);
    }
    private static Endpoint endpoint(Request request) {
        try {
            boolean tunnel = request.method().equals("CONNECT");
            URI uri = URI.create(tunnel ? "https://" + request.target() : request.target());
            if (!Set.of("http", "https").contains(uri.getScheme()) || (!tunnel && !"http".equals(uri.getScheme()))
                    || uri.getRawUserInfo() != null || uri.getFragment() != null || uri.getHost() == null)
                throw new IllegalArgumentException();
            String host = new WebsiteTarget(uri.getHost()).domain();
            int port = uri.getPort() == -1 ? tunnel ? 443 : 80 : uri.getPort();
            if (port < 1 || port > 65535 || tunnel && (!uri.getRawPath().isEmpty() || uri.getRawQuery() != null))
                throw new IllegalArgumentException();
            var hostHeader = request.headers().getOrDefault("host", List.of());
            String authority = host + ((port == (tunnel ? 443 : 80)) ? "" : ":" + port);
            if (hostHeader.size() != 1) throw new IllegalArgumentException();
            URI hostUri = URI.create((tunnel ? "https://" : "http://") + hostHeader.get(0));
            if (hostUri.getHost() == null || !host.equals(new WebsiteTarget(hostUri.getHost()).domain())
                    || (hostUri.getPort() == -1 ? tunnel ? 443 : 80 : hostUri.getPort()) != port
                    || hostUri.getRawUserInfo() != null || !hostUri.getRawPath().isEmpty() || hostUri.getRawQuery() != null)
                throw new IllegalArgumentException();
            var lengths = request.headers().getOrDefault("content-length", List.of());
            if (lengths.size() > 1 || request.headers().containsKey("transfer-encoding")
                    || request.headers().containsKey("upgrade") || request.headers().containsKey("expect")) throw new IllegalArgumentException();
            if (!lengths.isEmpty() && !lengths.get(0).matches("[0-9]{1,10}")) throw new IllegalArgumentException();
            for (String value : request.headers().getOrDefault("connection", List.of()))
                for (String token : value.split(","))
                    if (Set.of("host", "content-length", "transfer-encoding").contains(token.trim().toLowerCase(Locale.ROOT)))
                        throw new IllegalArgumentException();
            long length = lengths.isEmpty() ? 0 : Long.parseLong(lengths.get(0));
            if (length < 0 || length > 10 * 1024 * 1024 || tunnel && length != 0) throw new IllegalArgumentException();
            String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            return new Endpoint(host, port, authority, path, length);
        } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid proxy request"); }
    }
    private static Socket connectPublic(String host, int port) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new IOException("No destination");
        for (InetAddress address : addresses) {
            byte[] raw = address.getAddress();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || raw.length == 16 && (raw[0] & 0xfe) == 0xfc)
                throw new IOException("Local destinations bypass the proxy");
        }
        var socket = new Socket();
        try { socket.connect(new InetSocketAddress(addresses[0], port), 5000); return socket; }
        catch (IOException exception) { close(socket); throw exception; }
    }
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16384]; int count;
        while ((count = input.read(buffer)) >= 0) { output.write(buffer, 0, count); output.flush(); }
    }
    private static void respond(OutputStream output, int status, String message) throws IOException {
        respond(output, status, message, "text/plain; charset=utf-8");
    }
    private static void respond(OutputStream output, int status, String message, String type) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        output.write(("HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Request rejected")
                + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(body); output.flush();
    }
    private static void close(Socket socket) { try { socket.close(); } catch (IOException ignored) { } }
    @Override public synchronized void close() {
        running = false;
        try { if (listener != null) listener.close(); } catch (IOException ignored) { }
        sockets.forEach(WebsiteProxy::close); workers.shutdownNow();
    }
    private record Request(String method, String target, Map<String, List<String>> headers) { }
    private record Endpoint(String host, int port, String authority, String path, long bodyLength) { }
}
