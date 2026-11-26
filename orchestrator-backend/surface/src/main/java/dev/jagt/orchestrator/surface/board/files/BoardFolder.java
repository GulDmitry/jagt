package dev.jagt.orchestrator.surface.board.files;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class BoardFolder implements WebMvcConfigurer {

  private final BoardProperties properties;

  private final OrchestratorPaths paths;

  public Resource resource(String page) {
    if (StringUtils.hasText(properties.dir())) {
      Path served = paths.root().resolve(properties.dir()).resolve(page);
      if (Files.isRegularFile(served)) {
        return new FileSystemResource(served);
      }
    }
    return new ClassPathResource("static/" + page);
  }

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
