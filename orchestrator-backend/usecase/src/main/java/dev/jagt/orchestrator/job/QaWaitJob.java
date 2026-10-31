package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.port.EnvironmentProbe;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaEnvironment;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * A check that found its environment down holds with nobody until every environment answers its probe: its `landed`
 * address where one is named, else its own. Then the same check runs again.
 */
@Service
@RequiredArgsConstructor
public class QaWaitJob implements TaskJob {

    private final ConfigService config;
    private final QaRecords records;
    private final EnvironmentProbe probe;

    @Override
    public String id() {
        return "qa-wait";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "run a QA check again once the environment it found down answers its probe";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(FlowRules::checkedWhereItLanded).withStops(StopKind.NONE);
    }

    @Override
    public void run(String taskId, TaskState task) {
        List<QaEnvironment> environments = config.load().qa().environmentsOrEmpty();
        records.latest(taskId).filter(record -> record.ending() == QaEnding.WAITING).ifPresent(record -> {
            if (!environments.isEmpty() && environments.stream().allMatch(this::answers)) {
                records.released(taskId, record.digest());
            }
        });
    }

    private boolean answers(QaEnvironment environment) {
        String landed = environment.landed();
        String address = landed == null || landed.isBlank() ? environment.url() : landed;
        return address != null && probe.answers(address);
    }
}
