package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SeamSelectionTest {

  private static final Pattern SELECTED =
      Pattern.compile("@ConditionalOnExpression\\(|@ConditionalOnProperty\\([^)]*orchestrator");
  private static final Set<String> RUN_TOGETHER =
      Set.of("StartupCheck", "ChannelDriver", "CodeHostCommands");

  @Test
  void selectsEveryAdapterOfPortWithSeveralAdaptersByAnOrchestratorKey() {
    AdapterPorts adapterPorts = new AdapterPorts();

    List<String> unselected =
        adapterPorts.adaptersByPort().entrySet().stream()
            .filter(port -> port.getValue().size() > 1 && !RUN_TOGETHER.contains(port.getKey()))
            .flatMap(port ->
                port.getValue().stream()
                    .filter(name ->
                        !SELECTED.matcher(adapterPorts.sourceOf(name)).find()
                            && !adapterPorts.wiredAsFallback(port.getKey(), name))
                    .map(name -> port.getKey() + " <- " + name))
            .sorted()
            .toList();

    assertThat(unselected).isEmpty();
  }
}
