package dev.jagt.orchestrator;

import dev.jagt.orchestrator.task.MasterRight;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MasterRightsSpecTest {

    @Test
    void everyStepAHumanMayKeepIsNamedInAMineScenarioOfTheMasterSpec() throws IOException {
        String spec = Files.readString(Path.of("../openspec/specs/master/spec.md"));
        List<String> named = Pattern.compile("mine: \\[([^\\]]*)]").matcher(spec).results()
                .flatMap(list -> Pattern.compile("[a-z]+").matcher(list.group(1)).results())
                .map(MatchResult::group).distinct().toList();

        assertThat(named).containsAll(MasterRight.ids());
    }
}
