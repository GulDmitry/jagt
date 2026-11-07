package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** No pipeline is read, so a deploy settles on the address no host has. */
@Component
public class NoCodeHost extends OfflineCodeHost {

  static final String ID = ProjectConfig.NO_CODE_HOST;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Answer<String> retryFailed(HostSettings host, String pipelineUrl) {
    return Answer.failed(TokenUsage.NONE, host.selectedBy() + " is none: nothing is re-run");
  }

  @Override
  public Answer<String> closeRequest(HostSettings host, String requestUrl) {
    return Answer.failed(TokenUsage.NONE, host.selectedBy() + " is none: no request is closed");
  }

  @Override
  public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
    return new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE);
  }
}
