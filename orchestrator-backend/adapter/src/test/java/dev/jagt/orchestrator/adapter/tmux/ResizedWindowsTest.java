package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.host.Processes;
import org.junit.jupiter.api.Test;

class ResizedWindowsTest {

  private final FakeProcesses processes =
      new FakeProcesses()
          .answering(new Processes.Result(0, "", ""))
          .answering(new Processes.Result(0, "100 30", ""), "display-message");
  private final ResizedWindows sizes =
      new ResizedWindows(processes, new TmuxReads(processes, ToolchainProperties.defaults()));

  @Test
  void givesTheWindowTheViewsSizeAndRecordsWhatItWas() {
    sizes.claim("@1", 150, 40);

    assertThat(processes.issuedContaining("display-message", "@1")).hasSize(1);
    assertThat(processes.issuedContaining("resize-window", "@1", "150", "40")).hasSize(1);
  }

  @Test
  void recordsTheOriginalSizeOnceHoweverManyViewsClaimTheWindow() {
    sizes.claim("@1", 150, 40);
    sizes.claim("@1", 90, 20);

    assertThat(processes.issuedContaining("display-message", "@1")).hasSize(1);
    assertThat(processes.issuedContaining("resize-window", "@1", "90", "20")).hasSize(1);
  }

  @Test
  void putsTheOriginalSizeBackAndDropsTheSizePolicyItSetWhenTheLastViewIsClosed() {
    sizes.claim("@1", 150, 40);

    sizes.release("@1");

    assertThat(processes.issuedContaining("resize-window", "@1", "100", "30")).hasSize(1);
    assertThat(processes.issuedContaining("set-option", "-w", "-u", "@1", "window-size"))
        .hasSize(1);
  }

  @Test
  void keepsTheViewsSizeWhileAnotherViewIsStillOpen() {
    sizes.claim("@1", 150, 40);
    sizes.claim("@1", 90, 20);

    sizes.release("@1");

    assertThat(processes.issuedContaining("resize-window", "@1", "100", "30")).isEmpty();
  }

  @Test
  void putsBackTheSizePolicyTheWindowHadOfItsOwn() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(0, "", ""))
            .answering(new Processes.Result(0, "100 30", ""), "display-message")
            .answering(new Processes.Result(0, "latest", ""), "show-options");
    ResizedWindows sizes =
        new ResizedWindows(processes, new TmuxReads(processes, ToolchainProperties.defaults()));
    sizes.claim("@1", 150, 40);

    sizes.release("@1");

    assertThat(processes.issuedContaining("set-option", "-w", "@1", "window-size", "latest"))
        .hasSize(1);
  }

  @Test
  void releasesNothingForWindowNoViewClaimed() {
    sizes.release("@7");

    assertThat(processes.issued()).isEmpty();
  }
}
