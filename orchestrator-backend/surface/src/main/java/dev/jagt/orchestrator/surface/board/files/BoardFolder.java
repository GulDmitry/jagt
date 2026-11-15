package dev.jagt.orchestrator.surface.board.files;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class BoardFolder implements WebMvcConfigurer {

  private final BoardProperties properties;

  private final OrchestratorPaths paths;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (!StringUtils.hasText(properties.dir())) {
      return;
    }
    registry
        .addResourceHandler("/**")
        .addResourceLocations(paths.root().resolve(properties.dir()).toUri().toString());
  }
}
