package dev.jagt.orchestrator.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHostTest {

    @Test
    void findsTheJarTheProcessWasStartedFrom(@TempDir Path dir) throws IOException {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");

        assertThat(SystemHost.jarFromClassPath(jar.toString(), Files::isRegularFile)).isEqualTo(jar);
    }

    @Test
    void findsNoJarWhenSomethingElseIsOnTheClassPathToo(@TempDir Path dir) throws IOException {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");

        assertThat(SystemHost.jarFromClassPath(jar + File.pathSeparator + "other.jar", Files::isRegularFile))
                .isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"does-not-exist.jar", "classes"})
    void findsNoJarWhenTheClassPathNamesNoFile(String classPath) {
        assertThat(SystemHost.jarFromClassPath(classPath, Files::isRegularFile)).isNull();
    }
}
