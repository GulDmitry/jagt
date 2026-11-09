package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import java.util.Optional;

/**
 * A code host that is never asked: no failed job to read, no pipeline after a commit, nothing to be
 * missing.
 */
abstract class OfflineCodeHost implements CodeHostDriver {

  @Override
  public Answer<List<FailedJob>> failedJobs(HostSettings host, String pipelineUrl) {
    return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
  }

  @Override
  public Answer<List<String>> unresolvedNotes(HostSettings host, String requestUrl) {
    return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
  }

  @Override
  public Answer<String> requestFingerprint(HostSettings host, String requestUrl) {
    return Answer.unavailable();
  }

  @Override
  public Answer<CommitPipeline> onBranchSince(
      HostSettings host, String remoteUrl, String branch, String sha) {
    return new Answer<>(Optional.of(new CommitPipeline("none", "")), TokenUsage.NONE);
  }

  @Override
  public List<Problem> problems(HostSettings host) {
    return List.of();
  }
}
