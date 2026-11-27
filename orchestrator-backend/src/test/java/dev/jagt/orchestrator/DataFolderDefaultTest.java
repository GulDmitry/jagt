package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class DataFolderDefaultTest {

  @ParameterizedTest
  @CsvSource({
    "orchestrator.default-data, /opt/j/data, /opt/j/data, /opt/j/data/logs/jagt-backend.log",
    "orchestrator.data, /srv/jagt, /srv/jagt, /srv/jagt/logs/jagt-backend.log",
    "orchestrator.root, /opt/j, /h/ann/.jagt/data, /h/ann/.jagt/data/logs/jagt-backend.log"
  })
  void putsDataAndLogsUnderTheHomeUnlessTheDataKeyNamesOne(
      String key, String given, String data, String log) throws Exception {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    for (PropertySource<?> source :
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))) {
      environment.getPropertySources().addLast(source);
    }
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("desktop", Map.of("user.home", "/h/ann", key, given)));

    assertThat(environment.getProperty("orchestrator.data")).isEqualTo(data);
    assertThat(environment.getProperty("logging.file.name")).isEqualTo(log);
    assertThat(environment.getProperty("logging.master.name"))
        .isEqualTo(data + "/logs/jagt-master.log");
  }
}
