package dev.jagt.orchestrator.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalCommandsTest {

    private final GlobalCommand spend = mock(GlobalCommand.class);
    private final GlobalCommand master = mock(GlobalCommand.class);

    @Test
    void findsACommandByItsVerbWhateverTheCaseAndPadding() {
        when(spend.id()).thenReturn("Spend");
        GlobalCommands commands = new GlobalCommands(List.of(spend));

        assertThat(commands.byId("  sPEND ")).containsSame(spend);
    }

    @Test
    void takesAnUnknownWordForFreeTextNotForTheNearestVerb() {
        when(spend.id()).thenReturn("spend");
        GlobalCommands commands = new GlobalCommands(List.of(spend));

        assertThat(commands.byId("spent")).isEmpty();
    }

    @Test
    void findsNoCommandForAMissingVerb() {
        when(spend.id()).thenReturn("spend");
        GlobalCommands commands = new GlobalCommands(List.of(spend));

        assertThat(commands.byId(null)).isEmpty();
    }

    @Test
    void listsEveryCommandInTheOrderOfItsVerb() {
        when(spend.id()).thenReturn("spend");
        when(master.id()).thenReturn("master");

        assertThat(new GlobalCommands(List.of(spend, master)).all()).containsExactly(master, spend);
    }

    @Test
    void refusesToStartWhereTwoCommandsDeclareTheSameVerb() {
        when(spend.id()).thenReturn("spend");
        when(master.id()).thenReturn("SPEND");

        assertThatThrownBy(() -> new GlobalCommands(List.of(spend, master)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Two commands declare the verb");
    }
}
