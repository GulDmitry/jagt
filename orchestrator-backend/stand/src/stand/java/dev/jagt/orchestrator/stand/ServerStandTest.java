package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerStandTest {

  private static final String OFF_REFERENCE =
      "budgets hold on the reference machine; -Pbudgets=false skips them";
  private static final String JSON = "application/json";
  private static final String TOOLS = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
  private static final String SCREEN = "/api/tasks/screen?task=fix-the-widget";

  private ServerRun server;

  @RegisterExtension final DynamicFailureWatch watch = new DynamicFailureWatch();

  @BeforeAll
  void startsJagtFromTheShippedServerStarter() throws IOException {
    server = ServerRun.start("server");
  }

  @AfterAll
  void removesEverythingTheRunMade() throws IOException {
    if (!watch.failed()) {
      server.passed();
    }
    server.close();
  }

  @TestFactory
  Stream<DynamicTest> theShippedStarterServesWhatItPromises() {
    return Stream.of(
        dynamicTest(
            "appStartsWithinTheBudgetWhenTheEngineStartsIt",
            () -> {
              assumeTrue(Boolean.getBoolean("stand.budgets"), OFF_REFERENCE); // reference host only
              String log = server.engine().log();

              double seconds =
                  Double.parseDouble(log.replaceAll("(?s).*process running for ([0-9.]+).*", "$1"));

              assertThat(Duration.ofMillis(Math.round(seconds * 1000)))
                  .isLessThanOrEqualTo(StandTimings.READY_BUDGET);
            }),
        dynamicTest(
            "stateAnswersWithinTheBudgetAtTheNinetyFifthPercentileOverFiftySequentialCalls",
            () -> {
              assumeTrue(Boolean.getBoolean("stand.budgets"), OFF_REFERENCE); // reference host only
              long p95Nanos =
                  IntStream.range(0, 50)
                      .mapToLong(call -> {
                        long begun = System.nanoTime();
                        server.board().status("/state");
                        return System.nanoTime() - begun;
                      })
                      .sorted()
                      .skip(47)
                      .findFirst()
                      .orElseThrow();

              assertThat(Duration.ofNanos(p95Nanos))
                  .isLessThanOrEqualTo(StandTimings.STATE_P95_BUDGET);
            }),
        dynamicTest(
            "startupAndStateP95AreRecordedWhateverTheBudgetsSay",
            () -> {
              String log = server.engine().log();
              double seconds =
                  Double.parseDouble(log.replaceAll("(?s).*process running for ([0-9.]+).*", "$1"));
              double medianP95Millis =
                  IntStream.range(0, 5)
                          .mapToLong(round ->
                              IntStream.range(0, 50)
                                  .mapToLong(call -> {
                                    long begun = System.nanoTime();
                                    server.board().status("/state");
                                    return System.nanoTime() - begun;
                                  })
                                  .sorted()
                                  .skip(47)
                                  .findFirst()
                                  .orElseThrow())
                          .sorted()
                          .skip(2)
                          .findFirst()
                          .orElseThrow()
                      / 1_000_000.0;

              StandTimings.standard()
                  .write(
                      Path.of(System.getProperty("stand.timings", "build/stand/timings.json")),
                      seconds,
                      medianP95Millis);

              assertThat(medianP95Millis).isPositive();
            }),
        dynamicTest(
            "taskReachesReviewWhenAgentWorksOnTheClonedProject",
            () -> {
              server.board().create("my-project", "fix the widget");

              await()
                  .atMost(Duration.ofMinutes(2))
                  .ignoreExceptions()
                  .untilAsserted(() ->
                      assertThat(
                              server
                                  .board()
                                  .state()
                                  .path("tasks")
                                  .path("fix-the-widget")
                                  .path("status")
                                  .asString())
                          .isEqualTo("REVIEW_PENDING"));
              assertThat(
                      server
                          .engine()
                          .exec(
                              "test",
                              "-d",
                              server.engine().data().resolve("projects/my-project/.git").toString())
                          .exit())
                  .isZero();
            }),
        dynamicTest(
            "diffShowsTheAgentsChangeWhenTaskIsInReview",
            () ->
                assertThat(server.board().text("/api/tasks/diff?task=fix-the-widget"))
                    .contains("+changed")),
        dynamicTest(
            "boardAndApiRefuseWhenNoLoginWasMade",
            () -> {
              int page = server.board().anonymously("/state", Map.of(), null);
              int api = server.board().anonymously("/api/jobs", Map.of(), null);

              assertThat(page).isEqualTo(302);
              assertThat(api).isEqualTo(401);
            }),
        dynamicTest(
            "boardAndApiAnswerWhenLoggedInByPassword",
            () -> {
              int state = server.board().status("/state");
              int api = server.board().status("/api/jobs");

              assertThat(state).isEqualTo(200);
              assertThat(api).isEqualTo(200);
            }),
        dynamicTest(
            "sessionTabShowsTheAgentAndOneLineReachesIt",
            () -> {
              await()
                  .atMost(Duration.ofSeconds(30))
                  .untilAsserted(
                      () -> assertThat(server.board().text(SCREEN)).contains("agent ready"));
              server.board().say("fix-the-widget", "hello agent");

              await()
                  .atMost(Duration.ofSeconds(30))
                  .untilAsserted(
                      () -> assertThat(server.board().text(SCREEN)).contains("got: hello agent"));
            }),
        dynamicTest(
            "stopHaltsWorkAndLiftResumesIt",
            () -> {
              String stopped = server.board().command("/api/commands/stop");
              String again = server.board().command("/api/commands/stop");
              String lifted = server.board().command("/api/commands/unstop");
              String liftedTwice = server.board().command("/api/commands/unstop");

              assertThat(stopped).contains("stopped:");
              assertThat(again).contains("already stopped");
              assertThat(lifted).contains("stop lifted");
              assertThat(liftedTwice).contains("was not stopped");
            }),
        dynamicTest(
            "mcpIsRefusedWithOnlyTheWorktreeHeaderFromOutsideTheEngine",
            () -> {
              int status =
                  server
                      .board()
                      .anonymously(
                          "/mcp",
                          Map.of(
                              "Content-Type",
                              JSON,
                              "X-Working-Directory",
                              server.engine().data().resolve("tasks/fix-the-widget").toString()),
                          TOOLS);

              assertThat(status).isEqualTo(401);
            }),
        dynamicTest(
            "mcpIsServedWithTheMasterTokenFromOutsideTheEngine",
            () -> {
              String token =
                  server
                      .engine()
                      .exec("cat", server.engine().data().resolve("jagt/master-token").toString())
                      .output()
                      .strip();

              int status =
                  server
                      .board()
                      .anonymously(
                          "/mcp", Map.of("Content-Type", JSON, "X-Jagt-Master", token), TOOLS);

              assertThat(status).isEqualTo(200);
            }),
        dynamicTest(
            "passwordHashFromTheFileSecretIsNotLogged",
            () -> {
              String hash = Files.readString(server.root().resolve("password-hash"));

              assertThat(server.engine().log()).doesNotContain(hash);
            }));
  }
}
