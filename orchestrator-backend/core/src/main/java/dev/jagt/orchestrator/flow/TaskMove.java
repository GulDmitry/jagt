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

    record Reported(TaskStatus status, boolean verificationOwed) implements TaskMove {
    }

    /** A session's no_changes answering a QA round: the deployed change stays as it was. */
    record QaDeclined() implements TaskMove {
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

    /** A report's request links: one per repository, or the one {@code url} on the primary where {@code byProject} is empty. */
    record RequestLinked(Map<String, String> byProject, String url, boolean newRound) implements TaskMove {
    }

    /** What git found of whether a repository's request is merged into its target. */
    record MergedByGit(String project, boolean merged) implements TaskMove {
    }

    /** The host's word that a repository's request is merged. */
    record MergedByHost(String project) implements TaskMove {
    }

    /** The newest commit a read saw pushed; {@code moved} where it is not the head the task already had. */
    record HeadSeen(String tip, boolean moved) implements TaskMove {
    }

    /** What a read listed of the checks on {@code tip}. */
    record ChecksRead(String hostStatus, String tip) implements TaskMove {
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

    /** The armed re-run forgotten, and the sent ones too where {@code all}. */
    record InfraCleared(boolean all) implements TaskMove {
    }

    /** The re-run sent. */
    record InfraSent(InfraRetry retry) implements TaskMove {
    }
}
