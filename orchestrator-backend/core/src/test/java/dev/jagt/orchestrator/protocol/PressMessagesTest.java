package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PressMessagesTest {

  @Test
  void acceptsAVerbOnATask() {
    assertThat(new TaskPress("ABC-42", "done", null).violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void asksForTheTaskWhereTheVerbIsPressedOnNone() {
    assertThat(new TaskPress(" ", "done", null).violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("taskId");
  }

  @Test
  void refusesAVerbNoTaskActionNames() {
    assertThat(new TaskPress("ABC-42", "explode", null).violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("verb");
  }

  @Test
  void asksForTheVerbWhereNoneIsGiven() {
    assertThat(new TaskPress("ABC-42", null, null).violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("verb");
  }

  @Test
  void stripsTheVerbAndTheProjectAndKeepsNoneBlank() {
    TaskPress said = new TaskPress("ABC-42", " remove-repo ", " api ");

    assertThat(said.verb()).isEqualTo("remove-repo");
    assertThat(said.project()).isEqualTo("api");
    assertThat(new TaskPress("ABC-42", "done", " ").project()).isNull();
  }

  @Test
  void acceptsACommandWithOrWithoutWhatFollowsIt() {
    assertThat(new CommandPress("stats", null).violations(MessageContext.NONE)).isEmpty();
    assertThat(new CommandPress("stats", null).tail()).isEmpty();
    assertThat(new CommandPress("stats", " today ").tail()).isEqualTo("today");
  }

  @Test
  void asksForTheCommandWhereNoneIsGiven() {
    assertThat(new CommandPress(" ", "today").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("verb");
  }

  @Test
  void describesTheVerbsAndTheCommandAToolTakes() {
    assertThat(TaskPress.schema("press").json()).contains("\"verb\"", "remove-repo", "taskId");
    assertThat(CommandPress.schema("run").json()).contains("\"verb\"", "\"tail\"");
  }
}
