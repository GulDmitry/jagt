package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRewriteTest {

    @Test
    void theLocalModelRewritesTheTicketWhenItAnswers(@TempDir Path stand) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"Greet everyone.\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            LocalRewrite rewrite = new LocalRewrite(stand, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "qwen", (ask, ticket) -> "cloud");

            String rewritten = rewrite.rewrite("Make it vague.", "Greet.");

            assertThat(rewritten).isEqualTo("Greet everyone.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theCloudRewritesTheTicketWhenTheLocalModelIsUnreachable(@TempDir Path stand) {
        LocalRewrite rewrite = new LocalRewrite(stand, "http://127.0.0.1:1/v1", "qwen", (ask, ticket) -> "cloud");

        String rewritten = rewrite.rewrite("Make it vague.", "Greet.");

        assertThat(rewritten).isEqualTo("cloud");
    }
}
