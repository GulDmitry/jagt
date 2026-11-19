package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallScriptTest {

  private static final String JAR_SHA256 =
      "1d4d0c9bbf8c4a4c0f4f1c5c9d1b1a64b1d8b1c1c8f0d7c1d0f1c6d8c9c6a1f2";

  @Test
  void installsReleaseAndLauncherRunsBundledJavaWhenChecksumsMatch(@TempDir Path dir)
      throws Exception {
    byte[] jar = "fake jar".getBytes(StandardCharsets.UTF_8);
    Path release = Files.createDirectories(dir.resolve("feed/download/v1.2.3"));
    Files.write(release.resolve("jagt-1.2.3.jar"), jar);
    Files.writeString(
        release.resolve("jagt-1.2.3.sha256"),
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar))
            + "  jagt-1.2.3.jar\nabc  jagt-1.2.3.cdx.json\n");
    Path jre = Files.createDirectories(dir.resolve("jre/linux/x64/jre/hotspot/normal"));
    Path src = Files.createDirectories(dir.resolve("src/jdk-25-jre/bin"));
    Files.writeString(src.resolve("java"), "#!/bin/sh\necho fake-java-25 \"$@\"\n");
    src.resolve("java").toFile().setExecutable(true);
    Process tar =
        new ProcessBuilder(
                "tar",
                "-czf",
                jre.resolve("eclipse").toString(),
                "-C",
                dir.resolve("src").toString(),
                "jdk-25-jre")
            .start();
    assertThat(tar.waitFor(30, TimeUnit.SECONDS)).isTrue();
    Files.writeString(
        jre.resolve("eclipse.sha256.txt"),
        HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(jre.resolve("eclipse"))))
            + "  OpenJDK25U-jre.tar.gz\n");
    Files.createSymbolicLink(dir.resolve("jre/linux/aarch64"), Path.of("x64"));
    Files.createSymbolicLink(dir.resolve("jre/mac"), Path.of("linux"));
    ProcessBuilder install = TestInstallProcessBuilder.installIn(dir).build();

    Process installed = install.start();
    String installLog =
        new String(installed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(installed.waitFor(60, TimeUnit.SECONDS)).as(installLog).isTrue();
    assertThat(installed.exitValue()).as(installLog).isZero();

    ProcessBuilder launcher = new ProcessBuilder(dir.resolve("bin/jagt").toString(), "-version");
    launcher.redirectErrorStream(true);
    Process launched = launcher.start();
    String launchOutput =
        new String(launched.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(launched.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(launchOutput).contains("fake-java-25").contains("-version");
    assertThat(dir.resolve("home/versions/1.2.3/jagt.jar")).hasBinaryContent(jar);
    assertThat(dir.resolve("home/current/jagt.jar")).exists();
  }

  @Test
  void installsNothingWhenJarChecksumMismatches(@TempDir Path dir) throws Exception {
    Path release = Files.createDirectories(dir.resolve("feed/download/v1.2.3"));
    Files.writeString(release.resolve("jagt-1.2.3.jar"), "fake jar");
    Files.writeString(release.resolve("jagt-1.2.3.sha256"), JAR_SHA256 + "  jagt-1.2.3.jar\n");
    ProcessBuilder install = TestInstallProcessBuilder.installIn(dir).build();

    Process installed = install.start();
    String installLog =
        new String(installed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(installed.waitFor(60, TimeUnit.SECONDS)).isTrue();

    assertThat(installed.exitValue()).isNotZero();
    assertThat(installLog).contains("checksum mismatch");
    assertThat(dir.resolve("home")).doesNotExist();
    assertThat(dir.resolve("bin/jagt")).doesNotExist();
  }
}
