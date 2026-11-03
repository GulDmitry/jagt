package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;

import java.util.Map;

/** What can happen to a task's status, named by the door it came through. */
sealed interface TaskMove {

    record Ran(TaskAction action, Outcome.Kind outcome) implements TaskMove {
    }

    /** Whether a human may run {@code action} now; no edge on it changes a state or emits an effect. */
    record Offers(TaskAction action) implements TaskMove {
    }

    record Reported(TaskStatus status, boolean verificationOwed) implements TaskMove {
    }

    /** A note on what the Master left; {@code after} is where the master region then stands. */
    record MasterNoted(MasterNote note, MasterPhase after) implements TaskMove {
    }

    /** What a checker did to the deploy; only the region of the role it names takes it. */
    sealed interface Checked extends TaskMove {

        String role();

        /** A session's no_changes answering the role's round: the deployed change stays as it was. */
        record RoundDeclined(String role, AgentReport means, TaskStatus reported) implements Checked {
        }

        /** The role's read of a step ended; {@code holds} where the turn it opened still stands. */
        record ReadEnded(String role, boolean holds) implements Checked {
        }

        /** The role passed the deploy standing at {@code sha}; {@code standing} where its region then reads a pass. */
        record Passed(String role, String sha, boolean standing) implements Checked {
        }

        /** The role sent a round on the deploy asked on {@code digest}, carrying {@code rows}; {@code opens} where that is the deploy's. */
        record RoundSent(String role, String digest, String rows, boolean opens) implements Checked {
        }
    }

    record Read(TaskStatus concluded) implements TaskMove {
    }

    record DeployRead(boolean redOnTheCode) implements TaskMove {
    }

    record Verified(boolean passed) implements TaskMove {
    }

    record StopSet(TaskStop stop) implements TaskMove {
    }

    record Lifted() implements TaskMove {
    }

    record StopEnded(TaskStop.Trigger.Ends ends) implements TaskMove {
    }

    /** A request written that leaves out one the stop was counted on. */
    record RequestReplaced() implements TaskMove {
    }

    record PlanAsked(boolean planMode) implements TaskMove {
    }

    record PlanApproved() implements TaskMove {
    }

    record NoPlanDeclared() implements TaskMove {
    }

    record Autoplanned() implements TaskMove {
    }

    /** A session's report meaning {@code means}; a status that holds its line keeps a report that is not a question. */
    record Said(AgentReport means) implements TaskMove {
    }

    /** A verb's stamp meaning {@code means}, set whatever the status. */
    record Stamped(AgentReport means) implements TaskMove {
    }

    /** A relay handing the turn over; {@code answered} fingerprints what the session had last reported. */
    record Relayed(String step, String digest, String answered) implements TaskMove {
    }

    /** A report, or a refusal, closing the turn it answers. */
    record TurnClosed() implements TaskMove {
    }

    /** A relay that failed to send, put back to the turn it found. */
    record TurnRestored(Turn before) implements TaskMove {
    }

    record DeployStepped(DeployStep step) implements TaskMove {
    }

    record Signed(SessionSign sign) implements TaskMove {
    }

    /** A session started over: nothing the old one signed speaks for the new one. */
    record SessionStartedOver() implements TaskMove {
    }

    /** The git state a human adopted, or a first check found and took as it stands. */
    record GitTrusted(GitTrust trust) implements TaskMove {
    }

    /** A check found git obeying something other than what was trusted. */
    record GitChanged(String changed) implements TaskMove {
    }

    /** An added repository's entries joining what was trusted. */
    record GitRepoJoined(GitTrust added) implements TaskMove {
    }

    /** A report naming the repository the session needs. */
    record RepoAsked(String project) implements TaskMove {
    }

    /** A report that needs no repository. */
    record RepoUnasked() implements TaskMove {
    }

    /** A repository added to the task, which ends any request. */
    record RepoAttached(TaskRepo repo) implements TaskMove {
    }

    /**
     * A repository whose request the host closed unmerged, taken out of the task; {@code after} is where the request
     * region then stands, {@code endsConflict} where it was the repository a deploy handed back for its pipeline.
     */
    record RepoLeft(String project, RequestPhase after, boolean endsConflict) implements TaskMove {
    }

    /**
     * A report's request links: one per repository, or the one {@code url} on the primary where {@code byProject} is
     * empty. {@code relinks} where a repository's record then differs, {@code opens} where the requests then stand open.
     */
    record RequestLinked(Map<String, String> byProject, String url, boolean newRound, boolean relinks, boolean opens)
            implements TaskMove {
    }

    /**
     * What git found of whether a repository's request is merged into its target; {@code completes} where every
     * repository's request is then merged.
     */
    record MergedByGit(String project, boolean merged, boolean completes) implements TaskMove {
    }

    /** The host's word that a repository's request is merged; {@code completes} as in {@link MergedByGit}. */
    record MergedByHost(String project, boolean completes) implements TaskMove {
    }

    /**
     * The newest commit a read saw pushed; {@code moved} where it is not the head the task already had,
     * {@code staleReads} where the checks it read are then not of the head, {@code unarmed} where the infra region
     * stands once an armed re-run is gone.
     */
    record HeadSeen(String tip, boolean moved, boolean staleReads, InfraPhase unarmed) implements TaskMove {
    }

    /** What a read listed of the checks on {@code tip}, {@code onHead} where that is the head the task has seen. */
    record ChecksRead(String hostStatus, String tip, boolean onHead) implements TaskMove {
    }

    /** A round that could not list the checks. */
    record ChecksUnread(dev.jagt.orchestrator.task.ChecksUnread read) implements TaskMove {
    }

    /** What the host said of the approval. */
    record ApprovalRead(boolean approved) implements TaskMove {
    }

    /** A re-run armed for a pipeline that died on infrastructure. */
    record InfraArmed(InfraRetry retry) implements TaskMove {
    }

    /** The armed re-run forgotten, and the sent ones too where {@code all}; {@code unarmed} as in {@link HeadSeen}. */
    record InfraCleared(boolean all, InfraPhase unarmed) implements TaskMove {
    }

    /** The re-run sent. */
    record InfraSent(InfraRetry retry) implements TaskMove {
    }
}
