package dev.jagt.orchestrator;

import java.io.File;
import java.nio.file.Path;

public record TestInstallProcessBuilder(Path dir) {

  public static TestInstallProcessBuilder installIn(Path dir) {
    return new TestInstallProcessBuilder(dir);
  }

  public ProcessBuilder build() {
    ProcessBuilder install =
        new ProcessBuilder("sh", Path.of("../install.sh").toAbsolutePath().toString());
    install.environment().put("JAGT_VERSION", "1.2.3");
    install.environment().put("JAGT_HOME", dir.resolve("home").toString());
    install.environment().put("JAGT_BIN", dir.resolve("bin").toString());
    install
        .environment()
        .put("JAGT_INSTALL_FEED", dir.resolve("feed").toUri().toString().replaceAll("/$", ""));
    install
        .environment()
        .put("JAGT_INSTALL_JRE_FEED", dir.resolve("jre").toUri().toString().replaceAll("/$", ""));
    install
        .environment()
        .put("PATH", dir.resolve("ghbin") + File.pathSeparator + System.getenv("PATH"));
    install.redirectErrorStream(true);
    return install;
  }
}
