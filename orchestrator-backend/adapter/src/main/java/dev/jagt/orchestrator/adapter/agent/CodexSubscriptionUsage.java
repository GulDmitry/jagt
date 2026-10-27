package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "codex")
@RequiredArgsConstructor
public class CodexSubscriptionUsage implements SubscriptionUsage {

    static final URI USAGE = URI.create("https://chatgpt.com/backend-api/wham/usage");

    private final CodexCredentials credentials;
    private final UsageEndpoint endpoint;
    private final Clock clock;

    @Override
    public Answer read() {
        return UsageRead.of(credentials.read(clock.instant()), endpoint, USAGE, "codex",
                token -> Map.of("Authorization", "Bearer " + token.accessToken(), "ChatGPT-Account-Id", token.accountId()),
                CodexUsageJson::parse);
    }
}
