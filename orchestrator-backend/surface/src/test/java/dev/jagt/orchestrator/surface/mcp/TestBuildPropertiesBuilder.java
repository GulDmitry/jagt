package dev.jagt.orchestrator.surface.mcp;

import java.util.Properties;
import lombok.With;
import org.springframework.boot.info.BuildProperties;

@With
public record TestBuildPropertiesBuilder(String version) {

  public static TestBuildPropertiesBuilder buildProperties() {
    return new TestBuildPropertiesBuilder("1.2.3");
  }

  public BuildProperties build() {
    Properties entries = new Properties();
    entries.setProperty("version", version);
    return new BuildProperties(entries);
  }
}
