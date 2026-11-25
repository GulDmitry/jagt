package dev.jagt.orchestrator.task.deploy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.task.text.Blank;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What the host's checks last said, on which commit, and the re-runs they earned. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Checks(
    // The host's own wording for the checks, unparsed. Null = never read.
    @Nullable String pipelineStatus,
    // The reads running that could not list them, newest last; any = the word above is an older
    // round's.
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ChecksUnread> checksUnread,
    // The pushed commit the stored checks and approval were read on; null = a read that named
    // none.
    @Nullable String checksTip,
    // The newest pushed commit a read saw. Null = none yet, and the stored reads stand as they
    // are.
    @Nullable String headTip,
    // The re-runs of a pipeline that died on infrastructure: the ones sent, and at most one still
    // armed.
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<InfraRetry> infraRetries,
    // The pipeline of the request's head as the host listed it; null = none listed yet.
    @Nullable String pipelineUrl) {

  public Checks {
    checksUnread = checksUnread == null ? List.of() : List.copyOf(checksUnread);
    infraRetries = infraRetries == null ? List.of() : List.copyOf(infraRetries);
  }

  public Checks withRead(String hostStatus, @Nullable String tip) {
    return new Checks(hostStatus, List.of(), Blank.toNull(tip), headTip, infraRetries, pipelineUrl);
  }

  public Checks withPipelineUrl(@Nullable String url) {
    String listed = Blank.toNull(url);
    return listed == null || listed.equals(pipelineUrl)
        ? this
        : new Checks(pipelineStatus, checksUnread, checksTip, headTip, infraRetries, listed);
  }

  public Checks withHead(@Nullable String tip) {
    String head = Blank.toNull(tip);
    return head == null || head.equals(headTip)
        ? this
        : new Checks(pipelineStatus, checksUnread, checksTip, head, infraRetries, pipelineUrl);
  }

  public Checks withUnread(ChecksUnread read) {
    List<ChecksUnread> running = new ArrayList<>(checksUnread);
    running.add(read);
    return new Checks(
        pipelineStatus,
        running.subList(Math.max(0, running.size() - ChecksUnread.CEILING), running.size()),
        checksTip,
        headTip,
        infraRetries,
        pipelineUrl);
  }

  public Checks withInfraRetries(List<InfraRetry> retries) {
    return new Checks(pipelineStatus, checksUnread, checksTip, headTip, retries, pipelineUrl);
  }

  public Checks reset() {
    return new Checks(null, List.of(), null, headTip, infraRetries, null);
  }

  public @Nullable String read() {
    return readsStaleOn(checksTip) ? null : pipelineStatus;
  }

  public boolean readsStaleOn(@Nullable String tip) {
    return headTip != null && !headTip.equals(Blank.toNull(tip));
  }

  public boolean unread() {
    return !checksUnread.isEmpty();
  }
}
