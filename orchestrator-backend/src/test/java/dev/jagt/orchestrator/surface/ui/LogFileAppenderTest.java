package dev.jagt.orchestrator.surface.ui;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogFileAppenderTest {

    @ParameterizedTest
    @CsvSource({"ROOT,FILE", "dev.jagt.orchestrator.job.MasterPress,MASTER"})
    void keepsAtMostOneArchiveRolledAtTenMegabytes(String logger, String appender, @TempDir Path directory)
            throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty("LOG_FILE", directory.resolve("jagt.log").toString());
        context.putProperty("MASTER_LOG", directory.resolve("master.log").toString());
        context.putObject(Environment.class.getName(), new MockEnvironment());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));

        RollingFileAppender<?> file = (RollingFileAppender<?>) context.getLogger(logger).getAppender(appender);
        assertThat(((FixedWindowRollingPolicy) file.getRollingPolicy()).getMaxIndex()).isEqualTo(1);
        assertThat(((SizeBasedTriggeringPolicy<?>) file.getTriggeringPolicy()).getMaxFileSize())
                .hasToString("10 MB");
    }
}
