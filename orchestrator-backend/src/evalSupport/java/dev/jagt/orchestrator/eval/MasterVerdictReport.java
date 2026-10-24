package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import dev.jagt.orchestrator.service.master.MasterReview.Read;
import dev.jagt.orchestrator.service.master.MasterReview.Review;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MasterVerdictReport {

    private static final JsonMapper MAPPER = new JsonMapper();

    private MasterVerdictReport() {
    }

    public static String of(String expected, Path worktree) {
        Path stored = worktree.resolve(".jagt").resolve("master-review.json");
        if (!Files.isRegularFile(stored)) {
            return expected + "\nno master-review.json under " + worktree;
        }
        try {
            return describe(expected, MAPPER.readValue(stored.toFile(), Review.class));
        } catch (JacksonException unreadable) {
            return expected + "\nmaster-review.json unreadable: " + unreadable.getOriginalMessage();
        }
    }

    public static String describe(String expected, Review review) {
        List<String> lines = new ArrayList<>();
        lines.add("expected: " + expected);
        lines.add("stored verdict, round " + review.round() + ":");
        review.reads().forEach(read -> lines.addAll(role(read)));
        return String.join("\n", lines);
    }

    private static List<String> role(Read read) {
        Judgement said = read.judgement();
        List<String> lines = new ArrayList<>();
        if (!said.failure().isBlank()) {
            lines.add("- " + read.role() + ": could not read the round: " + said.failure());
            return lines;
        }
        lines.add("- " + read.role() + ": " + said.verdict());
        said.findings().forEach(finding -> lines.add("    " + finding(finding)));
        said.premises().forEach(premise -> lines.add("    " + premise(premise)));
        if (!said.question().isBlank()) {
            lines.add("    question: " + said.question());
        }
        return lines;
    }

    private static String finding(Finding finding) {
        return ("finding [" + finding.severity() + "] " + finding.file() + " " + finding.anchor()).strip()
                + " - " + finding.issue();
    }

    private static String premise(Premise premise) {
        return "premise: " + premise.claim()
                + (premise.provenBy().isBlank() ? " (reasoned only)" : " (proven by: " + premise.provenBy() + ")");
    }
}
