package dev.jagt.orchestrator.surface.board;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BoardAssetsTest {

    private static final Path STATIC = Path.of("src/main/resources/static");
    private static final Pattern LOADED_FROM_OUTSIDE = Pattern.compile(
            "(src|href)=[\"'](https?:)?//|import\\s*\\(?\\s*[\"'](https?:)?//|from\\s+[\"'](https?:)?//"
                    + "|@import\\s+(url\\()?[\"']?(https?:)?//|url\\(\\s*[\"']?(https?:)?//|\"(https?:)?//[^\"]*\\.m?js\"");

    @ParameterizedTest
    @CsvSource({
            "preact.mjs, 3a3201e9ccef7d30ef1273427f1b17ef8323e464b2c8d73b4fd5636af730433e",
            "hooks.mjs, 6cb79fe9959ad185b8b86134aff47c12fd20216b24837597b19be59a133d9947",
            "htm.mjs, ab33dd3f38059b9be4d5f5350128eefb2356639c4e0bbe9d9e8b3ba75847e9e4",
            "mermaid.min.js, 6484afc32872a3aa16cac9a76ba1816a1ed4cc870a6593cc2e17757750f518b2"})
    void aVendoredLibraryIsTheExactFileTheRegistryServed(String file, String sha256)
            throws IOException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(STATIC.resolve("vendor").resolve(file));

        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

        assertThat(digest).isEqualTo(sha256);
    }

    @Test
    void theBoardsCodeAndStylesWeighUnderOneHundredSixtyKilobytes() throws IOException {
        long bytes;
        try (Stream<Path> files = Files.walk(STATIC)) {
            bytes = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().matches(".*\\.(js|mjs|css|html|svg)$"))
                    .filter(path -> !path.endsWith("mermaid.min.js"))
                    .mapToLong(BoardAssetsTest::size).sum();
        }

        assertThat(bytes).isLessThan(160 * 1024);
    }

    @Test
    void noPageScriptOrStyleLoadsAnythingFromOutsideTheJar() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(STATIC)) {
            sources = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().matches(".*\\.(js|mjs|css|html)$"))
                    .filter(path -> !path.endsWith("mermaid.min.js")).toList();
        }

        assertThat(sources).isNotEmpty().noneMatch(source -> LOADED_FROM_OUTSIDE.matcher(read(source)).find());
    }

    @Test
    void noBoardScriptFormatsATimeInTheBrowsersLocaleOrZone() throws IOException {
        List<Path> scripts;
        try (Stream<Path> files = Files.walk(STATIC)) {
            scripts = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().matches(".*\\.m?js$"))
                    .filter(path -> !path.startsWith(STATIC.resolve("vendor"))).toList();
        }

        assertThat(scripts).isNotEmpty()
                .noneMatch(script -> read(script).contains("toLocale"))
                .noneMatch(script -> read(script).contains("Intl.DateTimeFormat")
                        && !read(script).contains("timeZone: 'UTC'"));
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
