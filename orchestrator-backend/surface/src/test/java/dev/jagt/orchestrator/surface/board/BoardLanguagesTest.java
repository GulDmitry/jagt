package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class BoardLanguagesTest {

  @TempDir Path root;

  private BoardLanguages languages;

  @BeforeEach
  void openLanguagesOverTheRoot() {
    languages =
        new BoardLanguages(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));
  }

  @Test
  void offersTheShippedCatalogsWhenTheRootHoldsNone() {
    assertThat(languages.codes()).containsExactly("en", "pl", "ru");
  }

  @Test
  void catalogFileInTheRootIsLanguageOfferedWithNoRebuild() throws IOException {
    Files.createDirectories(root.resolve("i18n"));
    Files.writeString(root.resolve("i18n/pt-BR.json"), "{\"language.name\": \"Português\"}");

    assertThat(languages.codes()).containsExactly("en", "pl", "pt-BR", "ru");
    assertThat(languages.catalog("pt-BR")).containsEntry("language.name", "Português");
  }

  @Test
  void fileInTheRootThatIsNoCatalogOffersNoLanguage() throws IOException {
    Files.createDirectories(root.resolve("i18n"));
    Files.writeString(root.resolve("i18n/de"), "{}");
    Files.writeString(root.resolve("i18n/not a code.json"), "{}");

    assertThat(languages.codes()).containsExactly("en", "pl", "ru");
  }

  @Test
  void catalogInTheRootOverridesTheShippedWordAndKeepsTheRest() throws IOException {
    Files.createDirectories(root.resolve("i18n"));
    Files.writeString(root.resolve("i18n/pl.json"), "{\"common.close\": \"Zamknij teraz\"}");

    assertThat(languages.catalog("pl"))
        .containsEntry("common.close", "Zamknij teraz")
        .containsEntry("language.name", "Polski");
  }

  @Test
  void codeThatIsNotLanguageCodeFindsNoFile() {
    assertThatThrownBy(() -> languages.catalog("..%2Fjagt"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
