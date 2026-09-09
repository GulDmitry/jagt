package dev.jagt.orchestrator.surface.ui;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogFileAppenderTest {

    @Test
    void writesOneFileThatIsNeverRolledIntoAnArchive(@TempDir Path directory) throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty("LOG_FILE", directory.resolve("jagt.log").toString());
        context.putObject(Environment.class.getName(), new MockEnvironment());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));

        Appender<?> file = context.getLogger("ROOT").getAppender("FILE");
        assertThat(file).isInstanceOf(FileAppender.class).isNotInstanceOf(RollingFileAppender.class);
    }
}
