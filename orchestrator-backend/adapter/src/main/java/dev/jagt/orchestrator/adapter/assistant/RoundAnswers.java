package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class RoundAnswers {

    private RoundAnswers() {
    }

    static Answer<Judgement> judged(HeadlessReply reply) {
        JsonNode envelope = reply.envelope();
        if (envelope == null) {
            return new Answer<>(Optional.of(Judgement.failed("the review failed: " + reply.cause())), reply.usage());
        }
        JsonNode answer = envelope.path("structured_output");
        return new Answer<>(Optional.of(answer.isObject() ? judgement(answer)
                : Judgement.failed("the review answered nothing readable")), reply.usage());
    }

    private static Judgement judgement(JsonNode answer) {
        List<Finding> findings = new ArrayList<>();
        answer.path("findings").forEach(f -> findings.add(new Finding(f.path("file").asString(""),
                f.path("issue").asString(""), f.path("pattern").asString(""),
                f.path("severity").asString("").strip().toLowerCase(Locale.ROOT),
                f.path("anchor").asString(""), f.path("kind").asString(""))));
        List<Premise> premises = new ArrayList<>();
        answer.path("premises").forEach(p -> premises.add(new Premise(p.path("claim").asString(""),
                p.path("provenBy").asString(""))));
        return new Judgement(answer.path("failure").asString(""), answer.path("verdict").asString(""),
                findings, answer.path("question").asString(""), premises, answer.path("newTask").asString(""));
    }
}
