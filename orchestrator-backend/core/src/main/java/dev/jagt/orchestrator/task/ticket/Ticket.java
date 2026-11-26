package dev.jagt.orchestrator.task.ticket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import org.jspecify.annotations.Nullable;

/** What the task was opened for. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Ticket(
    @Nullable String alias,
    @Nullable String title,
    @Nullable String ticketUrl,
    // Null = the project's configured baseBranch, so a config change still reaches the task.
    @Nullable String baseBranch,
    // Null for a task stored before it was recorded: read as a human's.
    @Nullable ActionOrigin openedBy) {

  /** The task's own base branch, or the project default when it has none (null or blank). */
  public String baseBranchOr(String projectDefault) {
    return baseBranch == null || baseBranch.isBlank() ? projectDefault : baseBranch;
  }

  public Ticket withBaseBranch(String branch) {
    return new Ticket(alias, title, ticketUrl, branch, openedBy);
  }

  @JsonIgnore
  public boolean openedOutside() {
    return openedBy != null && openedBy.outside();
  }
}
