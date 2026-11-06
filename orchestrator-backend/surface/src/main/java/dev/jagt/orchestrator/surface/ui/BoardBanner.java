package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.service.BoardUrl;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
public class BoardBanner implements ApplicationRunner {

    private final BoardUrl boardUrl;

    @Override
    public void run(ApplicationArguments args) {
        Console.say("jagt board → " + boardUrl.root() + "   (Ctrl-C stops)");
    }
}
