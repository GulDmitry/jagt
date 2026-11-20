package dev.jagt.orchestrator.config.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShippedFilesTest {

  @TempDir Path jar;
  @TempDir Path root;

  @Test
  void placesShippedFileWhereTheRootHasNone() throws IOException {
    Files.createDirectories(jar.resolve("shipped/roles/session"));
    Files.writeString(jar.resolve("shipped/index.txt"), "roles/session/brief.md.dist\n");
    Files.writeString(jar.resolve("shipped/roles/session/brief.md.dist"), "shipped text");
    ClassLoader classes = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);

    ShippedFiles.placeInto(root, classes);

    assertThat(root.resolve("roles/session/brief.md.dist")).hasContent("shipped text");
  }

  @Test
  void writesShippedFileAgainWhenTheRootHoldsAnOlderOne() throws IOException {
    Files.createDirectories(jar.resolve("shipped/roles/session"));
    Files.writeString(jar.resolve("shipped/index.txt"), "roles/session/brief.md.dist\n");
    Files.writeString(jar.resolve("shipped/roles/session/brief.md.dist"), "new text");
    ClassLoader classes = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md.dist"), "old text");

    ShippedFiles.placeInto(root, classes);

    assertThat(root.resolve("roles/session/brief.md.dist")).hasContent("new text");
  }

  @Test
  void leavesTheHumansCopyBesideShippedFileAsItIs() throws IOException {
    Files.createDirectories(jar.resolve("shipped/roles/session"));
    Files.writeString(jar.resolve("shipped/index.txt"), "roles/session/brief.md.dist\n");
    Files.writeString(jar.resolve("shipped/roles/session/brief.md.dist"), "shipped text");
    ClassLoader classes = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "mine");

    ShippedFiles.placeInto(root, classes);

    assertThat(root.resolve("roles/session/brief.md")).hasContent("mine");
  }

  @Test
  void placesNothingWhenTheJarCarriesNoIndex() {
    ClassLoader none = new URLClassLoader(new URL[0], null);

    assertThatCode(() -> ShippedFiles.placeInto(root, none)).doesNotThrowAnyException();
    assertThat(root).isEmptyDirectory();
  }
}
