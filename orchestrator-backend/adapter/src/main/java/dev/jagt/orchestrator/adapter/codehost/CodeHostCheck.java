package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The selected code host's CLI or command is there; whether it is signed in is read when a pipeline
 * is.
 */
@Component
@RequiredArgsConstructor
public class CodeHostCheck implements StartupCheck {

  private final ConfigService configService;
  private final List<CodeHostDriver> drivers;

  @Override
  public List<Problem> problems() {
    ConfigService.ConfigFile config;
    try {
      config = configService.load();
    } catch (RuntimeException unreadable) {
      return List.of();
    }
    List<Problem> problems = new ArrayList<>();
    config
        .projects()
        .forEach(
            (project, declared) -> {
              CodeHostChoice choice = CodeHostChoice.of(config, project, declared);
              drivers.stream()
                  .filter(driver -> driver.id().equals(choice.kind()))
                  .findFirst()
                  .ifPresentOrElse(
                      driver -> problems.addAll(driver.problems(choice.host())),
                      () ->
                          problems.add(
                              new Problem(
                                  choice.host().selectedBy(),
                                  choice.host().selectedBy()
                                      + ": '"
                                      + choice.kind()
                                      + "' is not one of "
                                      + String.join(
                                          ", ",
                                          drivers.stream()
                                              .map(CodeHostDriver::id)
                                              .sorted()
                                              .toList())
                                      + ".")));
            });
    return problems.stream().distinct().toList();
  }
}
