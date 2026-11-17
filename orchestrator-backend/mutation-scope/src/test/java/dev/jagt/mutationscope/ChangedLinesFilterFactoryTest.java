package dev.jagt.mutationscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.build.InterceptorParameters;
import org.pitest.mutationtest.build.MutationInterceptorFactory;
import org.pitest.mutationtest.engine.Location;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.plugin.FeatureSetting;
import org.pitest.plugin.ToggleStatus;

class ChangedLinesFilterFactoryTest {

  @TempDir Path dir;

  @Test
  void buildsFilterKeepingLinesOfTheFileTheFeatureNames() throws IOException {
    Path lines = dir.resolve("lines.txt");
    Files.writeString(lines, "dev/jagt/Foo.java:12,13\ndev/jagt/Bar.java:7\n");
    var setting =
        new FeatureSetting(
            "CHANGEDLINES", ToggleStatus.ACTIVATE, Map.of("lines", List.of(lines.toString())));
    var parameters = new InterceptorParameters(setting, null, null, null, null, null);
    MutationDetails onBar =
        new MutationDetails(
            new MutationIdentifier(
                Location.location(ClassName.fromString("dev.jagt.Bar"), "run", "()V"), 0, "M"),
            "Bar.java",
            "description",
            7,
            0);

    var result =
        new ChangedLinesFilterFactory()
            .createInterceptor(parameters)
            .intercept(List.of(onBar), null);

    assertThat(result).containsExactly(onBar);
  }

  @Test
  void failsWhenTheLinesFileIsMissing() {
    var setting =
        new FeatureSetting(
            "CHANGEDLINES",
            ToggleStatus.ACTIVATE,
            Map.of("lines", List.of(dir.resolve("absent.txt").toString())));
    var parameters = new InterceptorParameters(setting, null, null, null, null, null);

    assertThatThrownBy(() -> new ChangedLinesFilterFactory().createInterceptor(parameters))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void pitestFindsTheFactoryThroughItsServiceFile() {
    var found = ServiceLoader.load(MutationInterceptorFactory.class);

    assertThat(found).anyMatch(ChangedLinesFilterFactory.class::isInstance);
  }
}
