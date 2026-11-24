package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.host.Problem;
import java.util.List;
import java.util.Set;

/** What a driver of one or more kinds of code host needs of a project and may be missing. */
public interface HostNeeds {

  /** The {@code codeHost} values this driver answers for. */
  Set<String> kinds();

  /** What is missing for {@code host}, without asking the network. */
  List<Problem> problems(HostSettings host);
}
