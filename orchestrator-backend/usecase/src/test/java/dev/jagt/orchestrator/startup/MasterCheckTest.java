package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.CodeReviewConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.master.MasterBriefs;
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

    @Test
    void findsNothingWrongWithTheSettingEveryInstallShipsWith(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md.dist"), "the shipped standards");
        when(configService.load()).thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults()));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void refusesAModeThisMachineDoesNotHave(@TempDir Path root) {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("supervise")));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems())
                .singleElement().asString().contains("orchestrator.master.mode");
    }

    @Test
    void refusesAMasterThatShipsWhileEveryDraftedReplyStillWaitsForYouToPostIt(@TempDir Path root)
            throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act"))
                .withCodeReview(new CodeReviewConfig(null, false, null)));

        assertThat(new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime).problems())
                .singleElement().asString().contains("postReviewReplies");
    }

    @Test
    void refusesTheRetiredNameRatherThanSilentlyGivingTheMasterEveryStep(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act").withWithhold(List.of("reply"))));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems())
                .singleElement().asString().contains("orchestrator.master.mine");
    }

    @Test
    void refusesKeepingTheDeployWhileLeavingItsUndoToTheMaster(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");
        when(configService.load()).thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")
                .withMine(List.of("reply", "deploy"))));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems())
                .singleElement().asString().contains("keep both or neither");
    }

    @Test
    void findsNothingWrongWithShippingUnattendedWhileRepliesAndTheSharedBranchStayYours(@TempDir Path root)
            throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");
        when(configService.load()).thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")
                .withMine(List.of("reply", "deploy", "revert"))));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void refusesKeepingEveryStepWhereSayingJudgeMeansTheSame(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "judge like me");
        when(configService.load()).thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")
                .withMine(List.of("ship", "deploy", "revert", "reply", "answer", "plan"))));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems())
                .singleElement().asString().contains("`judge` written long");
    }

    @Test
    void refusesToRunAReviewerWhoseBriefWasNeverCopiedFromTheShippedOne(@TempDir Path root) {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge")));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems())
                .singleElement().asString().contains("copy master-brief.md.dist");
    }

    @Test
    void judgesByTheShippedBriefWhereNoneWasCopied(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md.dist"), "the shipped standards");
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge")));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void refusesABriefNamedByHandThatIsNotThere(@TempDir Path root) {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge").withBrief("mine.md")));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems())
                .singleElement().asString().contains("mine.md");
    }

    @Test
    void refusesAModelTheRuntimeRunningTheSessionsCannotBeTold(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "what I care about\n");
        when(agentRuntime.displayName()).thenReturn("Codex");
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge").withModel("fable")));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems())
                .singleElement().asString().contains("orchestrator.master.model", "Codex");
    }

    @Test
    void acceptsAModeWhoseBriefTheInstallActuallyCarries(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("master-brief.md"), "what I care about\n");
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act")));
        MasterCheck check = new MasterCheck(configService, new MasterBriefs(new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())), mock(PromptTemplates.class)),
                agentRuntime);

        assertThat(check.problems()).isEmpty();
    }
}
