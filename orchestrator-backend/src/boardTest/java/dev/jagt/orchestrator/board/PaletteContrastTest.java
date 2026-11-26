package dev.jagt.orchestrator.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaletteContrastTest {

  private static final Path CSS = Path.of("../board/app.css");

  @ParameterizedTest(name = "{0}")
  @CsvSource({"light,1", "dark,2"})
  void controlEdgeIsAtLeastThreeToOneOnThePanel(String theme, int group) throws IOException {
    String css = Files.readString(CSS);
    Matcher text = Pattern.compile("--text: light-dark\\((#\\w{6}), (#\\w{6})\\)").matcher(css);
    Matcher panel = Pattern.compile("--panel: light-dark\\((#\\w{3,6}), (#\\w{6})\\)").matcher(css);
    Matcher mix =
        Pattern.compile("color-mix\\(in srgb, var\\(--text\\) (\\d+)%, var\\(--panel\\)\\)")
            .matcher(css);
    assertThat(text.find() && panel.find() && mix.find()).isTrue();
    double share = Double.parseDouble(mix.group(1)) / 100;
    int textRgb = Integer.parseInt(text.group(group).substring(1), 16);
    int panelRgb =
        Integer.parseInt(
            panel.group(group).substring(1).replaceAll("^(.)(.)(.)$", "$1$1$2$2$3$3"), 16);
    DoubleUnaryOperator linear = c -> c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    ToDoubleFunction<double[]> luminance =
        rgb ->
            0.2126 * linear.applyAsDouble(rgb[0])
                + 0.7152 * linear.applyAsDouble(rgb[1])
                + 0.0722 * linear.applyAsDouble(rgb[2]);
    double[] edge = new double[3];
    double[] ground = new double[3];
    for (int i = 0; i < 3; i++) {
      double t = ((textRgb >> (16 - 8 * i)) & 0xff) / 255.0;
      double p = ((panelRgb >> (16 - 8 * i)) & 0xff) / 255.0;
      edge[i] = share * t + (1 - share) * p;
      ground[i] = p;
    }
    double a = luminance.applyAsDouble(edge);
    double b = luminance.applyAsDouble(ground);

    double ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);

    assertThat(ratio).as("%s edge on the panel", theme).isGreaterThanOrEqualTo(3.0);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "button",
        "select,\ninput[type=\"text\"],\ninput[type=\"number\"],\ninput:not([type])",
        ".search",
        ".choice",
        "input[role=\"switch\"]"
      })
  void everyControlDrawsItsEdgeWithTheMixNotTheLine(String selector) throws IOException {
    String css = Files.readString(CSS);

    assertThat(css)
        .containsPattern(
            "\\n"
                + Pattern.quote(selector)
                + " \\{[^}]*border: 1px solid color-mix\\(in srgb, var\\(--text\\)");
  }
}
