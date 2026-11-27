package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.APPROVAL;
import static dev.jagt.orchestrator.flow.ChartRegions.CHECKS;
import static dev.jagt.orchestrator.flow.ChartRegions.DEPLOY;
import static dev.jagt.orchestrator.flow.ChartRegions.GIT;
import static dev.jagt.orchestrator.flow.ChartRegions.INFRA;
import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.REPO;
import static dev.jagt.orchestrator.flow.ChartRegions.REQUEST;
import static dev.jagt.orchestrator.flow.ChartRegions.regionEdge;
import static dev.jagt.orchestrator.flow.ChartRegions.takes;
import static dev.jagt.orchestrator.task.TaskStatus.DEPLOY_CONFLICT;

import dev.jagt.orchestrator.flow.deploy.ApprovalPhase;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.GitPhase;
import dev.jagt.orchestrator.flow.repo.RepoPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.statechart.StatechartBuilder;
import dev.jagt.statechart.TransitionBuilder;
import java.util.EnumSet;
import java.util.function.Predicate;

/** The regions of a task's delivery: deploy, git, repo, request, checks, approval and infra. */
final class DeliveryRegions {

  private final Landing land;

  DeliveryRegions(Landing land) {
    this.land = land;
  }

  void deploys(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(DEPLOY, DeployPhase.NONE);
    for (DeployPhase from : DeployPhase.values()) {
      deployed(
          chart, from, "opened", DeployPhase.LANDING, step -> step instanceof DeployStep.Opened);
    }
    for (DeployPhase from : EnumSet.of(DeployPhase.LANDING, DeployPhase.HANDED)) {
      deployed(
          chart,
          from,
          "landed",
          DeployPhase.LANDING,
          step ->
              step instanceof DeployStep.Merged
                  || step instanceof DeployStep.Fixed
                  || step instanceof DeployStep.Pushed
                  || step instanceof DeployStep.Blocked);
    }
    for (DeployPhase from : DeployPhase.values()) {
      deployed(
          chart,
          from,
          "conflicted",
          DeployPhase.HANDED,
          step -> step instanceof DeployStep.Conflicted);
    }
    for (DeployPhase from : DeployPhase.values()) {
      deployed(
          chart,
          from,
          "reverted",
          DeployPhase.REVERTING,
          step -> step instanceof DeployStep.Reverted);
    }
    deployed(
        chart,
        DeployPhase.HANDED,
        "discarded",
        DeployPhase.LANDING,
        step -> step instanceof DeployStep.Discarded);
    deployed(
        chart,
        DeployPhase.HANDED,
        "watched",
        DeployPhase.HANDED,
        step ->
            step instanceof DeployStep.Seen
                || step instanceof DeployStep.Referred
                || step instanceof DeployStep.Told
                || step instanceof DeployStep.Held);
    chart
        .onMatching(
            DEPLOY,
            DeployPhase.HANDED,
            TaskMove.RepoLeft.class,
            "conflicted repository left",
            move -> move instanceof TaskMove.RepoLeft left && left.leavesTheRecord())
        .emitFor(move ->
            new TaskEffect.Deploys(new DeployStep.Dropped(((TaskMove.RepoLeft) move).project())))
        .to(DeployPhase.LANDING);
    chart
        .onMatching(
            DEPLOY,
            DeployPhase.HANDED,
            TaskMove.DeployStepped.class,
            "conflict left",
            // A deploy step lands no status, so the guard alone already refuses it.
            move -> !(move instanceof TaskMove.DeployStepped))
        .when((active, move) ->
            active.state(LIFECYCLE) == DEPLOY_CONFLICT
                && land.land(DEPLOY_CONFLICT, move).filter(to -> to != DEPLOY_CONFLICT).isPresent())
        .emit(new TaskEffect.Deploys(new DeployStep.Cleared()))
        .to(DeployPhase.LANDING);
    deployed(
        chart,
        DeployPhase.LANDING,
        "asked",
        DeployPhase.CHECK_ASKED,
        step -> step instanceof DeployStep.Asked);
    for (DeployPhase at : EnumSet.of(DeployPhase.LANDING, DeployPhase.CHECK_ASKED)) {
      deployed(chart, at, "settled bare", at, step -> step instanceof DeployStep.Bare);
      deployed(chart, at, "read", at, step -> step instanceof DeployStep.ReadOn);
      deployed(chart, at, "read green", at, step -> step instanceof DeployStep.Green);
      deployed(chart, at, "retried", at, step -> step instanceof DeployStep.Retried);
    }
  }

  void gits(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(GIT, GitPhase.UNREAD);
    for (GitPhase from : GitPhase.values()) {
      TransitionBuilder<TaskMove, ChartFacts, TaskEffect, GitPhase> trusted =
          chart
              .onMatching(
                  GIT,
                  from,
                  TaskMove.GitTrusted.class,
                  "trusted",
                  move -> move instanceof TaskMove.GitTrusted)
              .emitFor(move -> new TaskEffect.TrustsGit(((TaskMove.GitTrusted) move).trust()));
      if (from == GitPhase.TRUSTED) {
        trusted.internal();
      } else {
        trusted.to(GitPhase.TRUSTED);
      }
    }
    chart
        .onMatching(
            GIT,
            GitPhase.TRUSTED,
            TaskMove.GitChanged.class,
            "changed",
            move -> move instanceof TaskMove.GitChanged)
        .emitFor(move -> new TaskEffect.ParksGit(((TaskMove.GitChanged) move).changed()))
        .to(GitPhase.CHANGED);
    chart
        .onMatching(
            GIT,
            GitPhase.CHANGED,
            TaskMove.GitChanged.class,
            "changed again",
            move -> move instanceof TaskMove.GitChanged)
        .emitFor(move -> new TaskEffect.ParksGit(((TaskMove.GitChanged) move).changed()))
        .internal();
    for (GitPhase from : EnumSet.of(GitPhase.TRUSTED, GitPhase.CHANGED)) {
      chart
          .onMatching(
              GIT,
              from,
              TaskMove.GitRepoJoined.class,
              "repository joined",
              move -> move instanceof TaskMove.GitRepoJoined)
          .emitFor(move -> new TaskEffect.JoinsGit(((TaskMove.GitRepoJoined) move).added()))
          .internal();
    }
  }

  void repos(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(REPO, RepoPhase.NONE);
    chart
        .onMatching(
            REPO,
            RepoPhase.NONE,
            TaskMove.RepoAsked.class,
            "asked",
            move -> move instanceof TaskMove.RepoAsked)
        .emitFor(move -> new TaskEffect.AsksRepo(((TaskMove.RepoAsked) move).project()))
        .to(RepoPhase.ASKED);
    chart
        .onMatching(
            REPO,
            RepoPhase.ASKED,
            TaskMove.RepoAsked.class,
            "asked again",
            move -> move instanceof TaskMove.RepoAsked)
        .emitFor(move -> new TaskEffect.AsksRepo(((TaskMove.RepoAsked) move).project()))
        .internal();
    chart
        .onMatching(
            REPO,
            RepoPhase.ASKED,
            TaskMove.RepoUnasked.class,
            "asked no more",
            new TaskMove.RepoUnasked()::equals)
        .emit(new TaskEffect.DropsRepoRequest())
        .to(RepoPhase.NONE);
    chart
        .onMatching(
            REPO,
            RepoPhase.ASKED,
            TaskMove.RepoAttached.class,
            "attached",
            move -> move instanceof TaskMove.RepoAttached)
        .emitFor(move -> new TaskEffect.AttachesRepo(((TaskMove.RepoAttached) move).repo()))
        .to(RepoPhase.NONE);
    for (RepoPhase from : RepoPhase.values()) {
      chart
          .onMatching(
              REPO, from, TaskMove.Placed.class, "placed", move -> move instanceof TaskMove.Placed)
          .emitFor(move ->
              new TaskEffect.Places(
                  ((TaskMove.Placed) move).repos(), ((TaskMove.Placed) move).baseBranch()))
          .to(RepoPhase.NONE);
    }
    chart
        .onMatching(
            REPO,
            RepoPhase.NONE,
            TaskMove.RepoAttached.class,
            "attached unasked",
            move -> move instanceof TaskMove.RepoAttached)
        .emitFor(move -> new TaskEffect.AttachesRepo(((TaskMove.RepoAttached) move).repo()))
        .internal();
  }

  void requests(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(REQUEST, RequestPhase.NONE);
    for (RequestPhase from : RequestPhase.values()) {
      regionEdge(
          chart,
          REQUEST,
          from,
          "linked, open",
          takes(
              TaskMove.RequestLinked.class,
              move -> move instanceof TaskMove.RequestLinked linked && linked.opens()),
          DeliveryRegions::linksRequests,
          RequestPhase.OPEN);
      regionEdge(
          chart,
          REQUEST,
          from,
          "linked, standing",
          takes(
              TaskMove.RequestLinked.class,
              move -> move instanceof TaskMove.RequestLinked linked && !linked.opens()),
          DeliveryRegions::linksRequests,
          from);
    }
    chart
        .onMatching(
            REQUEST,
            RequestPhase.NONE,
            TaskMove.RepoAttached.class,
            "repository added with a request",
            DeliveryRegions::attachesRequest)
        .to(RequestPhase.OPEN);
    chart
        .onMatching(
            REQUEST,
            RequestPhase.MERGED,
            TaskMove.RepoAttached.class,
            "repository added with a request merged",
            DeliveryRegions::attachesMergedRequest)
        .internal();
    chart
        .onMatching(
            REQUEST,
            RequestPhase.MERGED,
            TaskMove.RepoAttached.class,
            "repository added without one",
            move -> move instanceof TaskMove.RepoAttached)
        .to(RequestPhase.OPEN);
    for (RequestPhase from : RequestPhase.values()) {
      for (RequestPhase to : RequestPhase.values()) {
        regionEdge(
            chart,
            REQUEST,
            from,
            "repository left",
            takes(
                TaskMove.RepoLeft.class,
                move -> move instanceof TaskMove.RepoLeft left && left.after() == to),
            move -> new TaskEffect.LeavesRepo(((TaskMove.RepoLeft) move).project()),
            to);
      }
    }
    regionEdge(
        chart,
        REQUEST,
        RequestPhase.OPEN,
        "merged (all)",
        takes(ChartRegions.MERGE_READS, DeliveryRegions::completesMerge),
        DeliveryRegions::mergesRequest,
        RequestPhase.MERGED);
    regionEdge(
        chart,
        REQUEST,
        RequestPhase.OPEN,
        "merge read (some)",
        takes(ChartRegions.MERGE_READS, DeliveryRegions::readsMerge),
        DeliveryRegions::mergesRequest,
        RequestPhase.OPEN);
    regionEdge(
        chart,
        REQUEST,
        RequestPhase.MERGED,
        "merged (all)",
        takes(ChartRegions.MERGE_READS, DeliveryRegions::completesMerge),
        DeliveryRegions::mergesRequest,
        RequestPhase.MERGED);
    regionEdge(
        chart,
        REQUEST,
        RequestPhase.MERGED,
        "unmerged (some)",
        takes(ChartRegions.MERGE_READS, DeliveryRegions::readsMerge),
        DeliveryRegions::mergesRequest,
        RequestPhase.OPEN);
    regionEdge(
        chart,
        REQUEST,
        RequestPhase.NONE,
        "merge recorded unrequested",
        takes(ChartRegions.MERGE_READS, DeliveryRegions::readsMerge),
        DeliveryRegions::mergesRequest,
        RequestPhase.NONE);
  }

  private static boolean attachesRequest(TaskMove move) {
    return move instanceof TaskMove.RepoAttached attached && attached.repo().hasReviewRequest();
  }

  private static boolean attachesMergedRequest(TaskMove move) {
    return attachesRequest(move) && ((TaskMove.RepoAttached) move).repo().wasMerged();
  }

  private static boolean readsMerge(TaskMove move) {
    return move instanceof TaskMove.MergedByHost || move instanceof TaskMove.MergedByGit;
  }

  private static boolean completesMerge(TaskMove move) {
    return (move instanceof TaskMove.MergedByHost host && host.completes())
        || (move instanceof TaskMove.MergedByGit git && git.completes());
  }

  private static TaskEffect linksRequests(TaskMove move) {
    TaskMove.RequestLinked linked = (TaskMove.RequestLinked) move;
    return new TaskEffect.LinksRequests(linked.byProject(), linked.url(), linked.newRound());
  }

  private static TaskEffect mergesRequest(TaskMove move) {
    return move instanceof TaskMove.MergedByGit git
        ? new TaskEffect.MergesRequest(git.project(), git.merged())
        : new TaskEffect.MergesRequestByHost(((TaskMove.MergedByHost) move).project());
  }

  void checks(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(CHECKS, ChecksPhase.NONE);
    for (ChecksPhase from : ChecksPhase.values()) {
      regionEdge(
          chart,
          CHECKS,
          from,
          "read on the head",
          takes(
              TaskMove.ChecksRead.class,
              move -> move instanceof TaskMove.ChecksRead read && read.onHead()),
          DeliveryRegions::readsChecks,
          ChecksPhase.READ);
      regionEdge(
          chart,
          CHECKS,
          from,
          "read on another head",
          takes(
              TaskMove.ChecksRead.class,
              move -> move instanceof TaskMove.ChecksRead read && !read.onHead()),
          DeliveryRegions::readsChecks,
          ChecksPhase.STALE);
      regionEdge(
          chart,
          CHECKS,
          from,
          "unread",
          takes(TaskMove.ChecksUnread.class, move -> move instanceof TaskMove.ChecksUnread),
          move -> new TaskEffect.FailsToReadChecks(((TaskMove.ChecksUnread) move).read()),
          ChecksPhase.UNREAD);
    }
    for (ChecksPhase from : ChecksPhase.values()) {
      regionEdge(
          chart,
          CHECKS,
          from,
          "a new round",
          takes(TaskMove.RequestLinked.class, ChartRegions::beginsRound),
          move -> new TaskEffect.ResetsChecks(),
          ChecksPhase.NONE);
    }
    for (ChecksPhase from : EnumSet.of(ChecksPhase.NONE, ChecksPhase.UNREAD)) {
      regionEdge(
          chart,
          CHECKS,
          from,
          "head seen",
          takes(TaskMove.HeadSeen.class, move -> move instanceof TaskMove.HeadSeen),
          DeliveryRegions::seesHead,
          from);
    }
    for (ChecksPhase from : EnumSet.of(ChecksPhase.READ, ChecksPhase.STALE)) {
      regionEdge(
          chart,
          CHECKS,
          from,
          "head seen, reads of another",
          takes(
              TaskMove.HeadSeen.class,
              move -> move instanceof TaskMove.HeadSeen seen && seen.staleReads()),
          DeliveryRegions::seesHead,
          ChecksPhase.STALE);
      regionEdge(
          chart,
          CHECKS,
          from,
          "head seen, reads of it",
          takes(
              TaskMove.HeadSeen.class,
              move -> move instanceof TaskMove.HeadSeen seen && !seen.staleReads()),
          DeliveryRegions::seesHead,
          ChecksPhase.READ);
    }
  }

  private static TaskEffect readsChecks(TaskMove move) {
    return new TaskEffect.ReadsChecks(
        ((TaskMove.ChecksRead) move).hostStatus(), ((TaskMove.ChecksRead) move).tip());
  }

  private static TaskEffect seesHead(TaskMove move) {
    return new TaskEffect.SeesHead(((TaskMove.HeadSeen) move).tip());
  }

  private static TaskEffect readsApproval(TaskMove move) {
    return new TaskEffect.ReadsApproval(((TaskMove.ApprovalRead) move).approved());
  }

  void approvals(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(APPROVAL, ApprovalPhase.UNREAD);
    for (ApprovalPhase from : ApprovalPhase.values()) {
      regionEdge(
          chart,
          APPROVAL,
          from,
          "a new round",
          takes(TaskMove.RequestLinked.class, ChartRegions::beginsRound),
          move -> new TaskEffect.ResetsApproval(),
          ApprovalPhase.UNREAD);
      regionEdge(
          chart,
          APPROVAL,
          from,
          "approved",
          takes(
              TaskMove.ApprovalRead.class,
              move -> move instanceof TaskMove.ApprovalRead read && read.approved()),
          DeliveryRegions::readsApproval,
          ApprovalPhase.APPROVED);
      regionEdge(
          chart,
          APPROVAL,
          from,
          "withheld",
          takes(
              TaskMove.ApprovalRead.class,
              move -> move instanceof TaskMove.ApprovalRead read && !read.approved()),
          DeliveryRegions::readsApproval,
          ApprovalPhase.WITHHELD);
    }
  }

  void infras(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(INFRA, InfraPhase.NONE);
    for (InfraPhase from : InfraPhase.values()) {
      regionEdge(
          chart,
          INFRA,
          from,
          "a new round",
          takes(TaskMove.RequestLinked.class, ChartRegions::beginsRound),
          move -> new TaskEffect.ResetsInfra(),
          InfraPhase.NONE);
      regionEdge(
          chart,
          INFRA,
          from,
          "armed",
          takes(TaskMove.InfraArmed.class, move -> move instanceof TaskMove.InfraArmed),
          move -> new TaskEffect.ArmsInfra(((TaskMove.InfraArmed) move).retry()),
          InfraPhase.ARMED);
      regionEdge(
          chart,
          INFRA,
          from,
          "cleared with the sent",
          takes(
              TaskMove.InfraCleared.class,
              move -> move instanceof TaskMove.InfraCleared clear && clear.all()),
          move -> new TaskEffect.ClearsInfra(true),
          InfraPhase.NONE);
      if (from != InfraPhase.ARMED) {
        regionEdge(
            chart,
            INFRA,
            from,
            "sent one cleared",
            takes(
                TaskMove.InfraCleared.class,
                move -> move instanceof TaskMove.InfraCleared clear && !clear.all()),
            move -> new TaskEffect.ClearsInfra(false),
            from);
      }
      regionEdge(
          chart,
          INFRA,
          from,
          "sent",
          takes(
              TaskMove.InfraSent.class,
              move ->
                  move instanceof TaskMove.InfraSent sent
                      && sent.retry().attempt() < sent.retry().of()),
          move -> new TaskEffect.SendsInfra(((TaskMove.InfraSent) move).retry()),
          InfraPhase.SENT);
      regionEdge(
          chart,
          INFRA,
          from,
          "last wait sent",
          takes(
              TaskMove.InfraSent.class,
              move ->
                  move instanceof TaskMove.InfraSent sent
                      && sent.retry().attempt() >= sent.retry().of()),
          move -> new TaskEffect.SendsInfra(((TaskMove.InfraSent) move).retry()),
          InfraPhase.SPENT);
    }
    for (InfraPhase unarmed : EnumSet.of(InfraPhase.NONE, InfraPhase.SENT, InfraPhase.SPENT)) {
      regionEdge(
          chart,
          INFRA,
          InfraPhase.ARMED,
          "armed one cleared, " + unarmed + " remains",
          takes(
              TaskMove.InfraCleared.class,
              move ->
                  move instanceof TaskMove.InfraCleared clear
                      && !clear.all()
                      && clear.unarmed() == unarmed),
          move -> new TaskEffect.ClearsInfra(false),
          unarmed);
      regionEdge(
          chart,
          INFRA,
          InfraPhase.ARMED,
          "head moved, " + unarmed + " remains",
          takes(
              TaskMove.HeadSeen.class,
              move ->
                  move instanceof TaskMove.HeadSeen seen
                      && seen.moved()
                      && seen.unarmed() == unarmed),
          move -> new TaskEffect.DisarmsInfra(),
          unarmed);
    }
  }

  private void deployed(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      DeployPhase from,
      String label,
      DeployPhase to,
      Predicate<DeployStep> matches) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, DeployPhase> edge =
        chart
            .onMatching(
                DEPLOY,
                from,
                TaskMove.DeployStepped.class,
                label,
                move ->
                    move instanceof TaskMove.DeployStepped stepped && matches.test(stepped.step()))
            .emitFor(move -> new TaskEffect.Deploys(((TaskMove.DeployStepped) move).step()));
    if (from == to) {
      edge.internal();
    } else {
      edge.to(to);
    }
  }
}
