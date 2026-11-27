package dev.jagt.orchestrator.job.checker.wiring;

import dev.jagt.orchestrator.job.JobSource;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.job.checker.CheckerCheckJob;
import dev.jagt.orchestrator.job.checker.CheckerDeclineJob;
import dev.jagt.orchestrator.job.checker.CheckerOutcomes;
import dev.jagt.orchestrator.job.checker.CheckerPrompts;
import dev.jagt.orchestrator.job.checker.CheckerReads;
import dev.jagt.orchestrator.job.checker.CheckerRecords;
import dev.jagt.orchestrator.job.checker.CheckerWaitJob;
import dev.jagt.orchestrator.port.assistant.EnvironmentProbe;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.role.RoleKits;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The check, wait and decline jobs of every installed checker whose {@code role.yml} runs jobs. */
@Component
@RequiredArgsConstructor
public class CheckerJobs implements JobSource {

  private final RoleKits kits;
  private final CheckerReaders readers;
  private final CheckerActs acts;
  private final CheckerLedgers ledgers;
  private final EnvironmentProbe probe;

  @Override
  public List<TaskJob> jobs() {
    List<TaskJob> jobs = new ArrayList<>();
    for (RoleKit kit : kits.all()) {
      if (kit.definition().checking().jobs()) {
        jobs.addAll(of(kit));
      }
    }
    return jobs;
  }

  private List<TaskJob> of(RoleKit kit) {
    CheckerPrompts prompts = ledgers.prompts(kit);
    CheckerReads reads = readers.reads(kit);
    CheckerRecords records = ledgers.records(kit.definition().id());
    CheckerOutcomes outcomes = acts.outcomes(kit);
    List<TaskJob> jobs = new ArrayList<>();
    jobs.add(new CheckerCheckJob(kit.settings(), prompts, reads, records, outcomes));
    jobs.add(new CheckerWaitJob(kit.settings(), records, probe));
    jobs.add(new CheckerDeclineJob(kit.settings(), prompts, reads, records, outcomes));
    return jobs;
  }
}
