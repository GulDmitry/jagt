package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Master;

/** One thing written to what the Master left on a task. */
sealed interface MasterNote {

    Master applyTo(Master master);

    record Left(String step) implements MasterNote {

        @Override
        public Master applyTo(Master master) {
            return master.withLeft(step);
        }
    }

    record Took() implements MasterNote {

        @Override
        public Master applyTo(Master master) {
            return master.withLeft("");
        }
    }

    record Unanswered(String digest) implements MasterNote {

        @Override
        public Master applyTo(Master master) {
            return master.withUnanswered(digest);
        }
    }

    record PressedDeploy(String arrival) implements MasterNote {

        @Override
        public Master applyTo(Master master) {
            return master.withPressedDeploy(arrival);
        }
    }

    record Sealed(String seal, long round) implements MasterNote {

        @Override
        public Master applyTo(Master master) {
            return master.withSeal(seal, round);
        }
    }
}
