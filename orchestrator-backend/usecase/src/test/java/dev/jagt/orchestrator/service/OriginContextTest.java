package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ActionOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OriginContextTest {

    @Test
    void attributesWhatARunDoesToTheOneWhoAskedAndForgetsThemAfter() {
        ActionOrigin during = OriginContext.as(ActionOrigin.BOARD, OriginContext::current);

        assertThat(during).isEqualTo(ActionOrigin.BOARD);
        assertThat(OriginContext.current()).isNull();
    }

    @Test
    void restoresTheOuterCallersOriginWhenAnInnerOneEnds() {
        ActionOrigin[] afterInner = new ActionOrigin[1];

        ActionOrigin inner = OriginContext.as(ActionOrigin.BOARD, () -> {
            ActionOrigin during = OriginContext.as(ActionOrigin.TRACKER, OriginContext::current);
            afterInner[0] = OriginContext.current();
            return during;
        });

        assertThat(inner).isEqualTo(ActionOrigin.TRACKER);
        assertThat(afterInner[0]).isEqualTo(ActionOrigin.BOARD);
    }

    @Test
    void answersWithTheWorkAndForgetsTheReasonAfterIt() {
        String during = OriginContext.because("work done", OriginContext::why);

        assertThat(during).isEqualTo("work done");
        assertThat(OriginContext.why()).isNull();
    }

    @Test
    void leavesAnOuterReasonStandingWhereTheInnerOneIsNull() {
        String inner = OriginContext.because("outer", () -> OriginContext.because(null, OriginContext::why));

        assertThat(inner).isEqualTo("outer");
    }

    @Test
    void restoresTheOuterReasonWhenAnInnerOneEnds() {
        String[] afterInner = new String[1];

        OriginContext.because("outer", () -> {
            OriginContext.because("inner", OriginContext::why);
            afterInner[0] = OriginContext.why();
            return null;
        });

        assertThat(afterInner[0]).isEqualTo("outer");
    }
}
