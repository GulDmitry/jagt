package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.assistant.TicketReader;
import dev.jagt.orchestrator.surface.mcp.McpController;
import dev.jagt.orchestrator.surface.mcp.McpProtocolService;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.Step;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.config.import=",
      "orchestrator.terminal=none",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false"
    })
class WholeApplicationWiringTest {

  @MockitoBean private UserNotifier userNotifier;

  @MockitoBean private EditorDriver editorDriver;

  @TempDir static Path root;

  private final RequestMappingHandlerMapping mappings;

  private final ApplicationContext context;

  private final GlobalCommands globals;

  @Autowired
  WholeApplicationWiringTest(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
      ApplicationContext context,
      GlobalCommands globals) {
    this.mappings = mappings;
    this.context = context;
    this.globals = globals;
  }

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry)
      throws IOException {
    ShippedRoot.configure(registry, root);
  }

  @Test
  void theApplicationStartsWithEveryBeanWiredSoStartupBreakFailsHereAndNotOnLaunch() {
    assertThat(context.getBean(McpController.class)).isNotNull();
    assertThat(context.getBean(GlobalCommands.class)).isNotNull();
    assertThat(context.getBean(TicketReader.class)).isNotNull();
  }

  @Test
  void readsTheMacKeychainAndSkipsTheLinuxSandboxCheckOnTheDefaultPlatform() {
    assertThat(context.containsBean("claudeKeychain")).isTrue();
    assertThat(context.containsBean("claudeSandboxCheck")).isFalse();
  }

  @Test
  void keepsTheDevelopersOwnStateFileOutOfBootedContext() {
    assertThat(context.getBean(OrchestratorPaths.class).stateFile().toString())
        .startsWith(root.toString());
  }

  @Test
  void everyCommandIsRunnableByTheMasterOrSaysWhyItIsNot() {
    assertThat(globals.all())
        .allSatisfy(command ->
            assertThat(command.whyNotTheArbiter().map(String::isBlank).orElse(false))
                .as(command.id())
                .isFalse());
  }

  @Test
  void everyApiRouteTheBackendMapsIsInTheContract() throws IOException {
    Map<String, Object> documented =
        new Yaml().load(Files.readString(Path.of("../contracts/openapi.yaml")));
    Map<?, ?> paths = (Map<?, ?>) Objects.requireNonNull(documented.get("paths"));
    Set<String> contract =
        paths.entrySet().stream()
            .flatMap(path ->
                Stream.of("get", "post", "put", "delete", "patch")
                    .filter(((Map<?, ?>) path.getValue())::containsKey)
                    .map(method ->
                        (method.toUpperCase(Locale.ROOT) + " " + path.getKey())
                            .replaceAll("\\{[^}]+}", "{}")))
            .collect(Collectors.toSet());

    List<String> missing =
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(info ->
                Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues().stream()
                    .filter(pattern -> pattern.startsWith("/api/"))
                    .filter(pattern -> !pattern.startsWith("/api/agent/"))
                    .flatMap(pattern ->
                        info.getMethodsCondition().getMethods().stream()
                            .map(method ->
                                (method.name() + " " + pattern).replaceAll("\\{[^}]+}", "{}"))))
            .filter(route -> !contract.contains(route))
            .sorted()
            .toList();

    assertThat(missing).as("routes missing from contracts/openapi.yaml").isEmpty();
  }

  @Test
  void everyApiRouteNamesItsHttpMethod() {
    List<String> methodless =
        mappings.getHandlerMethods().keySet().stream()
            .filter(info -> info.getMethodsCondition().isEmpty())
            .flatMap(info ->
                Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues().stream())
            .filter(pattern -> pattern.startsWith("/api/"))
            .filter(pattern -> !pattern.startsWith("/api/agent/"))
            .toList();

    assertThat(methodless).as("routes the contract cannot list by method").isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"master,14", "sub-agent,5"})
  void theToolsListAnswerOfEachScopeStaysUnderItsKilobyteBudget(String scope, int budgetKb) {
    ObjectMapper mapper = context.getBean(ObjectMapper.class);
    StateService state = mock(StateService.class);
    when(state.findByWorktree("/work/ABC-1"))
        .thenReturn(
            Optional.of(
                Map.entry(
                    "ABC-1",
                    TaskState.builder("proj", "/work/ABC-1", TaskStatus.IN_PROGRESS).build())));
    McpProtocolService protocol =
        new McpProtocolService(
            mapper,
            state,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            List.copyOf(context.getBeansOfType(McpTools.class).values()));

    String answer =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"),
                "master".equals(scope) ? null : "/work/ABC-1",
                "master".equals(scope))
            .orElseThrow()
            .path("result")
            .toString();

    assertThat(answer.getBytes(StandardCharsets.UTF_8).length)
        .as("tools/list bytes for %s", scope)
        .isLessThan(budgetKb * 1024);
  }

  @Test
  void theSubagentLaunchInstructionStaysUnderItsKilobyteBudget() {
    SessionBrief brief = context.getBean(SessionBrief.class);

    String launch = brief.line(Step.START) + brief.section(Step.CONTEXT).orElse("");

    assertThat(launch.getBytes(StandardCharsets.UTF_8).length)
        .as("launch instruction bytes")
        .isLessThan(9 * 1024);
  }
}
