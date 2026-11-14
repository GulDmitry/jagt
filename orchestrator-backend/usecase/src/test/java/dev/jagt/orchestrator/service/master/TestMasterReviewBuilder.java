package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestMasterReviewBuilder(
    long round, String diff, @Nullable String kind, String inputs) {

  public static TestMasterReviewBuilder masterReview() {
    return new TestMasterReviewBuilder(1_000, "abc123", "PLAIN", "in1");
  }

  public static TestMasterReviewBuilder bareMasterReview() {
    return new TestMasterReviewBuilder(1_000, "", "", "");
  }

  public MasterReview.Review build() {
    return new MasterReview.Review(
        round,
        diff,
        kind,
        inputs,
        List.of(
            new MasterReview.Read(
                "acceptance", new Judgement("", "ready", List.of(), "", List.of(), ""))));
  }
}
