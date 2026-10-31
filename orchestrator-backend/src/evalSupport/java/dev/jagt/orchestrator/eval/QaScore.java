package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.QaVerdict.Status;

import java.util.Locale;
import java.util.Map;

public record QaScore(int rows, int correct, int expectedMet, int falseNotMet, int falseMet, int judgedUnprovable,
                      int expectedUnprovable, boolean reportMatches, boolean endingMatches, boolean namesCited) {

    public static QaScore of(QaCase expected, QaJudgement judged) {
        int correct = 0;
        int expectedMet = 0;
        int falseNotMet = 0;
        int falseMet = 0;
        int judgedUnprovable = 0;
        int expectedUnprovable = 0;
        for (Map.Entry<String, Status> row : expected.rows().entrySet()) {
            Status want = row.getValue();
            Status got = judged.rows().get(row.getKey());
            correct += want == got ? 1 : 0;
            expectedMet += want == Status.MET ? 1 : 0;
            expectedUnprovable += want == Status.UNPROVABLE ? 1 : 0;
            judgedUnprovable += got == Status.UNPROVABLE ? 1 : 0;
            falseNotMet += want == Status.MET && got == Status.NOT_MET ? 1 : 0;
            falseMet += want != Status.MET && got == Status.MET ? 1 : 0;
        }
        return new QaScore(expected.rows().size(), correct, expectedMet, falseNotMet, falseMet, judgedUnprovable,
                expectedUnprovable, expected.report().equals(judged.report()),
                expected.expectedEnding() == judged.ending(), cited(expected, judged));
    }

    private static boolean cited(QaCase expected, QaJudgement judged) {
        boolean namesCited = expected.names().stream().allMatch(name -> anyOf(name, judged.cited()));
        boolean probeNamed = expected.probe().isEmpty()
                || judged.cited().contains(expected.probe().substring(expected.probe().indexOf(' ') + 1)
                .toLowerCase(Locale.ROOT));
        return namesCited && probeNamed;
    }

    private static boolean anyOf(String alternatives, String cited) {
        for (String alternative : alternatives.split("\\|")) {
            if (cited.contains(alternative.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public boolean passed() {
        return falseMet == 0 && correct == rows && reportMatches && endingMatches && namesCited;
    }

    public String describe() {
        return "rows " + correct + "/" + rows + ", false met " + falseMet + ", false not met " + falseNotMet
                + ", report " + reportMatches + ", ending " + endingMatches + ", names cited " + namesCited;
    }
}
