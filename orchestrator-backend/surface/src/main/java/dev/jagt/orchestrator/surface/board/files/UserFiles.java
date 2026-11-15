package dev.jagt.orchestrator.surface.board.files;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class UserFiles implements WebMvcConfigurer {

  private final OrchestratorPaths paths;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/user/i18n/**")
        .addResourceLocations(paths.root().resolve("i18n").toUri().toString());
  }
}
