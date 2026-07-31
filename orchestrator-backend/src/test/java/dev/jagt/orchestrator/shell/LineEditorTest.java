package dev.jagt.orchestrator.shell;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineEditorTest {

    private static MasterShell.LineEditor of(String text) {
        MasterShell.LineEditor e = new MasterShell.LineEditor();
        e.setText(text);
        return e;
    }

    @Test
    void insertsAtTheCursorNotJustTheEnd() {
        MasterShell.LineEditor e = of("abc");
        e.left();
        e.left();
        e.insert('X');

        assertThat(e.text()).isEqualTo("aXbc");
        assertThat(e.cursor()).isEqualTo(2);
    }

    @Test
    void homeThenTypingPrependsToTheLine() {
        MasterShell.LineEditor e = of("world");
        e.home();
        e.insert('h');
        e.insert('i');

        assertThat(e.text()).isEqualTo("hiworld");
        assertThat(e.cursor()).isEqualTo(2);
    }

    @Test
    void backspaceDeletesLeftOfCursorAndDeleteRemovesUnderIt() {
        MasterShell.LineEditor e = of("abcd");
        e.left();               // cursor between c and d
        e.backspace();          // removes c -> "abd"
        e.delete();             // removes d -> "ab"

        assertThat(e.text()).isEqualTo("ab");
        assertThat(e.cursor()).isEqualTo(2);
    }

    @Test
    void killToStartAndKillToEndClearEitherSideOfTheCursor() {
        MasterShell.LineEditor start = of("delete me here");
        start.end();
        start.killToStart();
        assertThat(start.text()).isEmpty();

        MasterShell.LineEditor end = of("keep this");
        end.home();
        end.right();
        end.right();
        end.right();
        end.right();            // cursor after "keep"
        end.killToEnd();
        assertThat(end.text()).isEqualTo("keep");
    }

    @Test
    void deleteWordBackRemovesThePreviousWordIncludingTrailingSpaces() {
        MasterShell.LineEditor e = of("do PAN-1 project ");
        e.deleteWordBack();

        assertThat(e.text()).isEqualTo("do PAN-1 ");
        assertThat(e.cursor()).isEqualTo(9);
    }

    @Test
    void wordLeftAndWordRightJumpOverWholeWords() {
        MasterShell.LineEditor e = of("alpha beta gamma");
        e.wordLeft();                        // to start of "gamma"
        assertThat(e.cursor()).isEqualTo(11);
        e.wordLeft();                        // to start of "beta"
        assertThat(e.cursor()).isEqualTo(6);
        e.wordRight();                       // to end of "beta"
        assertThat(e.cursor()).isEqualTo(10);
    }

    @Test
    void cursorMovesAreClampedToTheLineBounds() {
        MasterShell.LineEditor e = of("ab");
        e.left();
        e.left();
        e.left();                            // cannot go before 0
        assertThat(e.cursor()).isZero();
        e.end();
        e.right();                           // cannot go past the end
        assertThat(e.cursor()).isEqualTo(2);
    }

    @Test
    void setTextPlacesTheCursorAtTheEndAndClearResetsEverything() {
        MasterShell.LineEditor e = of("recalled command");
        assertThat(e.cursor()).isEqualTo("recalled command".length());

        e.clear();
        assertThat(e.text()).isEmpty();
        assertThat(e.cursor()).isZero();
    }
}
