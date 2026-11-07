package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** In memory: a restart reads again, and a read that still cannot reach says so at once. */
@Slf4j
@Component
public class ReachLedger implements Reach {

    private record Standing(ReachLost lost, BooleanSupplier stands) {
    }

    private final Map<String, Standing> standing = new ConcurrentHashMap<>();

    @Override
    public void lost(ReachLost lost) {
        lost(lost, () -> true);
    }

    @Override
    public void lost(ReachLost lost, BooleanSupplier stands) {
        if (standing.put(lost.key(), new Standing(lost, stands)) == null) {
            log.atWarn().setMessage("reach lost").addKeyValue("driver", lost.driver()).addKeyValue("host", lost.host())
                    .addKeyValue("cause", lost.said()).addKeyValue("fix", String.join(" ", lost.fix()))
                    .addKeyValue("effect", "the board's head says so, no task stops").log();
        }
    }

    @Override
    public void reached(String driver, String host) {
        if (standing.remove(driver + "@" + host) != null) {
            log.atInfo().setMessage("reach restored").addKeyValue("driver", driver).addKeyValue("host", host).log();
        }
    }

    @Override
    public List<ReachLost> standing() {
        standing.values().stream().filter(one -> !one.stands().getAsBoolean())
                .forEach(one -> reached(one.lost().driver(), one.lost().host()));
        return standing.values().stream().map(Standing::lost).sorted(Comparator.comparing(ReachLost::key)).toList();
    }
}
