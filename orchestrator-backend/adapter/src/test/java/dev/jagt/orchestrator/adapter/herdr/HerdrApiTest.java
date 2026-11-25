package dev.jagt.orchestrator.adapter.herdr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HerdrApiTest {

  private final Processes processes = mock(Processes.class);

  @Test
  void startsTheSessionServerWhereNoneListensAndWaitsUntilItAnswers() throws Exception {
    FakeHerdrWire herdr = new FakeHerdrWire().down();
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(),
            new HerdrProperties("/bin/herdr", "/state/herdr"),
            processes,
            herdr);
    doAnswer(
            invocation -> {
              herdr.up();
              return mock(Process.class);
            })
        .when(processes)
        .runDetached(any(), any());

    api.ensureServer("jagt");

    verify(processes)
        .runDetached(
            null,
            List.of("env", "XDG_CONFIG_HOME=/state", "/bin/herdr", "--session", "jagt", "server"));
    assertThat(herdr.requests()).extracting(FakeHerdrWire.Request::method).contains("ping");
  }

  @Test
  void raisesTheServersOwnCodeWhereItAnswersWithAnError() throws Exception {
    FakeHerdrWire herdr = new FakeHerdrWire().refusing("workspace.list", "pane_not_found");
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(),
            new HerdrProperties("/bin/herdr", "/state/herdr"),
            processes,
            herdr);

    assertThatThrownBy(() -> api.call("jagt", "workspace.list", Map.of()))
        .isInstanceOf(HerdrApi.Failed.class)
        .extracting(failure -> ((HerdrApi.Failed) failure).code())
        .isEqualTo("pane_not_found");
  }

  @Test
  void looksForTheSocketUnderTheMachinesHomeWhereNoHomeIsConfigured() throws Exception {
    FakeHerdrWire herdr = new FakeHerdrWire();
    HerdrApi api =
        new HerdrApi(FakeHost.machine(), new HerdrProperties("herdr", ""), processes, herdr);

    api.call("jagt", "ping", Map.of());

    assertThat(herdr.lastSocket())
        .isEqualTo(FakeHost.machine().home().resolve(".config/herdr/sessions/jagt/herdr.sock"));
  }

  @Test
  void looksForTheSocketUnderTheConfiguredHome() throws Exception {
    FakeHerdrWire herdr = new FakeHerdrWire();
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(), new HerdrProperties("herdr", "/state/herdr"), processes, herdr);

    api.call("jagt", "ping", Map.of());

    assertThat(herdr.lastSocket()).isEqualTo(Path.of("/state/herdr/sessions/jagt/herdr.sock"));
  }

  @Test
  void expandsLeadingTildeOfTheConfiguredHomeToTheMachinesHome() throws Exception {
    FakeHerdrWire herdr = new FakeHerdrWire();
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(), new HerdrProperties("herdr", "~/.config/herdr"), processes, herdr);

    api.call("jagt", "ping", Map.of());

    assertThat(herdr.lastSocket())
        .isEqualTo(FakeHost.machine().home().resolve(".config/herdr/sessions/jagt/herdr.sock"));
  }

  @Test
  void startsTheServerInTheExpandedHomeOfTheConfiguredTilde() throws Exception {
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(),
            new HerdrProperties("/bin/herdr", "~/.config/herdr"),
            processes,
            new FakeHerdrWire());

    assertThat(api.argv("jagt", "server"))
        .containsExactly(
            "env",
            "XDG_CONFIG_HOME=" + FakeHost.machine().home().resolve(".config"),
            "/bin/herdr",
            "--session",
            "jagt",
            "server");
  }

  @Test
  void addsNoEnvironmentWhereNoHomeIsConfigured() throws Exception {
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(),
            new HerdrProperties("/bin/herdr", ""),
            processes,
            new FakeHerdrWire());

    assertThat(api.argv("jagt", "server"))
        .containsExactly("/bin/herdr", "--session", "jagt", "server");
  }

  @Test
  void answersNoResultWhereNoServerListens() throws Exception {
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(),
            new HerdrProperties("/bin/herdr", "/state/herdr"),
            processes,
            new FakeHerdrWire().down());

    assertThat(api.tryCall("jagt", "ping", Map.of())).isEmpty();
  }

  @Test
  void refusesRequestsTheSchemaDoesNotAllow() throws Exception {
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(),
            new HerdrProperties("/bin/herdr", "/state/herdr"),
            processes,
            new FakeHerdrWire());

    assertThatThrownBy(() -> api.call("jagt", "pane.read", Map.of("pane_id", "w1:p1")))
        .isInstanceOf(HerdrApi.Failed.class)
        .extracting(failure -> ((HerdrApi.Failed) failure).code())
        .isEqualTo("invalid_request");
  }
}
