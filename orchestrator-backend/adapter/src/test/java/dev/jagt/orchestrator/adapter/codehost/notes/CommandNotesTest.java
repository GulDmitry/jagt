package dev.jagt.orchestrator.adapter.codehost.notes;

import static dev.jagt.orchestrator.adapter.codehost.TestHostSettingsBuilder.hostSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommandNotesTest {

  private final Processes processes = mock(Processes.class);
  private final CommandNotes command = new CommandNotes(processes, new ReachLedger());
  private final HostSettings host =
      hostSettings()
          .withReview(
              new ProjectConfig.Review(
                  List.of("/bin/ci-awaiting", "{remote}"),
                  List.of("/bin/ci-change", "{request}"),
                  List.of("/bin/ci-post", "{request}", "{replyTo}"),
                  List.of()))
          .build();

  @Test
  void noAwaitingCommandMeansNoRequestAndNoFailure() {
    Answer<List<ReviewRequest>> answer =
        command.awaitingReview(hostSettings().build(), "git@h:a/b.git");

    assertThat(answer.facts()).contains(List.of());
    assertThat(answer.failure()).isEmpty();
  }

  @Test
  void requestsPrintedAsJsonAreRead() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"url\":\"https://h/a/b/7\",\"title\":\"Add cart\",\"author\":\"ada\","
                    + "\"headSha\":\"0123abc\"}]",
                ""));

    Answer<List<ReviewRequest>> answer = command.awaitingReview(host, "git@h:a/b.git");

    assertThat(answer.facts())
        .contains(List.of(new ReviewRequest("https://h/a/b/7", "Add cart", "ada", "0123abc")));
  }

  @Test
  void outputThatIsNotJsonIsFailureNamingTheKey() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "no json here", ""));

    Answer<RequestChange> answer = command.change(host, "https://h/a/b/7");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure())
        .isEqualTo("orchestrator.projects.shop.changeCommand printed output that is not JSON");
  }

  @Test
  void changeWithoutItsCommandIsFailureNamingTheKey() {
    Answer<RequestChange> answer = command.change(hostSettings().build(), "https://h/a/b/7");

    assertThat(answer.failure()).isEqualTo("orchestrator.projects.shop.changeCommand is not set");
  }

  @Test
  void noteTextIsTheLastArgumentAndNeverReadForPlaceholder() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "55\n", ""));

    Answer<String> answer =
        command.post(
            host, "https://h/a/b/7", new NoteDraft("see {request}", "21", NoteAnchor.NONE));

    ArgumentCaptor<List<String>> run = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), run.capture());
    assertThat(run.getValue())
        .containsExactly("/bin/ci-post", "https://h/a/b/7", "21", "see {request}");
    assertThat(answer.facts()).contains("55");
  }

  @Test
  void commandHostCannotSayReactionsAndThatIsNeverZero() {
    Answer<Map<String, Reaction>> answer = command.reactions(host, "https://h/a/b/7", List.of("1"));

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEmpty();
  }

  @Test
  void notesPrintedAsJsonCarryBodyAuthorAndResolution() {
    HostSettings noting =
        hostSettings().withNotesCommand(List.of("/bin/ci-notes", "{request}")).build();
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"id\":\"5\",\"thread\":\"T1\",\"author\":\"ada\",\"body\":\"Null here\","
                    + "\"path\":\"A.java\",\"resolved\":true,\"mine\":false}]",
                ""));

    Answer<List<RequestNote>> answer = command.notes(noting, "https://h/a/b/7");

    assertThat(answer.facts().get())
        .containsExactly(
            RequestNote.defaults()
                .withId("5")
                .withThread("T1")
                .withAuthor("ada")
                .withBody("Null here")
                .withPath("A.java")
                .withResolution(Resolution.RESOLVED));
  }

  @Test
  void notesPrintedAsIdsOnlyAreFailureNamingWhatIsCannotSay() {
    HostSettings noting =
        hostSettings().withNotesCommand(List.of("/bin/ci-notes", "{request}")).build();
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "5\n6\n", ""));

    Answer<List<RequestNote>> answer = command.notes(noting, "https://h/a/b/7");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).contains("prints ids only").contains("cannot-say");
  }

  @Test
  void changeNamesTheStateTheCommandPrinted() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"headSha\":\"a\",\"state\":\"merged\"}", ""));

    Answer<RequestChange> answer = command.change(host, "https://h/a/b/7");

    assertThat(answer.facts().get().state()).isEqualTo(RequestState.MERGED);
  }
}
