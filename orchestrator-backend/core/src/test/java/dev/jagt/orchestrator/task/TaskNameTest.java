package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TaskNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"ABC-42", "feature/PAN-2489-facet-options", "hotfix/but/whatever", "_under",
            "release/2.1", "wip/ä-ünicode", "x"})
    void acceptsEveryNameGitAcceptsAsABranch(String name) {
        assertThat(TaskName.unusableReason(name)).isNull();
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
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
    void escapesAControlCharacterInsteadOfSplicingItIntoTheAnswer() {
        assertThat(TaskName.unusableReason("wip\tfix")).isEqualTo("'\\u0009' is not allowed");
    }

    @Test
    void refusesANameTooLongForOneDirectoryComponent() {
        assertThat(TaskName.unusableReason("x".repeat(181))).isEqualTo("it is longer than 180 characters");
    }

    @ParameterizedTest
    @CsvSource({"hotfix/but/whatever, hotfix-but-whatever", "ABC-42, ABC-42", "release/2.1, release-2-1",
            "wip/o'brien, wip-o-brien"})
    void flattensATaskIntoOneFilesystemName(String name, String slug) {
        assertThat(TaskName.slug(name)).isEqualTo(slug);
    }
}
