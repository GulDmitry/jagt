package dev.jagt.orchestrator.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Violation;
import org.junit.jupiter.api.Test;

class TaskPressMessagesTest {

  @Test
  void acceptsVerbOnTask() {
    assertThat(new TaskPress("ABC-42", "done", "", "").violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void asksForTheTaskWhereTheVerbIsPressedOnNone() {
    assertThat(new TaskPress(" ", "done", "", "").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("taskId");
  }

  @Test
  void refusesVerbNoTaskActionNames() {
    assertThat(new TaskPress("ABC-42", "explode", "", "").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("verb");
  }

  @Test
  void asksForTheVerbWhereNoneIsGiven() {
    assertThat(new TaskPress("ABC-42", "", "", "").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("verb");
  }

  @Test
  void stripsTheVerbAndTheProjectAndKeepsNoneBlank() {
    TaskPress said = new TaskPress("ABC-42", " remove-repo ", " api ", "");

    assertThat(said.verb()).isEqualTo("remove-repo");
    assertThat(said.project()).isEqualTo("api");
    assertThat(new TaskPress("ABC-42", "done", " ", "").project()).isNull();
  }

  @Test
  void carriesTheLineApartFromTheProject() {
    TaskPress said = new TaskPress("ABC-42", "run", "", " ls -la ");

    assertThat(said.line()).isEqualTo("ls -la");
    assertThat(said.project()).isNull();
  }

  @Test
  void acceptsCommandWithOrWithoutWhatFollowsIt() {
    assertThat(new CommandPress("stats", "").violations(MessageContext.NONE)).isEmpty();
    assertThat(new CommandPress("stats", "").tail()).isEmpty();
    assertThat(new CommandPress("stats", " today ").tail()).isEqualTo("today");
  }

  @Test
  void asksForTheCommandWhereNoneIsGiven() {
    assertThat(new CommandPress(" ", "today").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("verb");
  }

  @Test
  void describesTheVerbsAndTheCommandToolTakes() {
    assertThat(TaskPress.schema("press").json()).contains("\"verb\"", "remove-repo", "taskId");
    assertThat(CommandPress.schema("run").json()).contains("\"verb\"", "\"tail\"");
  }
}
