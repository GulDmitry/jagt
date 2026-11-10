package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskNameTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ABC-42",
        "feature/ABC-42-facet-options",
        "hotfix/but/whatever",
        "_under",
        "release/2.1",
        "wip/ä-ünicode",
        "x"
      })
  void acceptsEveryNameGitAcceptsAsBranch(String name) {
    assertThat(TaskName.unusableReason(name)).isNull();
  }

  @ParameterizedTest
  @CsvSource(
      quoteCharacter = '"',
      value = {
        "\"a b\", \"' ' is not allowed\"",
        "\"a~b\", \"'~' is not allowed\"",
        "\"a:b\", \"':' is not allowed\"",
        "\"-lead\", \"it starts with '-'\"",
        "\"feature/\", \"it starts or ends with '/'\"",
        "\"feature//x\", \"it has an empty part ('//')\"",
        "\"feature/x.\", \"it ends with '.'\"",
        "\"../escape\", \"'..' is not allowed\"",
        "\"feature/.hidden\", \"a part of it starts with '.'\"",
        "\"feature/x.lock\", \"a part of it ends with '.lock'\"",
        "\"x@{1}\", \"'@{' is not allowed\"",
        "\"HEAD\", \"git reserves that name\"",
      })
  void namesTheOnePartGitWouldRefuse(String name, String reason) {
    assertThat(TaskName.unusableReason(name)).isEqualTo(reason);
  }

  @Test
  void escapesControlCharacterInsteadOfSplicingItIntoTheAnswer() {
    assertThat(TaskName.unusableReason("wip\tfix")).isEqualTo("'\\u" + "0009' is not allowed");
  }

  @Test
  void refusesNameTooLongForOneDirectoryComponent() {
    assertThat(TaskName.unusableReason("x".repeat(181)))
        .isEqualTo("it is longer than 180 characters");
  }

  @ParameterizedTest
  @CsvSource({
    "hotfix/but/whatever, hotfix-but-whatever",
    "ABC-42, ABC-42",
    "release/2.1, release-2-1",
    "wip/o'brien, wip-o-brien"
  })
  void flattensTaskIntoOneFilesystemName(String name, String slug) {
    assertThat(TaskName.slug(name)).isEqualTo(slug);
  }

  @Test
  void namesBranchAfterTheWordsHumanOpenedTheTaskWith() {
    assertThat(TaskName.from("Split the invoice mailer in two"))
        .isEqualTo("split-the-invoice-mailer-in-two");
  }

  @Test
  void cutsLongOpeningLineAtWholeWord() {
    assertThat(TaskName.from("rewrite the invoice mailer so that it stops retrying forever"))
        .isEqualTo("rewrite-the-invoice-mailer-so-that-it");
  }

  @Test
  void namesNothingForTaskWhoseOpeningLineWasNeverGiven() {
    assertThat(TaskName.from(null)).isNull();
  }

  @Test
  void namesNothingWhenTheLineCarriesNoWordBranchCanUse() {
    assertThat(TaskName.from("!!! ???")).isNull();
  }

  @Test
  void keepsAnOpeningLineOfExactlyTheWrittenLengthWhole() {
    assertThat(TaskName.from("abcdefgh abcdefgh abcdefgh abcdefgh abcd"))
        .isEqualTo("abcdefgh-abcdefgh-abcdefgh-abcdefgh-abcd");
  }

  @Test
  void cutsLongOpeningWordWhereItHasNoWholeWordToCutAt() {
    assertThat(TaskName.from("x".repeat(50))).isEqualTo("x".repeat(40));
  }

  @Test
  void refusesToRequireNameGitWouldRefuse() {
    assertThatThrownBy(() -> TaskName.require("a b", "task"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Argument 'task'")
        .hasMessageContaining("' ' is not allowed")
        .hasMessageContaining("got: a b");
  }

  @Test
  void acceptsRequiredNameGitAccepts() {
    assertThatCode(() -> TaskName.require("ABC-42", "task")).doesNotThrowAnyException();
  }

  @Test
  void namesAnEmptyNameAsEmpty() {
    assertThat(TaskName.unusableReason("")).isEqualTo("it is empty");
  }
}
