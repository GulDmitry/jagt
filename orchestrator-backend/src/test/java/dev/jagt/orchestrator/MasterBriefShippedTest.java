package dev.jagt.orchestrator;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The startup refusal tells a human to copy a file, so the file has to be there under that name. */
class MasterBriefShippedTest {

    private static final Path SHIPPED = Path.of("../master-brief.md.dist");

    @Test
    void shipsTheBriefUnderTheNameTheRefusalTellsAHumanToCopy() {
        assertThat(Files.isRegularFile(SHIPPED)).describedAs("%s", SHIPPED).isTrue();
        assertThat(SHIPPED.getFileName().toString())
                .isEqualTo(MasterConfig.defaults().briefOrDefault() + ".dist");
    }

    @Test
    void keepsAHumansOwnBriefOutOfTheRepository() throws Exception {
        assertThat(Files.readString(Path.of("../.gitignore")))
                .contains(MasterConfig.defaults().briefOrDefault());
    }
}
