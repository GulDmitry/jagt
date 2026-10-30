package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.With;

/** The round, as its {@code mrCreatedAt}, each auto-review ping was last sent for; 0 where none was. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutoReviewTold(@With long windowElapsed, @With long unreadable) {

    public static AutoReviewTold none() {
        return new AutoReviewTold(0, 0);
    }

    public boolean isNone() {
        return windowElapsed == 0 && unreadable == 0;
    }
}
