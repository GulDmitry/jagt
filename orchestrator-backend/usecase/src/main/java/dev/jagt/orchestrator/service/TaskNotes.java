package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** What a hand-back owes the next session: notes rewritten this round, small enough to reread every turn. */
final class TaskNotes {

    /** About 4k tokens: twice the 1–2k a condensed hand-off takes, for a task that runs for months. */
    static final int MAX_CHARS = 16_000;

    static final int MAX_AGENT_FILE_LINES = 3;

    private static final String AGENT_FILE_LINE = "agent-file:";

    private TaskNotes() {
    }

    static Optional<String> owed(Path worktree) {
        Path notes = worktree.resolve(Artifact.NOTES.fileName());
        Path round = worktree.resolve(Artifact.CONTEXT.fileName());
        try {
            if (!Files.isRegularFile(notes)) {
                return Optional.of("write " + Artifact.NOTES.fileName() + " before handing the round back");
            }
            long size = Files.readString(notes).length();
            if (size > MAX_CHARS) {
                return Optional.of(Artifact.NOTES.fileName() + " is " + size + " characters, at most " + MAX_CHARS
                        + ": compress it, keeping every fact");
            }
            if (Files.isRegularFile(round)
                    && Files.getLastModifiedTime(notes).compareTo(Files.getLastModifiedTime(round)) < 0) {
                return Optional.of(Artifact.NOTES.fileName() + " predates this round's "
                        + Artifact.CONTEXT.fileName() + ": rewrite it for this round");
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of("could not read " + Artifact.NOTES.fileName() + ": " + e.getMessage());
        }
    }

    static Optional<String> agentFileOwed(Path worktree, int added) {
        if (added > MAX_AGENT_FILE_LINES) {
            return Optional.of("this task adds " + added + " lines to the project's agent file, at most "
                    + MAX_AGENT_FILE_LINES + ": keep the gaps the next task would hit");
        }
        try {
            long named = Files.readAllLines(worktree.resolve(Artifact.NOTES.fileName())).stream()
                    .filter(line -> line.strip().startsWith(AGENT_FILE_LINE)).count();
            return named >= added ? Optional.empty()
                    : Optional.of(Artifact.NOTES.fileName() + " names the gap for " + named + " of the " + added
                            + " lines this task added to the project's agent file: one `" + AGENT_FILE_LINE
                            + "` line each");
        } catch (IOException e) {
            return Optional.of("could not read " + Artifact.NOTES.fileName() + ": " + e.getMessage());
        }
    }
}
