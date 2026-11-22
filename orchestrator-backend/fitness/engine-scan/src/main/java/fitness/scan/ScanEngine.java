package fitness.scan;

import fitness.core.Direction;
import fitness.core.Engine;
import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import fitness.core.Reason;
import fitness.core.run.SourceRoots;
import fitness.scan.measure.Assertionless;
import fitness.scan.measure.Complexity;
import fitness.scan.measure.Duplication;
import fitness.scan.measure.Links;
import fitness.scan.measure.MockedTypes;
import fitness.scan.measure.Sentences;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;

public final class ScanEngine implements Engine {

  private static final Set<String> BUILD_OUTPUT = Set.of("build", "bin", ".gradle");
  private static final String ALL_JAVA = "**/*.java";
  private static final Map<String, Listing> LISTINGS =
      Map.of(
          "test:assertionless", Assertionless::tests,
          "link:unresolved", Links::unresolved,
          "link:nested", Links::nested);
  private static final Map<String, Sizing> SIZINGS =
      Map.of(
          "file:lines", Texts::fileLines,
          "section:lines", Texts::sectionLines,
          "line:characters", Texts::lineCharacters,
          "project:comment-percent", Texts::commentPercent,
          "sentence:words", Sentences::words,
          "method:cognitive-complexity", Complexity::cognitive,
          "duplicate:tokens", Duplication::tokens,
          "test:mocked-types", MockedTypes::perTest,
          "package:classes", Packages::classes);

  @Override
  public String name() {
    return "scan";
  }

  @Override
  public Map<String, Set<String>> vocabulary() {
    return Map.of(
        "kind", Set.of("file", "directory"),
        "per",
            Set.of(
                "file",
                "name",
                "line",
                "match",
                "section",
                "sentence",
                "method",
                "duplicate",
                "test",
                "package",
                "link",
                "project"),
        "measure",
            Set.of(
                "lines",
                "characters",
                "words",
                "tokens",
                "cognitive-complexity",
                "mocked-types",
                "classes",
                "comment-percent",
                "assertionless",
                "unresolved",
                "nested"),
        "set", Set.of(),
        "glob", Set.of(),
        "requires", Set.of(),
        "regex", Set.of(),
        "machine", Set.of("paths"),
        "except", Set.of());
  }

  @Override
  public Set<String> needs(FitnessFunction function) {
    String measure = String.valueOf(function.selectors().get("measure"));
    boolean pmd = Set.of("cognitive-complexity", "tokens").contains(measure);
    return pmd ? Set.of("pmd") : Set.of();
  }

  @Override
  public Map<String, String> libraries() {
    return Map.of("pmd", "net.sourceforge.pmd.PMDConfiguration");
  }

  @Override
  public void validate(FitnessFunction function, Project project) {
    Map<String, Object> selectors = function.selectors();
    String key = function.section() + "." + function.id();
    if (!"tracked".equals(selectors.get("set"))) {
      SourceRoots.validate(function, project);
    }
    if (selectors.get("set") == null && selectors.get("glob") == null) {
      throw new FitnessConfigException(Reason.MISSING_KEY, key + ".glob");
    }
    if (selectors.get("regex") instanceof String text) {
      compiles(key, text, "match".equals(selectors.get("per")));
    }
    if (!(selectors.get("requires") instanceof String)) {
      validateMeasure(function, key);
    }
  }

  private static void compiles(String key, String text, boolean needsGroup) {
    Pattern pattern;
    try {
      pattern = Pattern.compile(text);
    } catch (PatternSyntaxException e) {
      throw new FitnessConfigException(Reason.UNKNOWN_SELECTOR, key + ".regex", e);
    }
    if (needsGroup && pattern.matcher("").groupCount() == 0) {
      throw new FitnessConfigException(Reason.UNKNOWN_SELECTOR, key + ".regex");
    }
  }

  private static void validateMeasure(FitnessFunction function, String key) {
    Map<String, Object> selectors = function.selectors();
    Object per = selectors.get("per");
    boolean hasRegex = selectors.get("regex") instanceof String;
    if (!(selectors.get("measure") instanceof String measure)) {
      if (!hasRegex && ("line".equals(per) || "match".equals(per))) {
        throw new FitnessConfigException(Reason.MISSING_KEY, key + ".regex");
      }
      return;
    }
    if (LISTINGS.containsKey(per + ":" + measure)) {
      return;
    }
    if (per == null) {
      throw new FitnessConfigException(Reason.MISSING_KEY, key + ".per");
    }
    if (!SIZINGS.containsKey(per + ":" + measure)) {
      throw new FitnessConfigException(Reason.UNKNOWN_SELECTOR, key + ".measure");
    }
    if ("section".equals(per) && !hasRegex) {
      throw new FitnessConfigException(Reason.MISSING_KEY, key + ".regex");
    }
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<Path> files = candidates(function, project);
    if (files.isEmpty() && checksFiles(function)) {
      return new Measurement(
          function.threshold().limit() + 1, List.of("matches no file: " + scope(function)));
    }
    if (function.selectors().get("requires") instanceof String required) {
      return Measurement.of(lacking(project, files, required, Sources.regex(function)));
    }
    if (function.selectors().get("measure") instanceof String measure) {
      Listing listing = LISTINGS.get(function.selectors().get("per") + ":" + measure);
      return listing == null
          ? sized(function, project, files, measure)
          : Measurement.of(listing.items(function, project, files));
    }
    Pattern regex = Sources.regex(function);
    String per = String.valueOf(function.selectors().getOrDefault("per", "file"));
    List<String> items = new ArrayList<>();
    for (Path path : files) {
      items.addAll(itemsOf(function, project, path, per, regex));
    }
    return Measurement.of(items);
  }

  private static boolean checksFiles(FitnessFunction function) {
    Map<String, Object> selectors = function.selectors();
    boolean listsTheFiles =
        !selectors.containsKey("measure")
            && !selectors.containsKey("requires")
            && !selectors.containsKey("regex")
            && Set.of("file", "name")
                .contains(String.valueOf(selectors.getOrDefault("per", "file")));
    return function.threshold().direction() == Direction.MAX && !listsTheFiles;
  }

  private static String scope(FitnessFunction function) {
    Object glob = function.selectors().get("glob");
    return String.valueOf(glob != null ? glob : function.selectors().get("set"));
  }

  private static List<String> lacking(
      Project project, List<Path> files, String required, @Nullable Pattern holds) {
    return files.stream()
        .map(Path::getParent)
        .distinct()
        .filter(
            directory -> {
              Path file = directory.resolve(required);
              return !Files.isRegularFile(file)
                  || holds != null && !holds.matcher(Sources.read(file)).find();
            })
        .map(directory -> Sources.relative(project, directory))
        .toList();
  }

  private static Measurement sized(
      FitnessFunction function, Project project, List<Path> files, String measure) {
    if (!(function.selectors().get("per") instanceof String per)) {
      throw new FitnessConfigException(Reason.MISSING_KEY, function.id() + ".per");
    }
    Sizing sizing = SIZINGS.get(per + ":" + measure);
    if (sizing == null) {
      throw new FitnessConfigException(Reason.UNKNOWN_SELECTOR, function.id() + ".measure");
    }
    List<Sized> sizes = sizing.sizes(function, project, files);
    long limit = function.threshold().limit();
    List<String> over =
        sizes.stream()
            .filter(unit -> unit.size() > limit)
            .map(unit -> unit.size() + " " + unit.label())
            .sorted()
            .toList();
    long largest = sizes.stream().mapToLong(Sized::size).max().orElse(0);
    return new Measurement(largest, over);
  }

  private static List<String> itemsOf(
      FitnessFunction function, Project project, Path path, String per, Pattern regex) {
    if (per.equals("line") || per.equals("match")) {
      if (regex == null) {
        throw new FitnessConfigException(Reason.MISSING_KEY, function.id() + ".regex");
      }
      return per.equals("line") ? lines(project, path, regex) : matches(path, regex);
    }
    if (regex != null && !regex.matcher(Sources.read(path)).find()) {
      return List.of();
    }
    return List.of(
        per.equals("name") ? path.getFileName().toString() : Sources.relative(project, path));
  }

  private static List<String> lines(Project project, Path path, Pattern regex) {
    return Sources.read(path)
        .lines()
        .filter(line -> regex.matcher(line).find())
        .map(line -> Sources.relative(project, path) + " " + line.trim())
        .toList();
  }

  private static List<String> matches(Path path, Pattern regex) {
    Matcher matcher = regex.matcher(Sources.read(path));
    List<String> found = new ArrayList<>();
    while (matcher.find()) {
      found.add(matcher.group(1));
    }
    return found;
  }

  private static List<Path> candidates(FitnessFunction function, Project project) {
    List<Path> selected = selected(function, project);
    if (!(function.selectors().get("except") instanceof String except)) {
      return selected;
    }
    List<PathMatcher> excluded =
        Glob.split(except).stream()
            .map(each -> FileSystems.getDefault().getPathMatcher("glob:" + each))
            .toList();
    return selected.stream()
        .filter(
            path -> excluded.stream().noneMatch(m -> m.matches(project.root().relativize(path))))
        .toList();
  }

  private static List<Path> selected(FitnessFunction function, Project project) {
    boolean directories = "directory".equals(function.selectors().get("kind"));
    String glob = (String) function.selectors().get("glob");
    Object set = function.selectors().get("set");
    if ("tracked".equals(set)) {
      List<Path> tracked = Tracked.files(project.root());
      return glob == null ? tracked : globbed(tracked, project, glob);
    }
    return set == null
        ? untied(function, project, glob, directories)
        : inSourceSet(project, (String) set, glob, directories);
  }

  private static List<Path> untied(
      FitnessFunction function, Project project, String glob, boolean directories) {
    if (glob == null) {
      throw new FitnessConfigException(Reason.MISSING_KEY, function.id() + ".glob");
    }
    Set<Path> found = new LinkedHashSet<>();
    for (String each : Glob.split(glob)) {
      found.addAll(matching(project.root(), each, directories));
    }
    return List.copyOf(found);
  }

  private static List<Path> inSourceSet(
      Project project, String set, String glob, boolean directories) {
    Set<Path> found = new LinkedHashSet<>();
    for (Path root : SourceRoots.of(project, set)) {
      for (String each : Glob.split(glob == null ? ALL_JAVA : glob)) {
        found.addAll(matching(root, each, directories));
      }
    }
    return List.copyOf(found);
  }

  private static List<Path> globbed(List<Path> tracked, Project project, String globs) {
    List<Glob> compiled =
        Glob.split(globs).stream().map(each -> Glob.of(project.root(), each)).toList();
    return tracked.stream()
        .filter(
            path ->
                compiled.stream()
                    .anyMatch(
                        glob ->
                            path.startsWith(glob.base())
                                && glob.matches(
                                    glob.base().relativize(path).toString().replace('\\', '/'))))
        .toList();
  }

  private static List<Path> matching(Path root, String glob, boolean directories) {
    if (Glob.isLiteral(glob)) {
      Path path = root.resolve(glob).normalize();
      return Files.exists(path) && Files.isDirectory(path) == directories
          ? List.of(path)
          : List.of();
    }
    Glob compiled = Glob.of(root, glob);
    return walk(compiled.base()).stream()
        .filter(path -> Files.isDirectory(path) == directories)
        .filter(
            path ->
                compiled.matches(compiled.base().relativize(path).toString().replace('\\', '/')))
        .toList();
  }

  private static List<Path> walk(Path base) {
    List<Path> found = new ArrayList<>();
    try {
      Files.walkFileTree(
          base,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (!dir.equals(base) && BUILD_OUTPUT.contains(dir.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              if (!dir.equals(base)) {
                found.add(dir);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              found.add(file);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return found;
  }
}
