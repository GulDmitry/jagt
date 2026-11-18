package dev.jagt.orchestrator.job.review;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.job.review.FakeJobWorld.Start;
import dev.jagt.orchestrator.job.review.FakeJobWorld.Step;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class JobsTellOnceProperties {

  @Property
  void eachPointIsToldOncePerTaskWhileItsFactsStandStill(
      @ForAll("starts") Start start, @ForAll("scripts") List<Step> script) {
    try (FakeJobWorld world = new FakeJobWorld(start)) {
      world.run(script);

      Map<String, Long> timesSent =
          world.sent().stream()
              .collect(
                  Collectors.groupingBy(
                      sent -> sent.taskId() + " " + sent.point(), Collectors.counting()));
      assertThat(timesSent.values())
          .as("%s then %s told %s", start, script, timesSent)
          .allMatch(times -> times == 1);
    }
  }

  @Property
  void theSameScriptTellsTheSameMessages(
      @ForAll("starts") Start start, @ForAll("scripts") List<Step> script) {
    try (FakeJobWorld first = new FakeJobWorld(start);
        FakeJobWorld second = new FakeJobWorld(start)) {
      first.run(script);
      second.run(script);

      List<Notification> told = first.sent();
      assertThat(second.sent()).as("%s then %s", start, script).isEqualTo(told);
    }
  }

  @Provide
  Arbitrary<Start> starts() {
    return Combinators.combine(
            Arbitraries.of(TaskStatus.values()),
            Arbitraries.longs().between(0, 1_500),
            Arbitraries.of(TaskStatus.values()))
        .as(Start::new);
  }

  @Provide
  Arbitrary<List<Step>> scripts() {
    return Arbitraries.of(Step.values()).list().ofMinSize(1).ofMaxSize(40);
  }
}
