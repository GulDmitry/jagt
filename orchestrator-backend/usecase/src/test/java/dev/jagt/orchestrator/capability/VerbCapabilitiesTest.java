package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.capability.deploy.DeployCapability;
import dev.jagt.orchestrator.capability.deploy.DeployService;
import dev.jagt.orchestrator.capability.deploy.RevertCapability;
import dev.jagt.orchestrator.capability.deploy.RevertService;
import dev.jagt.orchestrator.capability.ide.DiffCapability;
import dev.jagt.orchestrator.capability.ide.IdeCapability;
import dev.jagt.orchestrator.capability.ide.IdeLauncher;
import dev.jagt.orchestrator.capability.ship.ShipCapability;
import dev.jagt.orchestrator.capability.ship.ShipService;
import dev.jagt.orchestrator.capability.trust.TrustCapability;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ReviewSweepService;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.service.TrustedGitState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerbCapabilitiesTest {

    @Test
    void deployVerbDeploysAndReportsTheDeployOutcome() {
        DeployService deploys = mock(DeployService.class);
        Outcome deployed = Outcome.ok("deployed");
        when(deploys.deploy("ABC-1")).thenReturn(deployed);
        DeployCapability capability = new DeployCapability(deploys);

        assertThat(capability.action()).isEqualTo(TaskAction.DEPLOY);
        assertThat(capability.run("ABC-1")).isSameAs(deployed);
    }

    @Test
    void revertVerbRevertsAndReportsTheRevertOutcome() {
        RevertService reverts = mock(RevertService.class);
        Outcome reverted = Outcome.ok("reverted");
        when(reverts.revert("ABC-1")).thenReturn(reverted);
        RevertCapability capability = new RevertCapability(reverts);

        assertThat(capability.action()).isEqualTo(TaskAction.REVERT);
        assertThat(capability.run("ABC-1")).isSameAs(reverted);
    }

    @Test
    void shipVerbShipsAndReportsTheShipOutcome() {
        ShipService ships = mock(ShipService.class);
        Outcome shipped = Outcome.ok("shipped");
        when(ships.ship("ABC-1")).thenReturn(shipped);
        ShipCapability capability = new ShipCapability(ships);

        assertThat(capability.action()).isEqualTo(TaskAction.SHIP);
        assertThat(capability.run("ABC-1")).isSameAs(shipped);
    }

    @Test
    void ideVerbOpensTheProjectAndSaysWhatOpened() {
        IdeLauncher ide = mock(IdeLauncher.class);
        when(ide.open("ABC-1", IdeLauncher.Mode.PROJECT)).thenReturn("opened project");
        IdeCapability capability = new IdeCapability(ide);

        assertThat(capability.action()).isEqualTo(TaskAction.IDE);
        assertThat(capability.run("ABC-1").message()).isEqualTo("opened project");
    }

    @Test
    void diffVerbOpensTheDiffAndSaysWhatOpened() {
        IdeLauncher ide = mock(IdeLauncher.class);
        when(ide.open("ABC-1", IdeLauncher.Mode.DIFF)).thenReturn("opened diff");
        DiffCapability capability = new DiffCapability(ide);

        assertThat(capability.action()).isEqualTo(TaskAction.DIFF);
        assertThat(capability.run("ABC-1").message()).isEqualTo("opened diff");
    }

    @Test
    void focusVerbFocusesTheTaskWindowAndSaysWhatFocused() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.focusTask("ABC-1")).thenReturn("focused");
        FocusCapability capability = new FocusCapability(sessions);

        assertThat(capability.action()).isEqualTo(TaskAction.FOCUS);
        assertThat(capability.run("ABC-1").message()).isEqualTo("focused");
    }

    @Test
    void respawnVerbReopensTheTaskTabAndSaysWhatOpened() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.openTaskTab("ABC-1", null)).thenReturn("reopened");
        RespawnCapability capability = new RespawnCapability(sessions);

        assertThat(capability.action()).isEqualTo(TaskAction.RESPAWN);
        assertThat(capability.run("ABC-1").message()).isEqualTo("reopened");
    }

    @Test
    void sweepVerbSweepsTheTaskAndReportsTheSweepMessage() {
        ReviewSweepService sweeps = mock(ReviewSweepService.class);
        when(sweeps.sweep("ABC-1")).thenReturn(new SweepResult(SweepResult.Kind.APPROVED, "approved"));
        SweepCapability capability = new SweepCapability(sweeps);

        assertThat(capability.action()).isEqualTo(TaskAction.SWEEP);
        assertThat(capability.run("ABC-1").message()).isEqualTo("approved");
    }

    @Test
    void trustVerbTrustsTheTaskGitStateAndSaysSo() {
        TrustedGitState trusted = mock(TrustedGitState.class);
        TrustCapability capability = new TrustCapability(trusted);

        Outcome outcome = capability.run("ABC-1");

        assertThat(capability.action()).isEqualTo(TaskAction.TRUST);
        assertThat(outcome.message()).contains("ABC-1");
        verify(trusted).trust("ABC-1");
    }
}
