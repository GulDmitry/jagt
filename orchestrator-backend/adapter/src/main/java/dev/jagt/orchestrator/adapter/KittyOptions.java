package dev.jagt.orchestrator.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** An empty font size leaves it to kitty's own config. */
@Component
public record KittyOptions(@Value("${orchestrator.kitty-command:kitty}") String command,
                           @Value("${orchestrator.kitty-font-size:}") String fontSize) {
}
