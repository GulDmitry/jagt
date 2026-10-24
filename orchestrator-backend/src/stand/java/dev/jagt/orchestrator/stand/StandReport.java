package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class StandReport {

    private static final String PASSED = "passed";
    private static final String FAILED = "failed";

    private StandReport() {
    }

    public static void main(String[] args) throws IOException {
        Path report = Path.of(args[0]);
        List<String> lines = Files.isRegularFile(report) ? Files.readAllLines(report) : List.of();
        System.out.println(lines.isEmpty() ? "stand report: no run recorded" : String.join("\n", rates(lines)));
    }

    static synchronized void append(String run, boolean passed) {
        String file = System.getProperty("stand.report", "");
        if (file.isBlank()) {
            return;
        }
        try {
            Path report = Path.of(file);
            Files.createDirectories(report.toAbsolutePath().getParent());
            Files.writeString(report, run + " " + (passed ? PASSED : FAILED) + "\n", StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("stand report not written to " + file + ": " + e.getMessage());
        }
    }

    public static List<String> rates(List<String> lines) {
        Map<String, List<Boolean>> runs = lines.stream().filter(line -> !line.isBlank()).map(line -> line.split(" "))
                .collect(Collectors.groupingBy(words -> words[0], TreeMap::new,
                        Collectors.mapping(words -> words[1].equals(PASSED), Collectors.toList())));
        return runs.entrySet().stream().map(run -> {
            long passed = run.getValue().stream().filter(Boolean::booleanValue).count();
            return run.getKey() + ": " + passed + " of " + run.getValue().size() + " passed ("
                    + passed * 100 / run.getValue().size() + "%)";
        }).toList();
    }
}
