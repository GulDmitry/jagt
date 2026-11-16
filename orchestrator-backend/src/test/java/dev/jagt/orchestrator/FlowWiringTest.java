package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Capabilities;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowWiringTest {

  @Test
  void displacedCapabilityIsReportedAsTakeover() {
    TaskCapability builtIn = mock(TaskCapability.class);
    when(builtIn.action()).thenReturn(TaskAction.DEPLOY);
    TaskCapability own = mock(TaskCapability.class);
    when(own.action()).thenReturn(TaskAction.DEPLOY);
    when(own.priority()).thenReturn(10);

    Capabilities capabilities = new FlowWiring().capabilities(List.of(builtIn, own), List.of());

    assertThat(capabilities.takeovers()).hasSize(1);
  }
}
