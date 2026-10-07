package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.CodeReviewConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.MasterBriefs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterCheckTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    private MasterCheck checking(Path root, MasterConfig master) {
        when(configService.load()).thenReturn(ConfigFile.defaults().withMaster(master));
        return new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);
    }

    @Test
    void findsNothingWrongWithTheSettingEveryInstallShipsWith(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md.dist"), "the shipped standards");

        assertThat(checking(root, MasterConfig.defaults()).problems()).isEmpty();
    }

    @Test
    void refusesAModeThisMachineDoesNotHave(@TempDir Path root) {
        assertThat(checking(root, new MasterConfig("supervise", null, null, null, null)).problems())
                .singleElement().asString().contains("orchestrator.master.mode");
    }

    @Test
    void refusesAMasterThatShipsWhileEveryDraftedReplyStillWaitsForYouToPostIt(@TempDir Path root)
            throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(new MasterConfig("act", null, null, null, null))
                .withCodeReview(new CodeReviewConfig(null, false, null, null)));

        assertThat(new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime).problems())
                .singleElement().asString().contains("postReviewReplies");
    }

    @Test
    void refusesTheRetiredNameRatherThanSilentlyGivingTheMasterEveryStep(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");

        assertThat(checking(root, new MasterConfig("act", null, null, null, List.of("reply"))).problems())
                .singleElement().asString().contains("orchestrator.master.mine");
    }

    @Test
    void refusesKeepingTheDeployWhileLeavingItsUndoToTheMaster(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");

        assertThat(checking(root, new MasterConfig("act", null, null, List.of("reply", "deploy"), null)).problems())
                .singleElement().asString().contains("keep both or neither");
    }

    @Test
    void findsNothingWrongWithShippingUnattendedWhileRepliesAndTheSharedBranchStayYours(@TempDir Path root)
            throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");

        assertThat(checking(root, new MasterConfig("act", null, null,
                List.of("reply", "deploy", "revert"), null)).problems()).isEmpty();
    }

    @Test
    void refusesKeepingEveryStepWhereSayingJudgeMeansTheSame(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");

        assertThat(checking(root, new MasterConfig("act", null, null,
                List.of("ship", "sweep", "deploy", "revert", "reply", "answer"), null)).problems())
                .singleElement().asString().contains("`judge` written long");
    }

    @Test
    void refusesToRunAReviewerWhoseBriefWasNeverCopiedFromTheShippedOne(@TempDir Path root) {
        assertThat(checking(root, new MasterConfig("judge", null, null, null, null)).problems())
                .singleElement().asString().contains("copy master-brief.md.dist");
    }

    @Test
    void judgesByTheShippedBriefWhereNoneWasCopied(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md.dist"), "the shipped standards");

        assertThat(checking(root, new MasterConfig("judge", null, null, null, null)).problems()).isEmpty();
    }

    @Test
    void refusesABriefNamedByHandThatIsNotThere(@TempDir Path root) {
        assertThat(checking(root, new MasterConfig("judge", "mine.md", null, null, null)).problems())
                .singleElement().asString().contains("mine.md");
    }

    @Test
    void refusesAModelTheRuntimeRunningTheSessionsCannotBeTold(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "what I care about\n");
        when(agentRuntime.displayName()).thenReturn("Codex");

        assertThat(checking(root, new MasterConfig("judge", null, "fable", null, null)).problems())
                .singleElement().asString().contains("orchestrator.master.model", "Codex");
    }

    @Test
    void acceptsAModeWhoseBriefTheInstallActuallyCarries(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "what I care about\n");

        assertThat(checking(root, new MasterConfig("act", null, null, null, null)).problems()).isEmpty();
    }
}
