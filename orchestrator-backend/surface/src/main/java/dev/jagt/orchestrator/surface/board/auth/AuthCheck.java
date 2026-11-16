package dev.jagt.orchestrator.surface.board.auth;

import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.service.log.BoardUrl;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCheck implements StartupCheck {

  private static final String KEY = "orchestrator.auth";
  private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

  private final AuthProperties auth;
  private final OidcProperties oidc;
  private final Environment env;
  private final ServerProperties server;
  private final BoardUrl boardUrl;

  @Override
  public List<Problem> problems() {
    return switch (auth.mode()) {
      case NONE -> openBoard();
      case PASSWORD -> password();
      case OIDC -> oidcSetup();
    };
  }

  private List<Problem> openBoard() {
    List<Problem> problems = new ArrayList<>();
    InetAddress address = server.getAddress();
    if (address == null || !address.isLoopbackAddress()) {
      problems.add(
          new Problem(
              KEY,
              "orchestrator.auth is 'none' and server.address is '"
                  + address
                  + "': a board beyond loopback needs orchestrator.auth.mode 'password' or"
                  + " 'oidc'."));
    }
    if (!auth.allowedHosts().isEmpty() || boardUrl.reachedByName()) {
      problems.add(
          new Problem(
              KEY,
              "orchestrator.auth.allowedHosts or server.publicUrl names a host and"
                  + " orchestrator.auth is 'none': a board reached by name needs"
                  + " orchestrator.auth.mode 'password' or 'oidc'."));
    }
    return problems;
  }

  private List<Problem> password() {
    if (auth.passwordHash() == null || !BCRYPT.matcher(auth.passwordHash()).matches()) {
      return List.of(
          new Problem(
              KEY + ".passwordHash",
              "orchestrator.auth is 'password' and orchestrator.auth.passwordHash is not a bcrypt"
                  + " hash."));
    }
    return List.of();
  }

  private List<Problem> oidcSetup() {
    List<Problem> problems = new ArrayList<>();
    missing(problems, "issuerUri", oidc.issuerUri());
    missing(problems, "clientId", oidc.clientId());
    missing(problems, "allowed", oidc.allowed());
    missing(problems, "clientSecretEnv", oidc.clientSecretEnv());
    String secretEnv = oidc.clientSecretEnv();
    if (secretEnv != null && !secretEnv.isBlank() && isBlank(env.getProperty(secretEnv))) {
      problems.add(
          new Problem(
              KEY + ".oidc.clientSecretEnv",
              "orchestrator.auth.oidc.clientSecretEnv names '"
                  + secretEnv
                  + "', an environment variable that is not set."));
    }
    return problems;
  }

  private static void missing(List<Problem> problems, String key, @Nullable String value) {
    if (isBlank(value)) {
      problems.add(
          new Problem(
              KEY + ".oidc." + key,
              "orchestrator.auth is 'oidc' and orchestrator.auth.oidc." + key + " is not set."));
    }
  }

  private static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  @Override
  public boolean machine() {
    return false;
  }
}
