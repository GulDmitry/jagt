package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.turn.RepliesInstruction;
import dev.jagt.orchestrator.task.master.MasterRight;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReplyRight implements RepliesInstruction {

  private static final String MAY_NOT_REPLY =
      "Do NOT post any replies — this ship is the Master session's,"
          + " which may not answer a person. LEAVE review_replies.md for the human.\n";

  private final CaseDoor door;

  @Override
  public Optional<String> repliesStep(String taskId, ConfigFile config) {
    boolean asked = config.arbiter().may(MasterRight.REPLY);
    boolean refusedByCase = asked && !door.presses(taskId, MasterMove.REPLY.id());
    // A right withheld from the Master is withheld from the ship it asked for, not from every ship.
    boolean withheld = !asked && OriginContext.current() == ActionOrigin.MASTER;
    return refusedByCase || withheld ? Optional.of(MAY_NOT_REPLY) : Optional.empty();
  }

  @Override
  public boolean postsPersonReplies(ConfigFile config) {
    return config.codeReview().repliesToPeopleUnaskedOrDefault()
        || OriginContext.current() != ActionOrigin.MASTER;
  }
}
