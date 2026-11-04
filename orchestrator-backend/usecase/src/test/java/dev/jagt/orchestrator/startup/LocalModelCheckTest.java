package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.AssistantCallKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalModelCheckTest {

    @Test
    void asksNothingWhereNoEndpointIsSet() {
        LocalModelProperties local = LocalModelProperties.off().withKinds(List.of(AssistantCallKind.TICKET_READ));

        assertThat(new LocalModelCheck(local).problems()).isEmpty();
    }

    @Test
    void acceptsAnHttpEndpointWithItsModelServingCommandMapping() {
        LocalModelProperties local = LocalModelProperties.off().withEndpoint("http://127.0.0.1:8080/v1")
                .withModel("qwen");

        assertThat(new LocalModelCheck(local).problems()).isEmpty();
    }

    @Test
    void refusesAnEndpointThatIsNoHttpAddressNamingTheKey() {
        LocalModelProperties local = LocalModelProperties.off().withEndpoint("ftp://host/v1").withModel("qwen");

        assertThat(new LocalModelCheck(local).problems()).extracting(Problem::key)
                .containsExactly("orchestrator.assistant.local.endpoint");
    }

    @Test
    void refusesAnEndpointWithoutAModelNamingTheKey() {
        LocalModelProperties local = LocalModelProperties.off().withEndpoint("http://127.0.0.1:8080/v1");

        assertThat(new LocalModelCheck(local).problems()).extracting(Problem::key)
                .containsExactly("orchestrator.assistant.local.model");
    }

    @Test
    void refusesAKindThatReadsToolsNamingTheKey() {
        LocalModelProperties local = LocalModelProperties.off().withEndpoint("http://127.0.0.1:8080/v1")
                .withModel("qwen").withKinds(List.of(AssistantCallKind.COMMAND_MAP, AssistantCallKind.TICKET_READ));

        List<Problem> problems = new LocalModelCheck(local).problems();

        assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.assistant.local.kinds");
        assertThat(problems.get(0).text()).contains("TICKET_READ");
    }
}
