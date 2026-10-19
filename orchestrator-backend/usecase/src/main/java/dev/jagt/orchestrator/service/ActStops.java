package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.ActStop.Cause;
import dev.jagt.orchestrator.service.master.MasterBrake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** The two stops that demote `act` to judge: three bad endings, and the day's ceiling on unattended starts. */
@Service
@RequiredArgsConstructor
public class ActStops {

    private final MasterBrake brake;
    private final DailyStarts dailyStarts;

    public Optional<ActStop> standing() {
        return dailyStarts.standing().or(() -> brake.stopped() ? Optional.of(new ActStop(Cause.STRIKES, 0)) : Optional.empty());
    }

    /** Only the ceiling stops what starts unasked; three bad endings leave it be. */
    public boolean startsStopped() {
        return dailyStarts.standing().isPresent();
    }

    public void take(int ceiling) {
        dailyStarts.take(ceiling);
    }

    /** True where either stood. */
    public boolean lift() {
        boolean struck = brake.restore();
        boolean capped = dailyStarts.lift();
        return struck || capped;
    }
}
