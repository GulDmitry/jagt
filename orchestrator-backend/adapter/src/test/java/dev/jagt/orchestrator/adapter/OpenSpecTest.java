package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Processes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenSpecTest {

  private final Host host = mock(Host.class);

  private static final List<String> VALIDATE =
      List.of(
          "/bin/openspec",
          "validate",
          "abc-42",
          "--type",
          "change",
          "--strict",
          "--no-interactive",
          "--json");
  private static final List<String> ARCHIVE = List.of("/bin/openspec", "archive", "abc-42", "-y");

  @Test
  void owesNothingWhereTheRepositoryKeepsNoSpecs(@TempDir Path worktree) {
    OpenSpec specs = new OpenSpec(mock(Processes.class), host);

    assertThat(specs.owed(worktree, "ABC-42")).isEmpty();
  }

  @Test
  void asksForAChangeWhereTheTaskOpenedNone(@TempDir Path worktree) throws IOException {
    Files.createDirectories(worktree.resolve("openspec/changes/archive"));
    OpenSpec specs = new OpenSpec(mock(Processes.class), host);

    assertThat(specs.owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("openspec new change abc-42", "openspec instructions specs --change abc-42");
  }

  @Test
  void owesNothingOnceAnEarlierRoundArchivedTheChange(@TempDir Path worktree) throws IOException {
    Files.createDirectories(worktree.resolve("openspec/changes/archive/2026-10-06-abc-42"));
    OpenSpec specs = new OpenSpec(mock(Processes.class), host);

    assertThat(specs.owed(worktree, "ABC-42")).isEmpty();
  }

  @Test
  void relaysWhatTheValidatorSaidAboutAnInvalidChange(@TempDir Path worktree) throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), eq(VALIDATE)))
        .thenReturn(
            new Processes.Result(
                1,
                """
                {"items":[{"valid":false,"issues":[{"level":"ERROR","path":"file","message":"Change must have at least one delta"}]}]}
                """,
                ""));

    assertThat(new OpenSpec(processes, host).owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("Change must have at least one delta");
  }

  @Test
  void refusesAHandBackWhoseDeltaArchiveWouldRefuse(@TempDir Path worktree) throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), eq(VALIDATE)))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"items":[{"valid":true,"issues":[{"level":"INFO","path":"board/spec.md",
                "message":"Archive would refuse this delta: board MODIFIED failed - not found"}]}]}
                """,
                ""));

    assertThat(new OpenSpec(processes, host).owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("INFO board/spec.md: Archive would refuse this delta");
  }

  @Test
  void refusesWithTheRawOutputWhereTheValidatorAnswersNoJson(@TempDir Path worktree)
      throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), eq(VALIDATE)))
        .thenReturn(new Processes.Result(1, "", "unknown option"));

    assertThat(new OpenSpec(processes, host).owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("unknown option");
  }

  @Test
  void refusesWithTheRawOutputWhereTheValidatorFailsWithoutNamingAnIssue(@TempDir Path worktree)
      throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), eq(VALIDATE)))
        .thenReturn(
            new Processes.Result(1, "{\"items\":[{\"valid\":false,\"issues\":[]}]}", "crashed"));

    assertThat(new OpenSpec(processes, host).owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("crashed");
  }

  @Test
  void refusesToFoldAChangeThatDoesNotValidate(@TempDir Path worktree) throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), eq(VALIDATE)))
        .thenReturn(
            new Processes.Result(
                1,
                """
                {"items":[{"valid":false,"issues":[{"level":"ERROR","path":"file","message":"Change must have at least one delta"}]}]}
                """,
                ""));

    assertThatThrownBy(() -> new OpenSpec(processes, host).fold(worktree, "ABC-42"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Change must have at least one delta");
    verify(processes, never()).run(any(), any(), anyMap(), eq(ARCHIVE));
  }

  @Test
  void foldsAValidChangeWithTelemetryOff(@TempDir Path worktree) throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"items":[{"valid":true,"issues":[]}]}
                """,
                ""));

    new OpenSpec(processes, host).fold(worktree, "ABC-42");

    verify(processes)
        .run(
            eq(worktree),
            any(Duration.class),
            argThat(env -> "0".equals(env.get("OPENSPEC_TELEMETRY"))),
            eq(ARCHIVE));
  }

  @Test
  void findsNodeBesideTheCliWhenJagtsPathLacksIt(@TempDir Path worktree) throws IOException {
    when(host.resolve("openspec")).thenReturn("/opt/homebrew/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"items":[{"valid":true,"issues":[]}]}
                """,
                ""));

    new OpenSpec(processes, host).fold(worktree, "ABC-42");

    verify(processes)
        .run(
            eq(worktree),
            any(Duration.class),
            argThat(env -> env.get("PATH").startsWith("/opt/homebrew/bin")),
            eq(List.of("/opt/homebrew/bin/openspec", "archive", "abc-42", "-y")));
  }

  @Test
  void keepsJagtsOwnPathAfterTheClisDirectory(@TempDir Path worktree) throws IOException {
    when(host.resolve("openspec")).thenReturn("/opt/homebrew/bin/openspec");
    when(host.env("PATH")).thenReturn(Optional.of("/usr/bin"));
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"items":[{"valid":true,"issues":[]}]}
                """,
                ""));

    new OpenSpec(processes, host).fold(worktree, "ABC-42");

    verify(processes, atLeastOnce())
        .run(
            eq(worktree),
            any(Duration.class),
            argThat(env -> "/opt/homebrew/bin:/usr/bin".equals(env.get("PATH"))),
            any());
  }

  @Test
  void asksForAChangeWhenOnlyAnotherTasksChangeIsOpen(@TempDir Path worktree) throws IOException {
    Files.createDirectories(worktree.resolve("openspec/changes/xyz-7"));
    OpenSpec specs = new OpenSpec(mock(Processes.class), host);

    assertThat(specs.owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("openspec new change abc-42");
  }

  @Test
  void asksForAChangeWhenOnlyAnotherTasksChangeWasArchived(@TempDir Path worktree)
      throws IOException {
    Files.createDirectories(worktree.resolve("openspec/changes/archive/2026-10-06-xyz-7"));
    OpenSpec specs = new OpenSpec(mock(Processes.class), host);

    assertThat(specs.owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("openspec new change abc-42");
  }

  @Test
  void asksForAChangeWhenTheOnlyEntryNamedAfterTheTaskIsAFile(@TempDir Path worktree)
      throws IOException {
    Files.createDirectories(worktree.resolve("openspec/changes"));
    Files.writeString(worktree.resolve("openspec/changes/abc-42"), "notes");
    OpenSpec specs = new OpenSpec(mock(Processes.class), host);

    assertThat(specs.owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("openspec new change abc-42");
  }

  @Test
  void validatesALaterRoundsChangeOpenedUnderTheTasksNameWithASuffix(@TempDir Path worktree)
      throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42-2"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), any()))
        .thenReturn(
            new Processes.Result(
                1,
                """
                {"items":[{"valid":false,"issues":[{"level":"ERROR","path":"file","message":"Change must have at least one delta"}]}]}
                """,
                ""));

    assertThat(new OpenSpec(processes, host).owed(worktree, "ABC-42"))
        .get()
        .asString()
        .contains("openspec/changes/abc-42-2 does not validate");
  }

  @Test
  void saysItCouldNotReadTheChangesDirectoryWhereItsPermissionsRefuseTheListing(
      @TempDir Path worktree) throws IOException {
    Path changes = Files.createDirectories(worktree.resolve("openspec/changes"));
    OpenSpec specs = new OpenSpec(mock(Processes.class), host);
    Files.setPosixFilePermissions(changes, java.util.Set.of());
    try {
      assertThat(specs.owed(worktree, "ABC-42"))
          .get()
          .asString()
          .startsWith("could not read openspec/changes");
    } finally {
      Files.setPosixFilePermissions(
          changes, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
  }
}
