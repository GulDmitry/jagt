package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.ActStop.Cause;
import dev.jagt.orchestrator.service.master.MasterBrake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** The stop that demotes `act` to judge: three bad endings. */
@Service
@RequiredArgsConstructor
public class ActStops {

    private final MasterBrake brake;

    public Optional<ActStop> standing() {
        return brake.stopped() ? Optional.of(new ActStop(Cause.STRIKES)) : Optional.empty();
    }

    /** True where it stood. */
    public boolean lift() {
        return brake.restore();
    }
}
