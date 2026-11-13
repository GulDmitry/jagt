package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.service.BoardUrl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
class BoardBannerWiring {

  @Bean
  @Order(Integer.MAX_VALUE)
  BoardBanner boardBanner(BoardUrl boardUrl) {
    return new BoardBanner(boardUrl, System.out);
  }
}
