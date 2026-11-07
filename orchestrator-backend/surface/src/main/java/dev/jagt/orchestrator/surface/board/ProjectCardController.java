package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.ProjectCards;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectCardController {

  private final ProjectCards cards;

  /** A GET: reading a card writes nothing. */
  @GetMapping("/{key}/card")
  public ProjectCards.Card card(@PathVariable("key") String key) {
    return cards.of(key);
  }
}
