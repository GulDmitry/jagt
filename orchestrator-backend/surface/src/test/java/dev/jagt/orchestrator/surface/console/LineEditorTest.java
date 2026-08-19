package dev.jagt.orchestrator.surface.console;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineEditorTest {

    @Test
    void typesIntoTheMiddleOfTheLineRatherThanAtItsEnd() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("abc");

        editor.left();
        editor.left();
        editor.insert('X');

        assertThat(editor.text()).isEqualTo("aXbc");
        assertThat(editor.cursor()).isEqualTo(2);
    }

    @Test
    void putsAVerbInFrontOfALineThatAlreadyHasItsArgument() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("world");

        editor.home();
        editor.insert('h');
        editor.insert('i');

        assertThat(editor.text()).isEqualTo("hiworld");
        assertThat(editor.cursor()).isEqualTo(2);
    }

    @Test
    void rubsOutTheCharacterBeforeTheCursor() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("abcd");
        editor.left();

        editor.backspace();

        assertThat(editor.text()).isEqualTo("abd");
        assertThat(editor.cursor()).isEqualTo(2);
    }

    @Test
    void rubsOutTheCharacterTheCursorSitsOn() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("abcd");
        editor.left();

        editor.delete();

        assertThat(editor.text()).isEqualTo("abc");
        assertThat(editor.cursor()).isEqualTo(3);
    }

    @Test
    void clearsEverythingTypedBeforeTheCursor() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("delete me here");
        editor.end();

        editor.killToStart();

        assertThat(editor.text()).isEmpty();
    }

    @Test
    void clearsTheRestOfTheLineAfterTheCursor() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("keep this");
        editor.home();
        editor.right();
        editor.right();
        editor.right();
        editor.right();

        editor.killToEnd();

        assertThat(editor.text()).isEqualTo("keep");
    }

    @Test
    void dropsTheLastWordTypedTogetherWithTheSpaceAfterIt() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("do ABC-1 project ");

        editor.deleteWordBack();

        assertThat(editor.text()).isEqualTo("do ABC-1 ");
        assertThat(editor.cursor()).isEqualTo(9);
    }

    @Test
    void jumpsBackWholeWordsInsteadOfOneCharacterAtATime() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("alpha beta gamma");

        editor.wordLeft();
        editor.wordLeft();

        assertThat(editor.cursor()).isEqualTo(6);
    }

    @Test
    void jumpsForwardToTheEndOfTheNextWord() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("alpha beta gamma");
        editor.home();

        editor.wordRight();

        assertThat(editor.cursor()).isEqualTo(5);
    }

    @Test
    void staysAtTheStartOfTheLineWhenTheCursorIsMovedFurtherLeft() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ab");

        editor.left();
        editor.left();
        editor.left();

        assertThat(editor.cursor()).isZero();
    }

    @Test
    void staysAtTheEndOfTheLineWhenTheCursorIsMovedFurtherRight() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ab");
        editor.end();

        editor.right();

        assertThat(editor.cursor()).isEqualTo(2);
    }

    @Test
    void leavesTheCursorReadyToTypeAtTheEndOfARecalledCommand() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();

        editor.setText("recalled command");

        assertThat(editor.cursor()).isEqualTo(16);
    }

    @Test
    void leavesAnEmptyPromptBehindWhenTheLineIsAbandoned() {
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("recalled command");

        editor.clear();

        assertThat(editor.text()).isEmpty();
        assertThat(editor.cursor()).isZero();
    }
}
