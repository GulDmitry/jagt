package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In memory: a restart reads again, and a read that still cannot reach says so at once. */
@Slf4j
@Component
public class ReachLedger implements Reach {

    private final Map<String, ReachLost> standing = new ConcurrentHashMap<>();

    @Override
    public void lost(ReachLost lost) {
        if (standing.put(lost.key(), lost) == null) {
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
        return standing.values().stream().sorted(Comparator.comparing(ReachLost::key)).toList();
    }
}
