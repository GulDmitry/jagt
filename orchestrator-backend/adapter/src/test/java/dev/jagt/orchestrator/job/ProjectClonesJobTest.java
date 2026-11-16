package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestClonedProjectBuilder.clonedProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectClonesJobTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @TempDir Path dir;

  private final ProcessRunner runner = new ProcessRunner();
  private final ConfigService config = mock(ConfigService.class);
  private final ReachLedger reach = new ReachLedger();
  private final ProjectClonesJob job = new ProjectClonesJob(config, runner, reach);
  private Path origin;

  @BeforeEach
  void anOriginHoldingOneCommit() throws Exception {
    origin = dir.resolve("origin.git");
    Path work = dir.resolve("work");
    runner.run(
        dir, TIMEOUT, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
    runner.run(dir, TIMEOUT, List.of("git", "clone", "-q", origin.toString(), work.toString()));
    Files.writeString(work.resolve("f.txt"), "base");
    runner.run(work, TIMEOUT, List.of("git", "add", "."));
    runner.run(
        work,
        TIMEOUT,
        List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
    runner.run(work, TIMEOUT, List.of("git", "push", "-q", "origin", "main"));
  }

  @Test
  void clonesTheUrlIntoTheFolderTheProjectIsPlacedAtWhereNothingStandsThere() {
    Path folder = dir.resolve("repos/api");
    ProjectConfig api =
        clonedProject().withUrl(origin.toString()).withPath(folder.toString()).build();
    when(config.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", api)));

    job.run();

    assertThat(folder.resolve("f.txt")).hasContent("base");
    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void clonesNothingWhereTheProjectsPathExists() throws Exception {
    Path existing = Files.createDirectories(dir.resolve("mine"));
    ProjectConfig api =
        clonedProject().withUrl(origin.toString()).withPath(existing.toString()).build();
    when(config.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", api)));

    job.run();

    assertThat(existing).isEmptyDirectory();
    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void marksTheBoardsHeadWithTheProjectAndCauseWhereTheUrlCannotBeClonedAndClonesTheOthers() {
    Path bad = dir.resolve("repos/bad");
    Path good = dir.resolve("repos/good");
    Map<String, ProjectConfig> both = new LinkedHashMap<>();
    both.put(
        "bad",
        clonedProject()
            .withUrl(dir.resolve("gone.git").toString())
            .withPath(bad.toString())
            .build());
    both.put("good", clonedProject().withUrl(origin.toString()).withPath(good.toString()).build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(both));

    job.run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> {
          assertThat(lost.host()).isEqualTo("bad");
          assertThat(lost.said()).isNotBlank();
        });
    assertThat(bad).doesNotExist();
    assertThat(good.resolve("f.txt")).exists();
  }

  @Test
  void clearsTheMarkWhenTheNextRunClones() {
    Path folder = dir.resolve("repos/api");
    Path later = dir.resolve("later.git");
    ProjectConfig api =
        clonedProject().withUrl(later.toString()).withPath(folder.toString()).build();
    when(config.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", api)));
    job.run();
    runner.run(
        dir, TIMEOUT, List.of("git", "clone", "-q", "--bare", origin.toString(), later.toString()));

    job.run();

    assertThat(reach.standing()).isEmpty();
    assertThat(folder.resolve("f.txt")).exists();
  }

  @Test
  void reportsCloningWhileOneProjectIsClonedAndNotBeforeNorAfter() {
    Processes processes = mock(Processes.class);
    ProjectClonesJob watched = new ProjectClonesJob(config, processes, reach);
    AtomicBoolean duringTheClone = new AtomicBoolean();
    when(processes.run(any(), any(), anyMap(), anyList()))
        .thenAnswer(call -> {
          duringTheClone.set(watched.cloning());
          return new Processes.Result(0, "", "");
        });
    ProjectConfig api =
        clonedProject()
            .withUrl(origin.toString())
            .withPath(dir.resolve("repos/api").toString())
            .build();
    when(config.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", api)));
    boolean before = watched.cloning();

    watched.run();

    assertThat(before).isFalse();
    assertThat(duringTheClone).isTrue();
    assertThat(watched.cloning()).isFalse();
  }

  @Test
  void reportsNotCloningWhereNoProjectIsMissing() throws Exception {
    Processes processes = mock(Processes.class);
    ProjectClonesJob watched = new ProjectClonesJob(config, processes, reach);
    AtomicBoolean duringTheRun = new AtomicBoolean(true);
    when(config.load())
        .thenAnswer(call -> {
          duringTheRun.set(watched.cloning());
          return ConfigService.ConfigFile.defaults();
        });

    watched.run();

    assertThat(duringTheRun).isFalse();
  }
}
