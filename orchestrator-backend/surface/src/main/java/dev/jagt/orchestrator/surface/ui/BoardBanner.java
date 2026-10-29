package dev.jagt.orchestrator.surface.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
public class BoardBanner implements ApplicationRunner {

    @Value("${server.port:8290}")
    private final String port;

    @Override
    public void run(ApplicationArguments args) {
        Console.say("jagt board → http://localhost:" + port + "   (Ctrl-C stops)");
    }
}
