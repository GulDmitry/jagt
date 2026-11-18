package dev.jagt.orchestrator.job.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.review.FakeJobWorld.Said;
import dev.jagt.orchestrator.job.review.FakeJobWorld.Start;
import dev.jagt.orchestrator.job.review.FakeJobWorld.Step;
import dev.jagt.orchestrator.job.review.FakeJobWorld.Told;
import dev.jagt.orchestrator.job.verdict.MasterRuling;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.review.ReviewSweepService;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;

class JobsTellOnceProperties {

  private final FakeSweepKind sweepKind = new FakeSweepKind();
  private final ReviewSweepService sweeps = mock(ReviewSweepService.class);
  private final MasterRuling ruling = mock(MasterRuling.class);
  private final CommandService commands = mock(CommandService.class);

  @BeforeTry
  void sweepsAnswerTheKindTheScriptSet() {
    when(sweeps.sweep(anyString())).thenAnswer(call -> new SweepResult(sweepKind.kind(), ""));
  }

  @Example
  void everyJobTellsWhereItsFactsChange() {
    try (FakeJobWorld world =
        new FakeJobWorld(
            new Start(TaskStatus.CI_POLLING, 10, TaskStatus.IN_PROGRESS),
            sweepKind,
            sweeps,
            ruling,
            commands)) {
      world.run(
          List.of(
              Step.POINT_WATCH,
              Step.YOUR_MOVE,
              Step.ASK,
              Step.YOUR_MOVE,
              Step.MINUTE,
              Step.YOUR_MOVE,
              Step.MASTER_REVIEW,
              Step.CONFLICT,
              Step.CONFLICT_HAND,
              Step.DAY,
              Step.CONFLICT_HAND,
              Step.AUTO_REVIEW,
              Step.POINT_WATCH,
              Step.OPEN_TURN,
              Step.SESSION_WRITES,
              Step.RESTART,
              Step.RESTART_ASK));

      assertThat(world.sent())
          .extracting(Told::point)
          .contains(
              "your-move", "master-review", "conflict-stalled", "auto-review", "deploy-conflict");
      assertThat(world.said()).extracting(Said::task).contains("ABC-3", "ABC-4");
    }
  }

  @Property
  void eachPointIsToldOncePerChangeOfItsFacts(
      @ForAll("starts") Start start, @ForAll("scripts") List<Step> script) {
    try (FakeJobWorld world = new FakeJobWorld(start, sweepKind, sweeps, ruling, commands)) {
      world.run(script);

      Map<Told, Long> timesSent =
          world.sent().stream().collect(Collectors.groupingBy(sent -> sent, Collectors.counting()));
      assertThat(timesSent.values())
          .as("%s then %s told %s", start, script, timesSent)
          .allMatch(times -> times == 1);
    }
  }

  @Property
  void sessionIsToldAtMostOncePerStartAndChange(
      @ForAll("starts") Start start, @ForAll("scripts") List<Step> script) {
    try (FakeJobWorld world = new FakeJobWorld(start, sweepKind, sweeps, ruling, commands)) {
      world.run(script);

      Map<Said, Long> timesSaid =
          world.said().stream().collect(Collectors.groupingBy(said -> said, Collectors.counting()));
      assertThat(timesSaid.values())
          .as("%s then %s said %s", start, script, timesSaid)
          .allMatch(times -> times == 1);
    }
  }

  @Property
  void theSameScriptTellsTheSameMessages(
      @ForAll("starts") Start start, @ForAll("scripts") List<Step> script) {
    List<Told> told;
    try (FakeJobWorld first = new FakeJobWorld(start, sweepKind, sweeps, ruling, commands)) {
      first.run(script);
      told = first.sent();
    }
    try (FakeJobWorld second = new FakeJobWorld(start, sweepKind, sweeps, ruling, commands)) {
      second.run(script);

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
