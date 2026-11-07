package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.task.GitRemote;
import java.net.URI;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Where a pipeline or a repository lives on its host; the kind of host is the caller's, never read
 * from here.
 */
record HostAddress(String hostname, String project, String id) {

  /** The pipeline an address names, its id the segment after {@code marker}. */
  static Optional<HostAddress> ofPipeline(String url, String marker, boolean keepsPort) {
    URI uri;
    try {
      uri = URI.create(url.strip());
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    String path = uri.getPath() == null ? "" : uri.getPath();
    int at = path.lastIndexOf(marker);
    if (uri.getHost() == null || at <= 1) {
      return Optional.empty();
    }
    String id = path.substring(at + marker.length()).split("/", 2)[0];
    return id.isBlank()
        ? Optional.empty()
        : Optional.of(
            new HostAddress(
                authority(uri, keepsPort), path.substring(1, at).replaceAll("/-$", ""), id));
  }

  /**
   * A repository's remote in either shape, on {@code configuredHost} where one is set, else on the
   * host the remote names.
   */
  static Optional<HostAddress> ofRemote(
      String remoteUrl, String commit, @Nullable String configuredHost, boolean keepsPort) {
    String project = GitRemote.projectPath(remoteUrl);
    if (project == null || commit.isBlank()) {
      return Optional.empty();
    }
    String remote = remoteUrl.strip();
    if (configuredHost != null && !configuredHost.isBlank()) {
      return Optional.of(new HostAddress(configuredHost.strip(), project, commit));
    }
    if (!remote.contains("://")) {
      return Optional.of(
          new HostAddress(
              remote.substring(remote.indexOf('@') + 1, remote.indexOf(':')), project, commit));
    }
    URI uri = URI.create(remote);
    return uri.getHost() == null
        ? Optional.empty()
        : Optional.of(new HostAddress(authority(uri, keepsPort), project, commit));
  }

  private static String authority(URI uri, boolean keepsPort) {
    boolean web =
        "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    return keepsPort && web && uri.getPort() != -1
        ? uri.getHost() + ":" + uri.getPort()
        : uri.getHost();
  }
}
