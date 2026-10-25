package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record StandOutcome(String status, String owner, Duration took, List<String> tail, Path root, Ending ending,
                           String detail, String stopTrigger) {

    public enum Ending {
        REACHED("reached"), HELD("held by the human"), STALLED("stalled"), TIMED_OUT("timed out"),
        SUSPENDED("host suspended"), STOPPED("stopped, human needed");

        private final String words;

        Ending(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }

        static Ending of(boolean reached, boolean suspended, boolean stopped, boolean held, boolean stalled) {
            if (reached) {
                return REACHED;
            }
            if (suspended) {
                return SUSPENDED;
            }
            if (stopped) {
                return STOPPED;
            }
            if (held) {
                return HELD;
            }
            return stalled ? STALLED : TIMED_OUT;
        }
    }

    public boolean withTheHuman() {
        return owner.equals("YOU");
    }

    public boolean withTheCodeHost() {
        return owner.equals("CI");
    }

    public boolean stopped() {
        return ending == Ending.STOPPED;
    }

    public boolean suspended() {
        return ending == Ending.SUSPENDED;
    }

    public String describe() {
        return ending.words() + (detail.isEmpty() ? "" : " (" + detail + ")") + ": last status " + status + " owned by "
                + owner + " after " + took.toSeconds() + "s, root " + root + ", timeline tail:\n"
                + String.join("\n", tail);
    }
}
