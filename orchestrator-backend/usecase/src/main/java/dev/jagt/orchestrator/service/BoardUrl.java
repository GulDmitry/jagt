package dev.jagt.orchestrator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class BoardUrl {

    private final String root;

    public BoardUrl(@Value("${server.port:8290}") String port) {
        this.root = "http://localhost:" + port.trim();
    }

    public String root() {
        return root;
    }

    public String ofTask(String taskId) {
        return root + "/?task=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
    }
}
