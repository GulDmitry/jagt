package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.project.Settings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

  public record Request(String key, @Nullable Object value) {}

  private final Settings settings;

  /** A GET: reading the settings writes nothing. */
  @GetMapping
  public List<Settings.View> read() {
    return settings.read();
  }

  @PutMapping
  public Settings.Change change(@RequestBody Request request) {
    return settings.change(request.key(), request.value());
  }
}
