package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipRepliesTest {

    @Test
    void forbidsTheMasterFromAnsweringAPersonWhereThatRightWasWithheld() {
        ConfigFile config = ConfigFile.defaults()
                .withMaster(new MasterConfig("act", null, null, List.of("reply")));

        String step = OriginContext.as(ActionOrigin.MASTER, () -> ShipService.repliesStep(config));

        assertThat(step).contains("may not answer a person");
    }

    @Test
    void leavesAHumansOwnShipPostingItsRepliesAsBefore() {
        ConfigFile config = ConfigFile.defaults()
                .withMaster(new MasterConfig("act", null, null, List.of("reply")));

        String step = OriginContext.as(ActionOrigin.BOARD, () -> ShipService.repliesStep(config));

        assertThat(step).contains("post each drafted reply");
    }
}
