package dev.jagt.orchestrator.config.home;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The files the jar carries under {@code shipped/}, listed in {@code shipped/index.txt}. Each is
 * jagt's own: written again when it differs, so an upgrade brings its new text. A file of the
 * human's, which has no place in the index, is never touched.
 */
public final class ShippedFiles {

  private static final String FOLDER = "shipped/";

  private ShippedFiles() {}

  public static void placeInto(Path root, ClassLoader classes) {
    try (InputStream index = classes.getResourceAsStream(FOLDER + "index.txt")) {
      if (index == null) {
        return;
      }
      Arrays.stream(new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
          .filter(name -> !name.isBlank())
          .forEach(name -> place(root, classes, name.strip()));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the files the jar carries", e);
    }
  }

  private static void place(Path root, ClassLoader classes, String name) {
    try (InputStream in = classes.getResourceAsStream(FOLDER + name)) {
      if (in == null) {
        throw new IllegalStateException("The jar's index names " + name + ", which it lacks");
      }
      byte[] shipped = in.readAllBytes();
      Path target = root.resolve(name);
      if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), shipped)) {
        return;
      }
      Files.createDirectories(target.getParent());
      Files.write(target, shipped);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot place " + name + " in " + root, e);
    }
  }
}
