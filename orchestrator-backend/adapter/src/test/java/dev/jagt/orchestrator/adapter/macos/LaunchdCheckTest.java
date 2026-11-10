package dev.jagt.orchestrator.adapter.macos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Problem;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaunchdCheckTest {

  private final Host host = mock(Host.class);

  @Test
  void refusesLaunchdOnLinux() {
    when(host.osName()).thenReturn("Linux");

    List<Problem> problems = new LaunchdCheck(host).problems();

    assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.supervisor");
  }

  @Test
  void refusesLaunchdWithoutItsControlTool() {
    when(host.osName()).thenReturn("Mac OS X");
    when(host.onPath("launchctl")).thenReturn(false);

    assertThat(new LaunchdCheck(host).problems()).hasSize(1);
  }

  @Test
  void acceptsLaunchdOnMacWithItsControlTool() {
    when(host.osName()).thenReturn("Mac OS X");
    when(host.onPath("launchctl")).thenReturn(true);

    assertThat(new LaunchdCheck(host).problems()).isEmpty();
  }
}
