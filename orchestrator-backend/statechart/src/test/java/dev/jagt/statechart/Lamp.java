package dev.jagt.statechart;

final class Lamp {

    enum Power { OFF, ON }

    enum Mode { DIM, BRIGHT }

    enum Stage { LOW, MID, HIGH }

    record Facts(Mode level) {
    }

    interface Event {
    }

    record Press() implements Event {
    }

    record Tick() implements Event {
    }

    record Hit() implements Event {
    }

    static final RegionId<Power> POWER = new RegionId<>("power", Power.class);
    static final RegionId<Mode> MODE = new RegionId<>("mode", Mode.class);
    static final RegionId<Stage> STAGE = new RegionId<>("stage", Stage.class);
    static final RegionId<Mode> LEVEL = new RegionId<>("level", Mode.class);

    private Lamp() {
    }
}
