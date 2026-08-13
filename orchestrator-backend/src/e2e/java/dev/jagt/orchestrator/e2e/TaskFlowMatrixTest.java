package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import dev.jagt.orchestrator.platform.TerminalDriver;
import dev.jagt.orchestrator.platform.UserNotifier;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.shell.MasterShell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end task flow, once per {@link TaskFlowCase} — real git worktrees, real tmux windows, real state
 * transitions, with the model replaced by the scripted {@code stub} runtime so the expected result is exact
 * rather than plausible. This is the skeleton the matrix grows on: {@code ship}/{@code review}/{@code deploy}
 * are NOT covered yet (they need a fake {@code CodeHost} and a scripted agent that reports back), and the
 * missing rows are named in {@link TaskFlowCase}.
 *
 * <p>Not part of {@code ./gradlew test}: it needs git + tmux and writes worktrees. Run it with
 * {@code ./gradlew e2eTest}. It leaves no trace — throwaway origin/clone under a temp dir, a throwaway tmux
 * session killed afterwards, the task's leftover branch deleted between cases, and every GUI-touching driver
 * replaced by a double.
 */
@Tag("e2e")
@SpringBootTest(properties = {"orchestrator.agent=stub", "orchestrator.open-warp-window=false"})
class TaskFlowMatrixTest {

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> workspace.resolve("root").toString());
        registry.add("orchestrator.config-file", () -> workspace.resolve("root/config.json").toString());
        registry.add("orchestrator.state-file", () -> workspace.resolve("root/state.json").toString());
    }

    /** Both would act on the DEVELOPER's machine: the shell takes over the terminal, the cleaner rewrites the IDE's state. */
    @MockitoBean
    private MasterShell masterShell;
    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;
    /** The headless doubles the oracle needs: a window, an editor or a notification cannot be asserted. */
    @MockitoBean
    private TerminalDriver terminalDriver;
    @MockitoBean
    private EditorDriver editorDriver;
    @MockitoBean
    private UserNotifier userNotifier;

    @Autowired
    private OrchestratorTools tools;
    @Autowired
    private StateService stateService;
    @Autowired
    private OrchestratorPaths paths;
    @Autowired
    private OrchestratorProperties properties;

    @BeforeAll
    static void createTheThrowawayOutsideWorld() throws Exception {
        E2eWorkspace.createRootMarker(workspace.resolve("root"));
        E2eWorkspace.createRepositoryWithOrigin(workspace.resolve("origin.git"), workspace.resolve("proj"));
    }

    @AfterEach
    void leaveNothingBehindForTheNextCombination() throws Exception {
        // `done` keeps the branch by design, so the next combination would hit "branch already exists".
        E2eWorkspace.git(workspace.resolve("proj"), "branch", "-D", "ABC-1");
        E2eWorkspace.killTmuxSessions(properties.tmuxCommand());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("dev.jagt.orchestrator.e2e.TaskFlowCase#matrix")
    void aTaskIsCreatedProvisionedAndThenTornDownLeavingOnlyItsBranch(TaskFlowCase flowCase) throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), workspace.resolve("proj"), flowCase);
        Path worktree = workspace.resolve("ABC-1-proj");

        String created = tools.initializeTask(NewTask.builder("ABC-1", "proj")
                .instructions("Fix the widget").title("Widget layout is off").build());

        assertThat(created).contains("Stub sub-agent started");
        assertThat(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE)).exists();
        assertThat(worktree.resolve("task_context.md")).exists();
        assertThat(worktree.resolve("mcp_client.js")).exists();
        assertThat(worktree.resolve(".mcp.json")).doesNotExist();
        assertThat(worktree.resolve(".claude")).doesNotExist();
        assertThat(stateService.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.NEW);
        assertThat(stateService.task("ABC-1").orElseThrow().autoReview()).isEqualTo(flowCase.autoReview());
        assertThat(E2eWorkspace.git(workspace.resolve("proj"), "branch", "--list", "ABC-1")).contains("ABC-1");

        tools.removeTask("ABC-1", null);

        assertThat(worktree).doesNotExist();
        assertThat(stateService.task("ABC-1")).isEmpty();
        assertThat(E2eWorkspace.git(workspace.resolve("proj"), "branch", "--list", "ABC-1")).contains("ABC-1");
    }
}
