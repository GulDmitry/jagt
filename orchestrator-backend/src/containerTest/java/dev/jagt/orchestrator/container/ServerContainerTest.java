package dev.jagt.orchestrator.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServerContainerTest {

  private static final String SERVICE = "jagt";
  private static final String READINESS = "/actuator/health/readiness";
  private static final Duration READY_BUDGET = Duration.ofSeconds(15);
  private static final Duration STATE_P95_BUDGET = Duration.ofMillis(100);

  private final String password = UUID.randomUUID().toString();
  private final String passwordHash = new BCryptPasswordEncoder().encode(password);
  private ComposeContainer stack;
  private String base;
  private RestClient anonymous;
  private RestClient board;
  private int ssoBoardPort;
  private int ssoProviderPort;

  @BeforeAll
  void startsTheServerFromItsOwnComposeFile(@TempDir Path fixtures) throws Exception {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "docker is absent on this machine, so containerTest is skipped");
    int port;
    try (ServerSocket free = new ServerSocket(0)) {
      port = free.getLocalPort();
    }
    base = "http://localhost:" + port;
    try (ServerSocket free = new ServerSocket(0)) {
      ssoBoardPort = free.getLocalPort();
    }
    try (ServerSocket free = new ServerSocket(0)) {
      ssoProviderPort = free.getLocalPort();
    }
    Files.setPosixFilePermissions(fixtures, PosixFilePermissions.fromString("rwxr-xr-x"));
    Files.writeString(fixtures.resolve("password-hash"), passwordHash);
    Files.writeString(
        fixtures.resolve("env"),
        "JAGT_PUBLIC_URL="
            + base
            + "\nJAGT__orchestrator__projects__my-project__url=/fixtures/origin.git"
            + "\nJAGT__orchestrator__roles__master__mode=off\n");
    Files.writeString(
        fixtures.resolve("jagt.yml"),
        Files.readString(Path.of("..", "jagt.server.yml.dist"))
            .replace("${JAGT_PASSWORD_HASH}", "${file:/run/secrets/jagt_password_hash}"));
    int origin =
        new ProcessBuilder(
                "sh",
                "-c",
                "git init -q -b main work && echo hi > work/a.txt && git -C work add a.txt"
                    + " && git -C work -c user.name=fixture -c user.email=fixture@localhost"
                    + " commit -qm init && git clone -q --bare work origin.git")
            .directory(fixtures.toFile())
            .inheritIO()
            .start()
            .waitFor();
    assertThat(origin).isZero();
    stack =
        new ComposeContainer(
                Path.of("..", "docker-compose.yml").toFile(),
                Path.of("src", "containerTest", "compose", "compose.test.yml").toFile())
            .withEnv(
                Map.of(
                    "JAGT_PORT", String.valueOf(port),
                    "JAGT_SSO_PORT", String.valueOf(ssoBoardPort),
                    "JAGT_SSO_PROVIDER_PORT", String.valueOf(ssoProviderPort),
                    "JAGT_FIXTURES", fixtures.toAbsolutePath().toString(),
                    "JAGT_STUB",
                        Path.of("src", "containerTest", "compose").toAbsolutePath().toString()))
            .withBuild(true)
            .withPull(false)
            .withRemoveImages(ComposeContainer.RemoveImages.LOCAL);
    stack.start();
    anonymous =
        RestClient.builder()
            .baseUrl(base)
            .defaultHeader(HttpHeaders.ORIGIN, base)
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();
    await()
        .atMost(Duration.ofMinutes(3))
        .ignoreExceptions()
        .until(() ->
            anonymous
                .get()
                .uri(READINESS)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .is2xxSuccessful());
  }

  @AfterAll
  void removesEverythingTheRunMade() {
    Optional.ofNullable(stack).ifPresent(ComposeContainer::stop);
  }

  @BeforeEach
  void logsInByPassword() {
    board =
        RestClient.builder()
            .baseUrl(base)
            .defaultHeader(HttpHeaders.ORIGIN, base)
            .requestFactory(
                new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().cookieHandler(new CookieManager()).build()))
            .build();
    board
        .post()
        .uri("/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body("username=owner&password=" + password)
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  @Order(0)
  void appStartsWithinTheBudgetWhenTheImageStarts() {
    String log = stack.getContainerByServiceName(SERVICE).orElseThrow().getLogs();

    double processSeconds =
        Double.parseDouble(log.replaceAll("(?s).*process running for ([0-9.]+).*", "$1"));

    assertThat(Duration.ofMillis(Math.round(processSeconds * 1000)))
        .isLessThanOrEqualTo(READY_BUDGET);
  }

  @Test
  @Order(0)
  void stateAnswersWithinTheBudgetAtTheNinetyFifthPercentileWhenCalledFiftySequentialTimes() {
    long p95Nanos =
        IntStream.range(0, 50)
            .mapToLong(call -> {
              long begun = System.nanoTime();
              board.get().uri("/state").retrieve().toBodilessEntity();
              return System.nanoTime() - begun;
            })
            .sorted()
            .skip(47)
            .findFirst()
            .orElseThrow();

    assertThat(Duration.ofNanos(p95Nanos)).isLessThanOrEqualTo(STATE_P95_BUDGET);
  }

  @Test
  @Order(1)
  void taskReachesReviewWhenStubAgentWorksOnTheClonedProject() throws Exception {
    board
        .post()
        .uri("/api/tasks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("project", "my-project", "notes", "fix the widget"))
        .retrieve()
        .toBodilessEntity();

    await()
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(() ->
            assertThat(
                    JsonPath.<String>read(
                        board.get().uri("/state").retrieve().body(String.class),
                        "$.tasks['fix-the-widget'].status"))
                .isEqualTo("REVIEW_PENDING"));
    assertThat(
            stack
                .getContainerByServiceName(SERVICE)
                .orElseThrow()
                .execInContainer("test", "-d", "/data/projects/my-project/.git")
                .getExitCode())
        .isZero();
  }

  @Test
  @Order(2)
  void diffShowsTheStubsChangeWhenTaskIsInReview() {
    String diff =
        board.get().uri("/api/tasks/diff?task=fix-the-widget").retrieve().body(String.class);

    assertThat(diff).contains("+changed");
  }

  @Test
  @Order(3)
  void boardAndApiRefuseWhenNoLoginWasMade() {
    int page =
        anonymous
            .get()
            .uri("/state")
            .exchange((request, response) -> response.getStatusCode().value());
    int api =
        anonymous
            .get()
            .uri("/api/jobs")
            .exchange((request, response) -> response.getStatusCode().value());

    assertThat(page).isEqualTo(302);
    assertThat(api).isEqualTo(401);
  }

  @Test
  @Order(4)
  void boardAndApiAnswerWhenLoggedInByPassword() {
    int state = board.get().uri("/state").retrieve().toBodilessEntity().getStatusCode().value();
    int api = board.get().uri("/api/jobs").retrieve().toBodilessEntity().getStatusCode().value();

    assertThat(state).isEqualTo(200);
    assertThat(api).isEqualTo(200);
  }

  @Test
  @Order(5)
  void sessionTabShowsStubAndLineReachesIt() {
    String screen =
        board.get().uri("/api/tasks/screen?task=fix-the-widget").retrieve().body(String.class);
    board
        .post()
        .uri("/api/tasks/say?task=fix-the-widget")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("line", "hello stub"))
        .retrieve()
        .toBodilessEntity();

    assertThat(screen).contains("stub ready");
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() ->
            assertThat(
                    board
                        .get()
                        .uri("/api/tasks/screen?task=fix-the-widget")
                        .retrieve()
                        .body(String.class))
                .contains("got: hello stub"));
  }

  @Test
  @Order(6)
  void stopHaltsWorkAndLiftResumesIt() {
    String stopped = board.post().uri("/api/commands/stop").retrieve().body(String.class);
    String again = board.post().uri("/api/commands/stop").retrieve().body(String.class);
    String lifted = board.post().uri("/api/commands/unstop").retrieve().body(String.class);
    String liftedTwice = board.post().uri("/api/commands/unstop").retrieve().body(String.class);

    assertThat(stopped).contains("stopped:");
    assertThat(again).contains("already stopped");
    assertThat(lifted).contains("stop lifted");
    assertThat(liftedTwice).contains("was not stopped");
  }

  @Test
  @Order(7)
  void mcpIsRefusedWithOnlyTheWorktreeHeaderFromOutsideTheContainer() {
    int status =
        anonymous
            .post()
            .uri("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Working-Directory", "/data/tasks/fix-the-widget/worktrees/my-project")
            .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")
            .exchange((request, response) -> response.getStatusCode().value());

    assertThat(status).isEqualTo(401);
  }

  @Test
  @Order(8)
  void mcpIsServedWithTheMasterTokenFromOutsideTheContainer() {
    String token =
        stack
            .getContainerByServiceName(SERVICE)
            .orElseThrow()
            .copyFileFromContainer(
                "/data/jagt/master-token",
                in -> new String(in.readAllBytes(), StandardCharsets.UTF_8).strip());

    int status =
        anonymous
            .post()
            .uri("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Jagt-Master", token)
            .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")
            .exchange((request, response) -> response.getStatusCode().value());

    assertThat(status).isEqualTo(200);
  }

  @Test
  @Order(9)
  void readsTheJagtVariableAndTheFileSecretWithoutLoggingTheSecret() throws Exception {
    var jagt = stack.getContainerByServiceName(SERVICE).orElseThrow();

    String origin =
        jagt.execInContainer(
                "git", "-C", "/data/projects/my-project", "remote", "get-url", "origin")
            .getStdout()
            .strip();

    assertThat(origin).isEqualTo("/fixtures/origin.git");
    assertThat(jagt.getLogs()).doesNotContain(passwordHash);
  }

  @Test
  @Order(10)
  void taskAndCloneSurviveWhenContainerRestartsWithTheVolume() throws Exception {
    var jagt = stack.getContainerByServiceName(SERVICE).orElseThrow();
    jagt.execInContainer("touch", "/data/projects/my-project/.marker");

    jagt.getDockerClient().restartContainerCmd(jagt.getContainerId()).exec();
    await()
        .atMost(Duration.ofMinutes(3))
        .ignoreExceptions()
        .until(() ->
            anonymous
                .get()
                .uri(READINESS)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .is2xxSuccessful());
    board
        .post()
        .uri("/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body("username=owner&password=" + password)
        .retrieve()
        .toBodilessEntity();

    assertThat(
            JsonPath.<Map<String, Object>>read(
                board.get().uri("/state").retrieve().body(String.class), "$.tasks"))
        .containsKey("fix-the-widget");
    assertThat(
            jagt.execInContainer("test", "-f", "/data/projects/my-project/.marker").getExitCode())
        .isZero();
  }

  @Test
  @Order(11)
  void taskAndCloneAreGoneWhenContainerRestartsWithoutTheVolume() throws Exception {
    stack.stop();
    stack.start();
    var fresh = stack.getContainerByServiceName(SERVICE).orElseThrow();
    await()
        .atMost(Duration.ofMinutes(3))
        .ignoreExceptions()
        .until(() ->
            anonymous
                .get()
                .uri(READINESS)
                .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()));

    assertThat(
            fresh
                .execInContainer("grep", "-q", "fix-the-widget", "/data/jagt/state.json")
                .getExitCode())
        .isNotZero();
    assertThat(
            fresh.execInContainer("test", "-f", "/data/projects/my-project/.marker").getExitCode())
        .isNotZero();
  }

  @Test
  @Order(13)
  void stateHoldsTheTaskWithItsSessionGoneWhenContainerGetsSigterm() throws Exception {
    board
        .post()
        .uri("/api/tasks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("project", "my-project", "notes", "keep working"))
        .retrieve()
        .toBodilessEntity();
    await()
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(() ->
            assertThat(
                    JsonPath.<Number>read(
                            board.get().uri("/state").retrieve().body(String.class),
                            "$.tasks['keep-working'].session.movedAt")
                        .longValue())
                .isPositive());
    var jagt = stack.getContainerByServiceName(SERVICE).orElseThrow();

    jagt.getDockerClient().killContainerCmd(jagt.getContainerId()).withSignal("SIGTERM").exec();
    await().atMost(Duration.ofSeconds(30)).until(() -> !jagt.isRunning());
    String state =
        jagt.copyFileFromContainer(
            "/data/jagt/state.json", in -> new String(in.readAllBytes(), StandardCharsets.UTF_8));

    assertThat(JsonPath.<String>read(state, "$.tasks['keep-working'].session.halt.kind"))
        .isEqualTo("GONE");
  }

  @ParameterizedTest
  @Order(12)
  @CsvSource({"owner@example.com,302,200", "intruder,403,302"})
  void boardOpensOnlyForTheAllowedSubjectWhenLoggedInThroughTheProvider(
      String subject, int callbackStatus, int boardStatus) throws Exception {
    HttpClient browser =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    String sso = "http://localhost:" + ssoProviderPort;
    String ssoBoard = "http://localhost:" + ssoBoardPort;
    await()
        .atMost(Duration.ofMinutes(3))
        .ignoreExceptions()
        .until(() ->
            browser
                    .send(
                        HttpRequest.newBuilder(URI.create(ssoBoard + READINESS)).build(),
                        HttpResponse.BodyHandlers.discarding())
                    .statusCode()
                == 200);

    String authorize =
        browser
            .send(
                HttpRequest.newBuilder(URI.create(ssoBoard + "/oauth2/authorization/oidc")).build(),
                HttpResponse.BodyHandlers.discarding())
            .headers()
            .firstValue("Location")
            .orElseThrow()
            .replace("http://sso:8080", sso);
    String callback =
        browser
            .send(
                HttpRequest.newBuilder(URI.create(authorize))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=" + subject))
                    .build(),
                HttpResponse.BodyHandlers.discarding())
            .headers()
            .firstValue("Location")
            .orElseThrow();
    int callbackResult =
        browser
            .send(
                HttpRequest.newBuilder(URI.create(callback)).build(),
                HttpResponse.BodyHandlers.discarding())
            .statusCode();
    int boardResult =
        browser
            .send(
                HttpRequest.newBuilder(URI.create(ssoBoard + "/state")).build(),
                HttpResponse.BodyHandlers.discarding())
            .statusCode();

    assertThat(callbackResult).isEqualTo(callbackStatus);
    assertThat(boardResult).isEqualTo(boardStatus);
  }
}
