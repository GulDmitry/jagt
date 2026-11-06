package dev.jagt.orchestrator.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleConfigOriginsTest {

    private final ConfigService config = mock(ConfigService.class);

    @Test
    @ResourceLock(Resources.GLOBAL)
    void modelComesFromJagtYmlWhenItWritesOne(@TempDir Path root) throws Exception {
        install(root, null);
        when(config.load()).thenReturn(ConfigFile.defaults().withRoles(Map.of("qa",
                new RoleConfig(true, "opus", List.of()))));

        List<String> lines = logged(root);

        assertThat(lines).contains("qa model opus jagt.yml", "qa enabled true jagt.yml", "qa environments 0 jagt.yml");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void modelComesFromTheHumanCopyWhenJagtYmlWritesNone(@TempDir Path root) throws Exception {
        install(root, "model: haiku\n");
        when(config.load()).thenReturn(ConfigFile.defaults());

        List<String> lines = logged(root);

        assertThat(lines).contains("qa model haiku role.yml");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void modelComesFromTheShippedDistWhenNeitherWritesOne(@TempDir Path root) throws Exception {
        install(root, null);
        when(config.load()).thenReturn(ConfigFile.defaults());

        List<String> lines = logged(root);

        assertThat(lines).contains("qa model sonnet role.yml.dist");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void modelComesFromTheShippedDistWhenTheHumanCopyCannotBeRead(@TempDir Path root) throws Exception {
        install(root, "model: [haiku\n");
        when(config.load()).thenReturn(ConfigFile.defaults());

        List<String> lines = logged(root);

        assertThat(lines).contains("qa model sonnet role.yml.dist");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void enabledAndEnvironmentsAreDefaultsWhenJagtYmlWritesNone(@TempDir Path root) throws Exception {
        install(root, null);
        when(config.load()).thenReturn(ConfigFile.defaults());

        List<String> lines = logged(root);

        assertThat(lines).contains("qa enabled false default", "qa environments 0 default");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void everyValueIsDefaultedWhereNoJagtYmlExistsYet(@TempDir Path root) throws Exception {
        install(root, null);
        Files.delete(root.resolve("jagt.yml"));

        List<String> lines = logged(root);

        assertThat(lines).contains("qa enabled false default", "qa environments 0 default");
    }

    private static void install(Path root, String humanCopy) throws Exception {
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/role.yml.dist"), root.resolve("roles/qa/role.yml.dist"));
        Files.writeString(root.resolve("jagt.yml"), "");
        if (humanCopy != null) {
            Files.writeString(root.resolve("roles/qa/role.yml"), humanCopy);
        }
    }

    private List<String> logged(Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        RoleConfigOrigins origins = new RoleConfigOrigins(new InstalledRoles(paths), config, paths);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(RoleConfigOrigins.class);
        logger.addAppender(appender);
        try {
            origins.logAtStart();
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list).extracting(ILoggingEvent::getMessage).containsOnly("role config");
        return appender.list.stream().map(event -> event.getKeyValuePairs().stream()
                .map(pair -> String.valueOf(pair.value)).reduce((a, b) -> a + " " + b).orElse("")).toList();
    }
}
