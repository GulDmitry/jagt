package dev.jagt.orchestrator.stand;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

public class StandHost implements AutoCloseable {

    private final HttpServer server;
    private final StandStore store;
    private final JsonRpc rpc;

    public StandHost(Path directory) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.store = new StandStore(directory, baseUrl());
        this.rpc = new JsonRpc(new StandTools(store));
        server.createContext("/mcp", this::serve);
    }

    public StandHost start() {
        server.start();
        return this;
    }

    public String mcpUrl() {
        return baseUrl() + "/mcp";
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public StandStore store() {
        return store;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Optional<String> answer = rpc.handle(body);
            if (answer.isEmpty()) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
