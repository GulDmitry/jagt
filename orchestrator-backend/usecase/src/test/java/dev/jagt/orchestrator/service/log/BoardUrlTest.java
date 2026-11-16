package dev.jagt.orchestrator.service.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoardUrlTest {

  @Test
  void linkStartsWithThePublicUrlWhenOneIsSet() {
    BoardUrl url = new BoardUrl("8290", " https://jagt.example.org/ ");

    assertThat(url.ofTask("ABC-42")).isEqualTo("https://jagt.example.org/?task=ABC-42");
    assertThat(url.root()).isEqualTo("https://jagt.example.org");
  }

  @Test
  void linkNamesLocalhostWhenNoPublicUrlIsSet() {
    BoardUrl url = new BoardUrl("8290", "");

    assertThat(url.ofTask("ABC-42")).isEqualTo("http://localhost:8290/?task=ABC-42");
  }

  @Test
  void hostIsTheHostOfThePublicUrl() {
    assertThat(new BoardUrl("8290", "https://jagt.example.com:8443/").host())
        .isEqualTo("jagt.example.com");
  }

  @Test
  void isReachedByNameOnlyWhenPublicUrlNamesNonLoopbackHost() {
    assertThat(new BoardUrl("8290", "https://jagt.example.com").reachedByName()).isTrue();
    assertThat(new BoardUrl("8290", "").reachedByName()).isFalse();
  }
}
