package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMarkerMigrationTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            awaiting: which database                                 | QUESTION   | which database
            awaiting permission: edits in the deploy worktree denied | PERMISSION | edits in the deploy worktree denied
            no changes: every comment already handled                | NO_CHANGES | every comment already handled
            outcome=no_changes: withdrawn thread relayed again       | NO_CHANGES | withdrawn thread relayed again
            outcome: no_changes — withdrawn thread relayed again     | NO_CHANGES | withdrawn thread relayed again
            outcome=question — keep v2?                              | QUESTION   | keep v2?
            no changesets were needed                                | PLAIN      | no changesets were needed
            """)
    void keepsWhatAPreviousVersionWroteIntoTheLineAsTheTasksReport(String message, AgentReport report, String line) {
        TaskState task = new JsonMapper().readValue(new JsonMapper().writeValueAsString(Map.of(
                "project", "proj", "worktreePath", "/wt", "status", "IN_PROGRESS", "lastActiveTimestamp", 0, "mrCreatedAt", 0,
                "lastPolledAt", 0, "message", message)), TaskState.class);

        assertThat(task.report()).isEqualTo(report);
        assertThat(task.message()).isEqualTo(line);
    }

    @Test
    void readsTheLineAsWrittenOnceTheFileCarriesItsReport() {
        TaskState task = new JsonMapper().readValue("""
                {"project":"proj","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":0,"mrCreatedAt":0,"lastPolledAt":0,
                 "message":"awaiting: a word the session chose","report":"PLAIN"}""", TaskState.class);

        assertThat(task.report()).isEqualTo(AgentReport.PLAIN);
        assertThat(task.message()).isEqualTo("awaiting: a word the session chose");
    }

    @Test
    void readsBackTheReportItWrote() {
        JsonMapper mapper = new JsonMapper();
        TaskState asked = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).message("which database")
                .report(AgentReport.QUESTION).build();

        TaskState read = mapper.readValue(mapper.writeValueAsString(asked), TaskState.class);

        assertThat(read.report()).isEqualTo(AgentReport.QUESTION);
        assertThat(read.message()).isEqualTo("which database");
    }
}
