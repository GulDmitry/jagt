package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPropertiesTest {

    @Test
    void shipsTheCheapModelSoNobodySilentlyPaysTheDefaultModelPriceOnEveryRead() throws IOException {
        var yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst();

        var shipped = new Binder(ConfigurationPropertySources.from(yaml))
                .bind("orchestrator.assistant", AssistantProperties.class).get();

        assertThat(shipped.model()).isEqualTo("haiku");
    }

    @Test
    void shipsUserScopedSettingsBecauseProjectScopeAloneLoadsNoMcpFromTheTempDir() throws IOException {
        var yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst();

        var shipped = new Binder(ConfigurationPropertySources.from(yaml))
                .bind("orchestrator.assistant", AssistantProperties.class).get();

        assertThat(shipped.settingSources()).isEqualTo("user,project,local");
    }
}
