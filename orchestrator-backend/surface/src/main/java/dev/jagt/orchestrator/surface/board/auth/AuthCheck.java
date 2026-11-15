package dev.jagt.orchestrator.surface.board.auth;

import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCheck implements StartupCheck {

  private static final String KEY = "orchestrator.auth";
  private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

  private final AuthProperties auth;
  private final ServerProperties server;

  @Override
  public List<Problem> problems() {
    List<Problem> problems = new ArrayList<>();
    if (auth.mode() == AuthProperties.Mode.NONE) {
      InetAddress address = server.getAddress();
      if (address == null || !address.isLoopbackAddress()) {
        problems.add(
            new Problem(
                KEY,
                "orchestrator.auth is 'none' and server.address is '"
                    + address
                    + "': a board beyond loopback needs orchestrator.auth.mode 'password'."));
      }
      if (!auth.allowedHosts().isEmpty()) {
        problems.add(
            new Problem(
                KEY,
                "orchestrator.auth.allowedHosts is set and orchestrator.auth is 'none': a board"
                    + " reached by name needs orchestrator.auth.mode 'password'."));
      }
    } else if (auth.passwordHash() == null || !BCRYPT.matcher(auth.passwordHash()).matches()) {
      problems.add(
          new Problem(
              KEY + ".passwordHash",
              "orchestrator.auth is 'password' and orchestrator.auth.passwordHash is not a bcrypt"
                  + " hash."));
    }
    return problems;
  }

  @Override
  public boolean machine() {
    return false;
  }
}
