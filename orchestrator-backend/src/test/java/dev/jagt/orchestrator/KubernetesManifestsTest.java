package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class KubernetesManifestsTest {

  private static final Path MANIFESTS = Path.of("../kubernetes.yml.dist");
  private static final Path SERVER = Path.of("../jagt.server.yml.dist");
  private static final Path CONTRACT = Path.of("../contracts/openapi.yaml");

  @Test
  void theManifestsHoldEveryKindAnOrganisationStartsFrom() {
    List<Object> kinds = new ArrayList<>();
    new Yaml()
        .loadAll(SourceRoots.read(MANIFESTS))
        .forEach(document -> kinds.add(((Map<?, ?>) document).get("kind")));

    assertThat(kinds)
        .containsExactlyInAnyOrder(
            "Secret",
            "Secret",
            "ServiceAccount",
            "PersistentVolumeClaim",
            "Deployment",
            "Service",
            "Ingress",
            "NetworkPolicy");
  }

  @Test
  void theIdentitySecretNamesOnlyVariablesTheServerReadsAndHoldsNoValue() {
    String server = SourceRoots.read(SERVER);

    assertThat(server)
        .contains("${JAGT_PASSWORD_HASH", "${JAGT_PUBLIC_URL")
        .contains("${JAGT_TRACKER_USER", "webhookEnv: JAGT_NOTIFY_WEBHOOK", "${JAGT_MCP_CONFIG");
    assertThat(SourceRoots.read(MANIFESTS))
        .containsPattern("(?m)^  JAGT_PASSWORD_HASH: \"\"$")
        .containsPattern("(?m)^  JAGT_TRACKER_USER: \"\"$")
        .containsPattern("(?m)^  JAGT_NOTIFY_WEBHOOK: \"\"$")
        .containsPattern("(?m)^  GH_TOKEN: \"\"$");
  }

  @Test
  void theIngressHostIsTheHostOfThePublicUrl() {
    String manifests = SourceRoots.read(MANIFESTS);

    String publicUrl =
        manifests
            .lines()
            .filter(line -> line.contains("JAGT_PUBLIC_URL:"))
            .findFirst()
            .orElseThrow();
    String host = URI.create(publicUrl.replaceFirst(".*JAGT_PUBLIC_URL:", "").trim()).getHost();
    assertThat(manifests).contains("- host: " + host);
  }

  @Test
  void theMcpFileIsMountedWhereTheDeploymentPointsTheServer() {
    String manifests = SourceRoots.read(MANIFESTS);

    assertThat(manifests)
        .contains(
            "value: /run/secrets/jagt/mcp.json",
            "mountPath: /run/secrets/jagt",
            "secretName: jagt-mcp");
  }

  @Test
  void thePodMeetsTheRestrictedPodSecurityStandard() {
    String manifests = SourceRoots.read(MANIFESTS);

    assertThat(manifests)
        .contains(
            "runAsNonRoot: true", "runAsUser: 10001", "fsGroup: 10001", "type: RuntimeDefault")
        .contains("allowPrivilegeEscalation: false", "drop: [ALL]")
        .contains("automountServiceAccountToken: false")
        .doesNotContain("privileged", "hostNetwork", "hostPath");
  }

  @Test
  void theProbesAskTheActuatorPathsTheBoardOpensWithoutLogin() {
    String manifests = SourceRoots.read(MANIFESTS);

    assertThat(SourceRoots.read(CONTRACT))
        .contains("  - /actuator/health/liveness", "  - /actuator/health/readiness");
    assertThat(manifests)
        .containsSubsequence(
            "startupProbe:",
            "path: /actuator/health/liveness",
            "livenessProbe:",
            "path: /actuator/health/liveness",
            "readinessProbe:",
            "path: /actuator/health/readiness");
  }

  @Test
  void theDeploymentIsOneRecreatedReplicaWithMemoryRequestedAsLimited() {
    String manifests = SourceRoots.read(MANIFESTS);

    assertThat(manifests)
        .contains("replicas: 1", "type: Recreate", "terminationGracePeriodSeconds: 60")
        .containsSubsequence("requests:", "memory: 8Gi", "limits:", "memory: 8Gi");
  }

  @Test
  void thePolicyDeniesIngressButTheControllerAndRestrictsEgress() {
    String manifests = SourceRoots.read(MANIFESTS);

    assertThat(manifests)
        .contains("policyTypes: [Ingress, Egress]", "kubernetes.io/metadata.name: ingress-nginx")
        .contains("kubernetes.io/metadata.name: kube-system");
  }
}
