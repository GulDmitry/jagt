package dev.jagt.orchestrator.task.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.HumanWait;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The work in flight: the session, whose turn it is, what stopped it, what it deployed, who checked
 * it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Work(
    // Absent in a file written before the session was recorded, which reads as no sign yet.
    Session session,
    // Absent in a file written before the turn existed, which reads as no turn open.
    @Nullable Turn turn,
    // Every stretch the human held the turn, oldest first; the last may still stand.
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<HumanWait> waits,
    // Set by jagt alone, cleared by a human's lift alone.
    @Nullable TaskStop stop,
    // The project key a session asked to have a repository of, until one is added or it reports
    // anything else.
    @Nullable String repoRequest,
    // Absent in a file written before the deployment was recorded.
    @Nullable Deployment deployment,
    // What each checker left on the task, by role id; empty until one read or judged.
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, RoleRecord> roles,
    // What the Master left on the task; absent until it left, answered or sealed one.
    @Nullable Master master) {

  public Work {
    session = session == null ? Session.defaults() : session;
    waits = waits == null ? List.of() : List.copyOf(waits);
    roles = roles == null ? Map.of() : Map.copyOf(roles);
  }

  public boolean stopped() {
    return stop != null;
  }

  public boolean paused() {
    return stop != null && stop.trigger().ends() == TaskStop.Trigger.Ends.PAUSE;
  }

  public boolean asksForRepository() {
    return repoRequest != null;
  }

  /** A repository whose change is live on a deploy branch until a revert takes it out. */
  public boolean landedIn(String project) {
    return deployment != null
        && deployment.targets().stream()
            .anyMatch(target -> target.project().equals(project) && target.landed());
  }

  /** The role passed the tips the deployment stands at now. */
  public boolean passedOnTips(String role) {
    return deployment != null && roleOrNone(role).passedOn(deployment.tips());
  }

  /** A round of the role went out on the deploy now live and the session has not yet settled it. */
  public boolean roundOpen(String role) {
    return deployment != null
        && deployment.askedDigest() != null
        && deployment.askedDigest().equals(roleOrNone(role).roundDigest());
  }

  public RoleRecord roleOrNone(String role) {
    return roles.getOrDefault(role, RoleRecord.defaults());
  }

  public Master arbiterOrNone() {
    return master == null ? Master.defaults() : master;
  }

  public Turn turnOrNone() {
    return turn == null ? Turn.none() : turn;
  }

  public @Nullable ModelTier tier() {
    return session.tier();
  }

  public Work withStop(TaskStop changed) {
    return new Work(session, turn, waits, changed, repoRequest, deployment, roles, master);
  }

  public Work lifted() {
    return new Work(session, turn, waits, null, repoRequest, deployment, roles, master);
  }

  public Work withRepoRequest(@Nullable String project) {
    return new Work(session, turn, waits, stop, project, deployment, roles, master);
  }

  public Work withMaster(Master changed) {
    return new Work(
        session,
        turn,
        waits,
        stop,
        repoRequest,
        deployment,
        roles,
        changed.isEmpty() ? null : changed);
  }

  public Work withRole(String role, RoleRecord record) {
    Map<String, RoleRecord> kept = new TreeMap<>(roles);
    if (record.isEmpty()) {
      kept.remove(role);
    } else {
      kept.put(role, record);
    }
    return new Work(session, turn, waits, stop, repoRequest, deployment, kept, master);
  }

  public Work withTurn(Turn changed) {
    return new Work(session, changed, waits, stop, repoRequest, deployment, roles, master);
  }

  public Work withDeployment(Deployment changed) {
    return new Work(session, turn, waits, stop, repoRequest, changed, roles, master);
  }

  public Work withWaits(List<HumanWait> changed) {
    return new Work(session, turn, changed, stop, repoRequest, deployment, roles, master);
  }

  public Work withSession(Session changed) {
    return new Work(changed, turn, waits, stop, repoRequest, deployment, roles, master);
  }
}
