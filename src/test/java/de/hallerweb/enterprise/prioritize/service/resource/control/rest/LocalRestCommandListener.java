package de.hallerweb.enterprise.prioritize.service.resource.control.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal, dependency-free local HTTP listener that makes the REST command
 * roundtrip reproducible in tests. Built on {@link HttpServer} from the
 * {@code jdk.httpserver} module — a supported, documented JDK API (the same one
 * behind the {@code jwebserver} tool), despite its {@code com.sun.net.httpserver}
 * package name. Using it keeps the build free of an extra, currently unmanaged
 * test dependency (WireMock / okhttp MockWebServer), which Spring Boot's BOM no
 * longer pins.
 * <p>
 * It binds to {@code 127.0.0.1} on an OS-assigned ephemeral port, records every
 * received request (method, path, body) and answers with a configurable status
 * code (200 by default). Point a {@code Resource}'s ip/port at {@link #getPort()}
 * and the {@link RestResourceControlAdapter} will deliver its command here.
 * <p>
 * Intended for tests and local discovery/telemetry experiments, not for production use.
 */
public final class LocalRestCommandListener implements AutoCloseable {

    /** A single captured request. */
    public record ReceivedRequest(String method, String path, String body) {
    }

    private final HttpServer server;
    private final List<ReceivedRequest> received = new CopyOnWriteArrayList<>();
    private volatile int responseStatus = 200;

    private LocalRestCommandListener(HttpServer server) {
        this.server = server;
    }

    /**
     * Starts a listener on a free port of the loopback interface. The listener
     * accepts any path, records the request and replies with the configured status.
     *
     * @return a started listener; close it (try-with-resources or {@code @AfterEach})
     * @throws IOException if the server socket cannot be opened
     */
    public static LocalRestCommandListener start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        LocalRestCommandListener listener = new LocalRestCommandListener(server);
        server.createContext("/", listener::handle);
        server.setExecutor(null); // default executor (a single background thread)
        server.start();
        return listener;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            URI uri = exchange.getRequestURI();
            // Record before responding, so a synchronous client sees the request
            // captured by the time its call returns.
            received.add(new ReceivedRequest(exchange.getRequestMethod(), uri.getPath(), body));
        }
        exchange.sendResponseHeaders(responseStatus, -1); // -1 = no response body
        exchange.close();
    }

    /** Makes the listener answer subsequent requests with the given HTTP status. */
    public void respondWithStatus(int status) {
        this.responseStatus = status;
    }

    /** The ephemeral port the listener is bound to. */
    public int getPort() {
        return server.getAddress().getPort();
    }

    /** All requests received so far, in arrival order. */
    public List<ReceivedRequest> getReceivedRequests() {
        return List.copyOf(received);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
