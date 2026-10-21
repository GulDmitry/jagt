package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FeaturesTest {

    private static final Path FEATURES = Path.of("../docs/features.md");

    @Test
    void everySpecIsLinkedAndEveryLinkedSpecExists() {
        List<String> linked = Pattern.compile("openspec/specs/([^/)#]+)/spec\\.md").matcher(read(FEATURES)).results()
                .map(match -> match.group(1)).distinct().sorted().toList();

        assertThat(linked).isEqualTo(specDirectories());
    }

    @Test
    void everyKeyNamedAsOptInIsDescribedInJagtYmlDist() {
        String dist = read(Path.of("../jagt.yml.dist"));
        List<String> keys = Pattern.compile("Key: `([^`]+)`").matcher(read(FEATURES)).results()
                .map(match -> match.group(1)).toList();

        assertThat(keys).isNotEmpty().allSatisfy(key -> assertThat(describedInOrder(dist, key))
                .describedAs("%s", key).isTrue());
    }

    private static boolean describedInOrder(String dist, String key) {
        int from = 0;
        for (String segment : key.replaceFirst("^orchestrator\\.", "").split("\\.")) {
            Matcher found = Pattern.compile("(?m)^\\s*#?\\s*" + Pattern.quote(segment) + ":").matcher(dist);
            if (!found.find(from)) {
                return false;
            }
            from = found.end();
        }
        return true;
    }

    private static List<String> specDirectories() {
        try (Stream<Path> specs = Files.list(Path.of("../openspec/specs"))) {
            return specs.filter(Files::isDirectory).map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
