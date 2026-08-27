package com.mine.geometry_node.client.ai.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** One authenticated registration on the shared, loopback-only MCP endpoint. */
public final class McpHttpServer implements AutoCloseable {
    public static final int MAX_REQUEST_BYTES = 1_048_576;
    public static final int PORT = 37_654;
    public static final String ROUTE = "/mcp";
    public static final String TOKEN_ENVIRONMENT = "GEOMETRY_NODE_MCP_TOKEN";
    public static final String URL_ENVIRONMENT = "GEOMETRY_NODE_MCP_URL";
    private static final Gson GSON = new Gson();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Object HOST_LOCK = new Object();
    private static SharedHost sharedHost;

    private final SharedHost host;
    private final String token;
    private final AtomicBoolean closed = new AtomicBoolean();

    private McpHttpServer(SharedHost host, String token) {
        this.host = host;
        this.token = token;
    }

    public static McpHttpServer start(McpRequestDispatcher dispatcher) throws IOException {
        Objects.requireNonNull(dispatcher, "dispatcher");
        synchronized (HOST_LOCK) {
            if (sharedHost == null) sharedHost = SharedHost.start();
            String token = randomSecret(32);
            sharedHost.register(token, dispatcher);
            return new McpHttpServer(sharedHost, token);
        }
    }

    public URI endpoint() {
        return URI.create("http://127.0.0.1:" + PORT + ROUTE);
    }

    public String bearerToken() { return token; }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        synchronized (HOST_LOCK) {
            host.unregister(token);
            if (host.isEmpty() && sharedHost == host) {
                sharedHost = null;
                host.close();
            }
        }
    }

    private static final class SharedHost implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final ConcurrentHashMap<String, Binding> bindings = new ConcurrentHashMap<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        private SharedHost(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        private static SharedHost start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), PORT), 16);
            ExecutorService executor = Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("geometry-node-mcp-http-", 0).factory());
            SharedHost host = new SharedHost(server, executor);
            server.createContext(ROUTE, host::handle);
            server.setExecutor(executor);
            server.start();
            return host;
        }

        private void register(String token, McpRequestDispatcher dispatcher) {
            if (closed.get()) throw new IllegalStateException("MCP host is closed");
            if (bindings.putIfAbsent(token, new Binding(token, dispatcher)) != null) {
                throw new IllegalStateException("MCP token collision");
            }
        }

        private void unregister(String token) {
            Binding binding = bindings.remove(token);
            if (binding != null) binding.dispatcher.close();
        }

        private boolean isEmpty() { return bindings.isEmpty(); }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            server.stop(0);
            for (Binding binding : bindings.values()) binding.dispatcher.close();
            bindings.clear();
            executor.shutdownNow();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (closed.get()) {
                    sendText(exchange, 410, "MCP host is closed");
                    return;
                }
                if (!validHost(exchange) || !validOrigin(exchange)) {
                    sendText(exchange, 403, "Loopback request validation failed");
                    return;
                }
                if (!ROUTE.equals(exchange.getRequestURI().getPath())) {
                    sendText(exchange, 404, "MCP endpoint not found");
                    return;
                }
                Binding binding = authorizedBinding(exchange);
                if (binding == null) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                    sendText(exchange, 401, "Unauthorized");
                    return;
                }
                if (!binding.requestBudget.tryAcquire()) {
                    sendText(exchange, 429, "MCP request rate limit exceeded");
                    return;
                }
                if (!binding.requests.tryAcquire()) {
                    sendText(exchange, 429, "Too many concurrent MCP requests");
                    return;
                }
                try {
                    switch (exchange.getRequestMethod().toUpperCase(Locale.ROOT)) {
                        case "POST" -> handlePost(exchange, binding.dispatcher);
                        case "DELETE" -> handleDelete(exchange, binding.dispatcher);
                        case "GET" -> {
                            exchange.getResponseHeaders().set("Allow", "POST, DELETE");
                            sendText(exchange, 405, "Standalone SSE is not supported");
                        }
                        default -> {
                            exchange.getResponseHeaders().set("Allow", "POST, DELETE");
                            sendText(exchange, 405, "Method not allowed");
                        }
                    }
                } finally {
                    binding.requests.release();
                }
            }
        }

        private Binding authorizedBinding(HttpExchange exchange) {
            if (headerValues(exchange, "Authorization").size() != 1) return null;
            String authorization = header(exchange, "Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) return null;
            byte[] candidate = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
            for (Binding binding : bindings.values()) {
                if (MessageDigest.isEqual(candidate, binding.tokenBytes)) {
                    return binding;
                }
            }
            return null;
        }
    }

    private static void handlePost(HttpExchange exchange, McpRequestDispatcher dispatcher) throws IOException {
        String contentType = header(exchange, "Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            sendText(exchange, 415, "Content-Type must be application/json");
            return;
        }
        long declaredLength = contentLength(exchange);
        if (declaredLength == Long.MIN_VALUE || declaredLength > MAX_REQUEST_BYTES) {
            sendText(exchange, 413, "MCP request is too large");
            return;
        }
        byte[] body;
        try {
            body = readBounded(exchange.getRequestBody());
        } catch (RequestTooLargeException tooLarge) {
            sendText(exchange, 413, "MCP request is too large");
            return;
        }
        JsonObject request;
        try {
            String json = new String(body, StandardCharsets.UTF_8);
            if (!McpJsonLimits.accepts(json)) {
                sendJson(exchange, 400, jsonRpcError(-32600, "JSON structure exceeds MCP limits"), "");
                return;
            }
            request = GSON.fromJson(json, JsonObject.class);
            if (request == null) throw new JsonParseException("request must be a JSON object");
        } catch (JsonParseException | IllegalStateException malformed) {
            sendJson(exchange, 400, jsonRpcError(-32700, "Parse error"), "");
            return;
        }
        String sessionId = normalize(header(exchange, "Mcp-Session-Id"));
        boolean initialize = "initialize".equals(request.has("method") && request.get("method").isJsonPrimitive()
                ? request.get("method").getAsString() : "");
        if (!initialize && !dispatcher.acceptsProtocolVersion(
                sessionId, header(exchange, "MCP-Protocol-Version"))) {
            sendText(exchange, 400, "Invalid MCP protocol version or session");
            return;
        }
        McpRequestDispatcher.Reply reply = dispatcher.dispatch(request, sessionId);
        if (reply.notification()) {
            exchange.sendResponseHeaders(202, -1);
            return;
        }
        sendJson(exchange, 200, reply.body(), reply.newSessionId());
    }

    private static void handleDelete(HttpExchange exchange, McpRequestDispatcher dispatcher) throws IOException {
        String sessionId = normalize(header(exchange, "Mcp-Session-Id"));
        if (sessionId.isBlank()) {
            sendText(exchange, 400, "Mcp-Session-Id is required");
            return;
        }
        if (!dispatcher.removeSession(sessionId)) {
            sendText(exchange, 404, "MCP session not found");
            return;
        }
        exchange.sendResponseHeaders(204, -1);
    }

    private static boolean validHost(HttpExchange exchange) {
        if (headerValues(exchange, "Host").size() != 1) return false;
        String host = normalize(header(exchange, "Host")).toLowerCase(Locale.ROOT);
        return host.equals("127.0.0.1:" + PORT) || host.equals("localhost:" + PORT);
    }

    private static boolean validOrigin(HttpExchange exchange) {
        List<String> origins = headerValues(exchange, "Origin");
        if (origins.size() > 1) return false;
        String origin = normalize(header(exchange, "Origin")).toLowerCase(Locale.ROOT);
        if (origin.isBlank()) return true;
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            int port = uri.getPort() < 0 ? 80 : uri.getPort();
            String path = uri.getPath();
            return "http".equals(uri.getScheme()) && port == PORT && host != null
                    && (host.equals("127.0.0.1") || host.equals("localhost"))
                    && uri.getUserInfo() == null && (path == null || path.isEmpty() || path.equals("/"))
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException, RequestTooLargeException {
        byte[] buffer = new byte[8 * 1024];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(buffer.length);
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            if (output.size() > MAX_REQUEST_BYTES - read) throw new RequestTooLargeException();
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void sendJson(HttpExchange exchange, int status, JsonObject json, String sessionId)
            throws IOException {
        byte[] body = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (!sessionId.isBlank()) exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static JsonObject jsonRpcError(int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", com.google.gson.JsonNull.INSTANCE);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static List<String> headerValues(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        return values == null ? List.of() : values;
    }

    private static long contentLength(HttpExchange exchange) {
        List<String> values = headerValues(exchange, "Content-Length");
        if (values.size() > 1) return Long.MIN_VALUE;
        String value = normalize(header(exchange, "Content-Length"));
        if (value.isBlank()) return -1;
        try {
            long length = Long.parseLong(value);
            return length < 0 ? Long.MIN_VALUE : length;
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }

    private static String randomSecret(int bytes) {
        byte[] value = new byte[bytes];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static final class Binding {
        private final byte[] tokenBytes;
        private final McpRequestDispatcher dispatcher;
        private final Semaphore requests = new Semaphore(8);
        private final RequestBudget requestBudget = new RequestBudget();

        private Binding(String token, McpRequestDispatcher dispatcher) {
            this.tokenBytes = token.getBytes(StandardCharsets.UTF_8);
            this.dispatcher = dispatcher;
        }
    }

    private static final class RequestBudget {
        private static final int MAX_REQUESTS = 240;
        private static final long WINDOW_NANOS = java.time.Duration.ofMinutes(1).toNanos();
        private final Deque<Long> requests = new ArrayDeque<>(MAX_REQUESTS);

        private synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            long cutoff = now - WINDOW_NANOS;
            while (!requests.isEmpty() && requests.peekFirst() < cutoff) requests.removeFirst();
            if (requests.size() >= MAX_REQUESTS) return false;
            requests.addLast(now);
            return true;
        }
    }

    private static final class RequestTooLargeException extends Exception { }
}
