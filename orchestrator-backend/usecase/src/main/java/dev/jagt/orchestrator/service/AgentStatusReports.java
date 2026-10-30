package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.PlanProbes;
import dev.jagt.orchestrator.protocol.Reported;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What an agent says about its own task. WHICH task a caller may touch is already decided upstream.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentStatusReports {

    private static final int MAX_MESSAGE = 100;

    private final StateService stateService;
    private final Notifications notifications;
    private final FlowReports flow;
    private final HandBack handBack;
    private final ConfigService configService;

    /** For jagt's own reports, which carry no outcome of an agent's and no request to link. */
    public String report(TaskStatus status, String message, String taskId) {
        AgentStatusMessage said = new AgentStatusMessage(status.name(), message, null, null, Map.of());
        return report(accepted(said, taskId), taskId, handBack::verificationOwed, false);
    }

    /** A verification run's own verdict, which owes no further run. */
    public String verified(boolean passed, String message, String taskId) {
        TaskStatus verdict = FlowRules.verified(passed);
        AgentStatusMessage said = new AgentStatusMessage(verdict.name(), message, null, null, Map.of());
        String answer = report(accepted(said, taskId), taskId, task -> false, false);
        TaskStatus landed = stateService.task(taskId).map(TaskState::status).orElse(null);
        // A measured verdict the flow turns aside leaves the task held, and the run repeats with nothing to say why.
        if (landed != verdict) {
            log.atError().setMessage("verdict not landed")
                    .addKeyValue("task", taskId)
                    .addKeyValue("status", landed)
                    .addKeyValue("cause", "verdict " + verdict + " turned aside")
                    .log();
        }
        return answer;
    }

    /**
     * A session's message that reached the door, closing the turn it answers. It is judged here as well as at the
     * transport, because jagt's own reports and a session's come through the same door and only one of them crossed a
     * wire; the reason is the wire's alone.
     */
    public String report(AgentStatusMessage said, String taskId) {
        return report(accepted(said, taskId), taskId, handBack::verificationOwed, true);
    }

    private Reported accepted(AgentStatusMessage said, String taskId) {
        MessageContext context = contextFor(taskId);
        List<Violation> violations = said.reportViolations(context);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(Message.refusal(violations));
        }
        return said.accepted(context).orElseThrow();
    }

    /** What the rules may ask about the task this message is about. */
    public MessageContext contextFor(String taskId) {
        return new MessageContext(known(taskId), stateService.task(taskId)
                .filter(task -> task.planGate() == PlanGate.REQUIRED).isPresent(),
                List.copyOf(configService.load().projects().keySet()));
    }

    /** As {@link #contextFor}, for a session's own report: its first one owes a judgement on each switch that is on. */
    public MessageContext sessionContextFor(String taskId) {
        boolean first = stateService.task(taskId).filter(task -> task.status() == TaskStatus.NEW).isPresent();
        return contextFor(taskId).withSwitchesOwed(first ? configService.load().agent().switchesOn() : List.of());
    }

    /** The task's repositories, primary first: which link a human follows first is the session's own. */
    private List<String> known(String taskId) {
        return stateService.task(taskId).map(TaskState::projects).orElse(List.of());
    }

    private String report(Reported said, String taskId, Predicate<TaskState> verificationOwed, boolean closesTurn) {
        Optional<TaskState> current = stateService.task(taskId);
        TaskStatus newStatus = said.status();
        Map<String, String> requestsByProject = said.requests();
        AgentReport means = measured(said, current, taskId);
        String shortMessage = OneLine.of(said.detail(), MAX_MESSAGE);
        String url = said.link();
        // What the machine lets this report land on; the agent is told, not left reading its own word back.
        FlowReports.Landed written = flow.report(taskId, newStatus, means, shortMessage,
                (was, next) -> closed(signed(planned(was, linked(was, next, url, requestsByProject))
                        .withRepoRequest(said.needsProject())
                        .judged(said.switches())), closesTurn), verificationOwed)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
        if (url != null && FlowRules.outForReview(written.now())) {
            stateService.task(taskId).map(handBack::pushedTips)
                    .ifPresent(tip -> stateService.updateTask(taskId, task -> task.withHead(tip)));
        }
        if (said.noPlan()) {
            stateService.updateTask(taskId, TaskReducer::noPlanDeclared);
        }
        TaskStatus previous = written.previous();
        TaskStatus landed = written.now();
        // An agent that stops to ask usually keeps its status, so the message is the only thing that changed.
        boolean askedNow = means.asks() && !current.map(TaskState::report).orElse(AgentReport.PLAIN).asks();
        logChange(taskId, current.map(TaskState::alias).orElse(null), previous, landed, shortMessage, means, askedNow);
        logReplacedStop(taskId, current);
        return answer(taskId, newStatus, landed, shortMessage);
    }

    private void logReplacedStop(String taskId, Optional<TaskState> before) {
        TaskStop standing = before.map(TaskState::stop).orElse(null);
        if (standing != null && stateService.task(taskId).filter(TaskState::stopped).isEmpty()) {
            log.atInfo().setMessage("stop lifted").addKeyValue("task", taskId)
                    .addKeyValue("trigger", standing.trigger())
                    .addKeyValue("cause", "new request")
                    .addKeyValue("request", standing.request())
                    .log();
        }
    }

    private void logChange(String taskId, @Nullable String alias, TaskStatus previous, TaskStatus landed,
                           @Nullable String shortMessage, AgentReport means, boolean askedNow) {
        if (landed != previous) {
            log.atInfo().setMessage("agent status changed").addKeyValue("task", taskId)
                    .addKeyValue("alias", alias)
                    .addKeyValue("status", landed)
                    .addKeyValue("previous", previous)
                    .addKeyValue("said", shortMessage == null ? "" : shortMessage)
                    .log();
        } else if (askedNow) {
            log.atInfo().setMessage("agent asked").addKeyValue("task", taskId)
                    .addKeyValue("alias", alias)
                    .addKeyValue("status", landed)
                    .addKeyValue("said", shortMessage)
                    .log();
        } else if (FlowRules.holdsItsLine(landed) && !means.asks()) {
            log.atInfo().setMessage("agent line held off the card").addKeyValue("task", taskId)
                    .addKeyValue("alias", alias)
                    .addKeyValue("status", landed)
                    .addKeyValue("said", shortMessage == null ? "" : shortMessage)
                    .log();
        }
    }

    private String answer(String taskId, TaskStatus asked, TaskStatus landed, @Nullable String shortMessage) {
        String said = shortMessage == null ? "" : " (" + shortMessage + ")";
        if (landed != asked && !FlowRules.awaitingVerification(landed)) {
            return "Task " + taskId + " stays " + landed + ": that one is a human's to move on from. Your line"
                    + " was recorded" + said;
        }
        String next = FlowRules.awaitingVerification(landed)
                ? "; verification runs first, and the Master reads this round once it passes; end your turn"
                : "; the Master reads this round next; end your turn";
        return "Task " + taskId + " -> " + landed + said
                + (FlowRules.headedForTheMaster(landed) && handBack.masterReads() ? next : "");
    }

    private static TaskState signed(TaskState next) {
        return TaskReducer.signed(next, new SessionSign.Reported(), next.lastActiveTimestamp());
    }

    private TaskState closed(TaskState next, boolean closesTurn) {
        if (!closesTurn) {
            return next;
        }
        return TaskTurns.closed(next);
    }

    private static TaskState linked(TaskStatus was, TaskState next, @Nullable String url,
                                    Map<String, String> requestsByProject) {
        if (url == null) {
            return next;
        }
        // Repeating CI_POLLING on the request already carried is the same round.
        boolean sameRound = FlowRules.outForReview(was) && (requestsByProject.isEmpty()
                ? url.equals(next.mrUrl())
                : requestsByProject.entrySet().stream().allMatch(request ->
                        next.reviewRequestOf(request.getKey()).filter(request.getValue()::equals).isPresent()));
        boolean newRound = FlowRules.outForReview(next.status()) && !sameRound;
        long now = next.lastActiveTimestamp();
        if (!requestsByProject.isEmpty()) {
            return TaskReducer.relinked(newRound ? next.withReviewRound(requestsByProject, now)
                    : next.withMrUrls(requestsByProject, now), now);
        }
        return TaskReducer.relinked(newRound ? next.withReviewRound(url, now) : next.withMrUrl(url, now), now);
    }

    /** Read as the plan is put up, not after: what VERIFYING runs is what was ratified. */
    private TaskState planned(TaskStatus was, TaskState next) {
        if (FlowRules.holdsAPlan(was) || !FlowRules.holdsAPlan(next.status())) {
            return next;
        }
        PlanProbes.Read read = handBack.planProbes(next);
        if (!read.violations().isEmpty()) {
            throw new IllegalArgumentException(Message.refusal(read.violations()));
        }
        return next.withProbes(read.probes());
    }

    /** Puts what {@link FlowRules#readReview} concluded on the board; a red was tapped when the round read it. */
    public void markRead(String taskId, TaskStatus read, Optional<TaskStop> stopped) {
        String message = switch (read) {
            case REVIEWED -> "reviewed — checks green, no unresolved comments";
            case APPROVED -> "approved — checks green, request approved";
            case CI_FAILED -> stopped.map(stop -> "checks failed — jagt stopped the task: " + stop.reading())
                    .orElse("checks failed — relayed to the session");
            default -> throw new IllegalArgumentException(read + " is not what a review read concludes");
        };
        String id = stateService.canonicalTaskId(taskId);
        flow.read(id, read, message);
    }

    public String notifyUser(String title, String message) {
        notifications.send(Notification.fromAgent(null, title, message));
        return "Notification sent";
    }

    /**
     * What this report means. NO_CHANGES is CHECKED: it is the one claim jagt can measure, and a round that edited
     * files is a diff for the human to read.
     */
    private AgentReport measured(Reported said, Optional<TaskState> task, String taskId) {
        if (said.claimed() == AgentReport.NO_CHANGES && task.filter(handBack::anyUncommitted).isPresent()) {
            log.atInfo().setMessage("report overruled").addKeyValue("task", taskId)
                    .addKeyValue("alias", task.get().alias())
                    .addKeyValue("cause", "uncommitted changes")
                    .addKeyValue("effect", "round recorded with a diff")
                    .log();
            return AgentReport.PLAIN;
        }
        return said.claimed();
    }
}
