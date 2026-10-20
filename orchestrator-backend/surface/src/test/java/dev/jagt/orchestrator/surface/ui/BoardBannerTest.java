package dev.jagt.orchestrator.surface.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class BoardBannerTest {

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void tellsTheHumanWhereTheBoardListens(CapturedOutput output) {
        new BoardBanner("8290").run(null);

        assertThat(output.getOut()).contains("jagt board → http://localhost:8290   (Ctrl-C stops)");
    }
}
