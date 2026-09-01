package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnattendedLoopTest {

    private static final List<Path> UNATTENDED = List.of(
            Path.of("usecase/src/main/java/dev/jagt/orchestrator/service/ReviewSweepService.java"),
            Path.of("usecase/src/main/java/dev/jagt/orchestrator/service/AutoReviewScheduler.java"));

    @Test
    void theSweepNobodyAskedForHoldsNoCollaboratorThatShipsDeploysOrPushes() {
        List<String> writers = List.of("GitService", "CommandService", "FlowEngine", "Capabilities",
                "ShipService", "DeployService", "TaskProvisioning", "TaskLauncher", "TaskRetirement");

        assertThat(UNATTENDED.stream()
                .filter(path -> writers.stream()
                        .anyMatch(writer -> read(path).contains("private final " + writer + " ")))
                .map(Path::getFileName)).isEmpty();
    }

    @Test
    void theSweepNobodyAskedForMovesNoTaskAlongItsFlow() {
        assertThat(UNATTENDED.stream()
                .filter(path -> read(path).contains("withStatus"))
                .map(Path::getFileName)).isEmpty();
    }

    @Test
    void readsEveryFileItClaimsToCheck() {
        assertThat(UNATTENDED).allSatisfy(path -> assertThat(Files.isRegularFile(path))
                .describedAs("source file %s", path).isTrue());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
