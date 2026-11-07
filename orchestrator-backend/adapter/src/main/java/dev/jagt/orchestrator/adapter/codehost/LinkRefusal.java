package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.task.GitRemote;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Why a write to a request's address is not sent: the address is the caller's word, the host the
 * project's own.
 */
enum LinkRefusal {
  HOSTLESS_REMOTE("the repository's remote names no host, so no request address is its own"),
  OTHER_HOST("the request is on another host than the repository's");

  private final String said;

  LinkRefusal(String said) {
    this.said = said;
  }

  String said() {
    return said;
  }

  /**
   * Whether {@code address} is a write target of the repository at {@code remoteUrl}, on {@code
   * configuredHost} where one is set, else on the host the remote names.
   */
  static Optional<LinkRefusal> of(
      @Nullable String remoteUrl, @Nullable String configuredHost, String address) {
    if (!GitRemote.hasHost(remoteUrl)) {
      return Optional.of(HOSTLESS_REMOTE);
    }
    String basis =
        configuredHost == null || configuredHost.isBlank()
            ? remoteUrl
            : "https://" + configuredHost.strip() + "/";
    return GitRemote.onHostOf(basis, address) ? Optional.empty() : Optional.of(OTHER_HOST);
  }
}
