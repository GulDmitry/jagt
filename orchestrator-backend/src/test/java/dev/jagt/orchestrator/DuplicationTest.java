package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CPDReport;
import net.sourceforge.pmd.cpd.CpdAnalysis;
import net.sourceforge.pmd.cpd.Mark;
import net.sourceforge.pmd.lang.LanguageRegistry;
import org.junit.jupiter.api.Test;

class DuplicationTest {

  private static final int MINIMUM_TOKENS = 75;

  private static final List<Path> SKIPPED =
      Stream.of(
          "service/AgentSessionsTest.java",
          "service/AgentStatusReportsTest.java",
          "service/CaseDoorTest.java",
          "service/ConfigServiceTest.java",
          "service/OwnStatusReportsTest.java",
          "service/ReportEffectsTest.java",
          "service/RoundRecordTest.java",
          "service/SessionLedgerTest.java",
          "service/StateServiceTest.java",
          "service/TaskStopsTest.java",
          "service/TestStateServiceBuilder.java",
          "service/TestTaskStopsBuilder.java",
          "job/AcceptanceCheckJobTest.java",
          "job/AcceptanceOutcomesTest.java",
          "job/AcceptanceRoundFlowTest.java",
          "job/AutoReviewSchedulerTest.java",
          "job/ConflictStallTest.java",
          "job/DeployCheckJobAcceptanceTest.java",
          "job/DeployCheckJobTest.java",
          "job/DeployPipelinesRetryTest.java",
          "job/DeployPipelinesTest.java",
          "job/JobsTest.java",
          "job/MasterAnswerJobTest.java",
          "job/MasterDeployJobTest.java",
          "job/MasterPressTest.java",
          "job/MasterStopJobTest.java",
          "job/MasterVerdictsTest.java",
          "job/RestartAskJobTest.java",
          "job/StopTriageJobTest.java",
          "job/SessionBriefContractTest.java",
          "job/TicketCloseJobTest.java",
          "job/VerifyJobTest.java",
          "job/WatchdogServiceTest.java",
          "job/WorktreeOrphanScannerTest.java",
          "job/YourMoveJobTest.java")
          .map(name -> Path.of("usecase/src/test/java/dev/jagt/orchestrator", name))
          .toList();

  @Test
  void noBlockOfCodeIsCopiedElsewhere() throws IOException {
    CPDConfiguration configuration = new CPDConfiguration();
    configuration.setOnlyRecognizeLanguage(LanguageRegistry.CPD.getLanguageById("java"));
    configuration.setMinimumTileSize(MINIMUM_TOKENS);
    SourceRoots.javaFiles()
        .filter(file -> SKIPPED.stream().noneMatch(file::startsWith))
        .forEach(configuration::addInputPath);
    List<String> duplicates = new ArrayList<>();

    try (CpdAnalysis cpd = CpdAnalysis.create(configuration)) {
      cpd.performAnalysis(
          (CPDReport report) -> {
            assertThat(report.getProcessingErrors()).isEmpty();
            report
                .getMatches()
                .forEach(
                    match ->
                        duplicates.add(
                            match.getTokenCount()
                                + " tokens: "
                                + StreamSupport.stream(match.spliterator(), false)
                                    .map(DuplicationTest::place)
                                    .toList()));
          });
    }

    assertThat(String.join(System.lineSeparator(), duplicates)).isEmpty();
  }

  private static String place(Mark mark) {
    return mark.getLocation().getFileId().getFileName() + ":" + mark.getLocation().getStartLine();
  }
}
