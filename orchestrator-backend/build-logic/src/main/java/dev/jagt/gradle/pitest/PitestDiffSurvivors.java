package dev.jagt.gradle.pitest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Writes the standing mutants on the changed lines; unchanged inputs keep the stored file. */
public abstract class PitestDiffSurvivors extends DefaultTask {

  private static final Set<String> STANDING = Set.of("SURVIVED", "NO_COVERAGE");

  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getChangedLines();

  @Classpath
  public abstract ConfigurableFileCollection getClasses();

  @InputFiles
  @PathSensitive(PathSensitivity.NONE)
  public abstract ConfigurableFileCollection getReports();

  @OutputFile
  public abstract RegularFileProperty getSurvivors();

  @TaskAction
  void judge() throws IOException {
    Map<String, Set<Integer>> lines = changedLines(getChangedLines().get().getAsFile().toPath());
    Map<String, Set<String>> outcomes = new HashMap<>();
    Set<String> mutated = new HashSet<>();
    for (File report : getReports().getFiles()) {
      if (report.exists()) {
        collect(report, lines, outcomes, mutated);
      }
    }
    Set<String> standing = new TreeSet<>();
    outcomes.forEach(
        (mutant, statuses) -> {
          if (STANDING.containsAll(statuses)) {
            standing.add(mutant);
          }
        });
    List<String> out = new ArrayList<>();
    if (standing.isEmpty()) {
      out.add("pitestDiff: no mutant survives on a changed line");
    } else {
      out.add("pitestDiff: judge each before the commit");
      out.addAll(standing);
    }
    for (String source : new TreeSet<>(lines.keySet())) {
      if (!mutated.contains(source)) {
        out.add("pitestDiff: no mutant generated on " + source);
      }
    }
    Files.write(getSurvivors().get().getAsFile().toPath(), out);
  }

  private static Map<String, Set<Integer>> changedLines(Path file) throws IOException {
    Map<String, Set<Integer>> lines = new HashMap<>();
    for (String line : Files.readAllLines(file)) {
      int colon = line.lastIndexOf(':');
      if (colon > 0) {
        Set<Integer> numbers = new HashSet<>();
        for (String number : line.substring(colon + 1).split(",")) {
          numbers.add(Integer.parseInt(number));
        }
        lines.put(line.substring(0, colon), numbers);
      }
    }
    return lines;
  }

  private static void collect(
      File report,
      Map<String, Set<Integer>> lines,
      Map<String, Set<String>> outcomes,
      Set<String> mutated) {
    NodeList mutations;
    try {
      mutations =
          DocumentBuilderFactory.newInstance()
              .newDocumentBuilder()
              .parse(report)
              .getElementsByTagName("mutation");
    } catch (Exception e) {
      throw new GradleException("pitestDiff: cannot read " + report, e);
    }
    for (int i = 0; i < mutations.getLength(); i++) {
      Element mutation = (Element) mutations.item(i);
      String source =
          text(mutation, "mutatedClass").replaceFirst("\\.[^.]+$", "").replace('.', '/')
              + "/"
              + text(mutation, "sourceFile");
      int number = Integer.parseInt(text(mutation, "lineNumber"));
      if (lines.getOrDefault(source, Set.of()).contains(number)) {
        mutated.add(source);
        String mutant =
            String.join(
                " ",
                source + ":" + number,
                text(mutation, "mutatedMethod"),
                text(mutation, "description"),
                text(mutation, "indexes"));
        outcomes
            .computeIfAbsent(mutant, key -> new HashSet<>())
            .add(mutation.getAttribute("status"));
      }
    }
  }

  private static String text(Element parent, String tag) {
    return parent.getElementsByTagName(tag).item(0).getTextContent();
  }
}
