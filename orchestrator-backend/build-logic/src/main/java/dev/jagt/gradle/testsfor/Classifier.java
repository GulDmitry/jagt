package dev.jagt.gradle.testsfor;

import static dev.jagt.gradle.testsfor.Layout.like;

import dev.jagt.gradle.testsfor.Layout.Located;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class Classifier {

  private static final String MAIN = "main";
  private static final String JQWIK = "import net.jqwik.";
  private static final Pattern SCANNED_ROOT =
      Pattern.compile("Path\\.of\\(\"([^\"]*src/[^\"]*)\"");
  private static final String TOMCAT = "[A-Za-z0-9]*OnTomcat";

  private final Repo repo;
  private final Layout layout;
  private final Selection selection;
  private final Map<String, List<String>> named = new HashMap<>();
  private ScanRows scanRows;
  private Map<String, List<String>> scanners;

  Classifier(Repo repo, Layout layout, Selection selection) {
    this.repo = repo;
    this.layout = layout;
    this.selection = selection;
  }

  void take(String path) {
    if (path.endsWith(".java")) {
      java(path);
    } else if (path.endsWith(".md")) {
      selection.rules(
          "Fitness",
          "DocLinks",
          "DecisionIndex",
          "Features",
          "SpecLimits",
          "PracticesPage",
          "EngineeringSpec");
    }
    if (scanRows().governsSize(path)) {
      selection.sizeRows = true;
    }
    if (scanRows().scans(path)) {
      selection.rules("Fitness");
    }
    testsScanning(path);
    if (like(path, ".github/*", "*/.github/*", "scripts/*", "*/scripts/*")) {
      selection.rules("CiWorkflow", "ImageJar", "QualityGates");
    }
    if (!path.endsWith(".java")) {
      testsReading(path.endsWith(".md") ? path : Path.of(path).getFileName().toString());
    }
    byName(path);
    if (path.endsWith(".yml.dist")) {
      selection.rules("ConfigReference");
    }
  }

  private ScanRows scanRows() {
    if (scanRows == null) {
      scanRows = ScanRows.of(repo.linesOf("fitness.yml"), layout.backend());
    }
    return scanRows;
  }

  private void testsScanning(String path) {
    layout
        .insideBackend(path)
        .ifPresent(
            inside ->
                scanners()
                    .forEach(
                        (type, roots) -> {
                          if (roots.stream().anyMatch(root -> underRoot(inside, root))) {
                            testsNaming(Layout.ROOT, type);
                          }
                        }));
  }

  private static String typeOf(String file) {
    return Path.of(file).getFileName().toString().replaceFirst("\\.java$", "");
  }

  private static boolean underRoot(String path, String root) {
    return path.equals(root) || path.startsWith(root.endsWith("/") ? root : root + "/");
  }

  private Map<String, List<String>> scanners() {
    if (scanners == null) {
      scanners = new HashMap<>();
      for (String file : repo.filesIn(List.of(layout.testDirectory(Layout.ROOT)))) {
        repo.holds(
            file,
            text -> {
              List<String> roots =
                  SCANNED_ROOT.matcher(text).results().map(m -> m.group(1)).toList();
              if (!roots.isEmpty()) {
                scanners.put(typeOf(file), roots);
              }
              return true;
            });
      }
    }
    return scanners;
  }

  private void java(String path) {
    selection.rules("Packages", "VendorNames", "Kinds", "Fitness");
    layout.locate(path).ifPresent(at -> located(path, at));
  }

  private void located(String path, Located at) {
    boolean main = at.set().equals(MAIN);
    selection.projects.add(at.module());
    if (main) {
      testsNaming(at.module(), TOMCAT);
    } else if (!at.set().equals("test")) {
      selection.otherSets.add(at.module() + ":" + at.set());
    }
    if (!repo.exists(path)) {
      selection.removedMain |= main;
      return;
    }
    String type = Path.of(path).getFileName().toString().replaceFirst("\\.java$", "");
    if (at.set().equals("test") && (type.endsWith("Test") || jqwik(path))) {
      testsOf(path).add(at.module() + ":" + type);
    }
    testsNaming(at.module(), type);
  }

  private void testsNaming(String module, String typeRegex) {
    named
        .computeIfAbsent(module + ":" + typeRegex, key -> find(module, typeRegex))
        .forEach(file -> testsOf(file).add(entry(file)));
  }

  private Set<String> testsOf(String file) {
    return jqwik(file) ? selection.properties : selection.tests;
  }

  private boolean jqwik(String file) {
    return repo.holds(file, text -> text.contains(JQWIK));
  }

  private boolean isSuite(String file) {
    return file.endsWith("Test.java") || jqwik(file);
  }

  private List<String> find(String module, String typeRegex) {
    List<String> directories =
        Stream.concat(Stream.of(module), layout.dependents(module).stream())
            .map(layout::testDirectory)
            .toList();
    Pattern fileName = Pattern.compile("/" + typeRegex + "[A-Za-z0-9]*\\.java$");
    Pattern word = Pattern.compile("(?<!\\w)" + typeRegex + "(?!\\w)");
    TreeSet<String> found = new TreeSet<>();
    repo.filesIn(directories).stream()
        .filter(file -> fileName.matcher(file).find())
        .filter(this::isSuite)
        .forEach(found::add);
    repo.filesWhoseText(text -> word.matcher(text).find(), directories).stream()
        .filter(this::isSuite)
        .forEach(found::add);
    return List.copyOf(found);
  }

  private String entry(String file) {
    String module = layout.locate(file).map(Located::module).orElse("");
    return module + ":" + Path.of(file).getFileName().toString().replaceFirst("\\.java$", "");
  }

  private void testsReading(String text) {
    List<String> directory = List.of(layout.testDirectory(Layout.ROOT));
    repo.filesWhoseText(content -> content.contains(text), directory).stream()
        .filter(file -> file.endsWith("Test.java"))
        .forEach(file -> selection.tests.add(entry(file)));
  }

  private void byName(String path) {
    if (like(path, "*.lockfile", "*/gradle-wrapper.properties")) {
      selection.rules("Pinning");
    } else if (like(
        path,
        "*/Dockerfile*",
        "Dockerfile*",
        "*/entrypoint.sh",
        "entrypoint.sh",
        "*/kubernetes.yml.dist",
        "kubernetes.yml.dist")) {
      selection.rules("KubernetesManifests", "ImageJvmOptions");
    } else if (like(path, "fitness.yml", "*/fitness.yml")) {
      selection.rules("Fitness");
    } else if (like(path, "fitness/*", "*/fitness/*")) {
      selection.fitness = true;
      selection.rules("Fitness");
    } else if (like(path, "build-logic/*", "*/build-logic/*")) {
      selection.buildLogic = true;
    } else if (like(path, "board/*")) {
      selection.board = true;
    } else if (like(path, "site/*")) {
      selection.rules("Site");
    } else if (like(path, "roles/*")) {
      selection.rules(
          "RoleNamesFitness", "RoleNamesOutsideFitness", "RoleVocabulary", "MasterBriefShipped");
      roleEval(path);
    }
  }

  private void roleEval(String path) {
    String[] parts = path.split("/");
    String eval = "roles/" + parts[1] + "/eval";
    if (parts.length > 2
        && (path.endsWith(".dist") || path.startsWith(eval + "/"))
        && repo.exists(eval)) {
      selection.roleEvals.add(parts[1]);
    }
  }
}
