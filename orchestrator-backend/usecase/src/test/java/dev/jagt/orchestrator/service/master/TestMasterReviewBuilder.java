package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestMasterReviewBuilder(String diff, @Nullable String kind, String inputs) {

  public static TestMasterReviewBuilder masterReview() {
    return new TestMasterReviewBuilder("abc123", "PLAIN", "in1");
  }

  public MasterReview.Review build() {
    return new MasterReview.Review(
        1_000,
        diff,
        kind,
        inputs,
        List.of(
            new MasterReview.Read(
                "acceptance", new Judgement("", "ready", List.of(), "", List.of(), ""))));
  }
}
