package dev.jagt.orchestrator.surface.ui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Where to look, on STDOUT: a server whose terminal says nothing at all is indistinguishable from one that
 * died.
 */
@Component
@Order(Integer.MAX_VALUE)
@Slf4j
public class BoardBanner implements ApplicationRunner {

    private final String port;

    public BoardBanner(@Value("${server.port:8290}") String port) {
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("jagt board → http://localhost:" + port + "   (Ctrl-C stops)");
        log.atInfo().setMessage("board serving")
                .addKeyValue("url", "http://localhost:" + port)
                .log();
    }
}
