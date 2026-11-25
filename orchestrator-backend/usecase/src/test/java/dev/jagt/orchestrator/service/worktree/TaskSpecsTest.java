package dev.jagt.orchestrator.service.worktree;

import static dev.jagt.orchestrator.service.TestConfigServiceBuilder.configService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.worktree.Specs;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskSpecsTest {

  private static final String SPECS_IN_DOCS =
      """
      orchestrator:
        projects:
          api:
            path: /repo/api
            baseBranch: origin/main
            specs: {path: specs/openspec, project: docs}
          docs: {path: /repo/docs, baseBranch: origin/main}
      """;

  @TempDir Path root;

  Specs specs = mock(Specs.class);

  @Test
  void readsTheSpecRootInTheWorktreeOfTheProjectItNames() throws Exception {
    ConfigService config =
        configService().withRoot(root).withYaml(SPECS_IN_DOCS).build(mock(AgentRuntime.class));
    TaskState task =
        TaskState.builder("api", "/wt/api", TaskStatus.IN_PROGRESS)
            .repos(List.of(TaskRepo.of("api", "/wt/api"), TaskRepo.of("docs", "/wt/docs")))
            .build();
    when(specs.owed(Path.of("/wt/docs"), "specs/openspec", "ABC-42"))
        .thenReturn(Optional.of("specs/openspec/changes/ABC-42-login does not validate"));

    Optional<String> owed = new TaskSpecs(config, specs).owed("ABC-42", task);

    assertThat(owed).contains("[docs] specs/openspec/changes/ABC-42-login does not validate");
  }

  @Test
  void readsTheRepositorysOwnSpecsWhereItsProjectNamesNone() throws Exception {
    ConfigService config =
        configService()
            .withRoot(root)
            .withYaml(
                """
                orchestrator:
                  projects:
                    api: {path: /repo/api, baseBranch: origin/main}
                """)
            .build(mock(AgentRuntime.class));
    TaskState task = TaskState.builder("api", "/wt/api", TaskStatus.IN_PROGRESS).build();
    when(specs.owed(Path.of("/wt/api"), null, "ABC-42")).thenReturn(Optional.of("no change"));

    Optional<String> owed = new TaskSpecs(config, specs).owed("ABC-42", task);

    assertThat(owed).contains("[api] no change");
  }

  @Test
  void asksForTheSpecProjectWhereTheTaskHoldsNoWorktreeOfIt() throws Exception {
    ConfigService config =
        configService().withRoot(root).withYaml(SPECS_IN_DOCS).build(mock(AgentRuntime.class));
    TaskState task = TaskState.builder("api", "/wt/api", TaskStatus.IN_PROGRESS).build();

    Optional<String> owed = new TaskSpecs(config, specs).owed("ABC-42", task);

    assertThat(owed).get().asString().contains("docs at specs/openspec", "needsProject: docs");
  }

  @Test
  void refusesToFoldWhereTheTaskHoldsNoWorktreeOfTheSpecProject() throws Exception {
    ConfigService config =
        configService().withRoot(root).withYaml(SPECS_IN_DOCS).build(mock(AgentRuntime.class));
    TaskState task = TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING).build();

    assertThatThrownBy(() -> new TaskSpecs(config, specs).fold("ABC-42", task))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("needsProject: docs");
  }

  @Test
  void foldsTheSpecRootInTheWorktreeOfTheProjectItNames() throws Exception {
    ConfigService config =
        configService().withRoot(root).withYaml(SPECS_IN_DOCS).build(mock(AgentRuntime.class));
    TaskState task =
        TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING)
            .repos(List.of(TaskRepo.of("api", "/wt/api"), TaskRepo.of("docs", "/wt/docs")))
            .build();

    new TaskSpecs(config, specs).fold("ABC-42", task);

    verify(specs).fold(Path.of("/wt/docs"), "specs/openspec", "ABC-42");
  }
}
