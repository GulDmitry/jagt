package dev.jagt.orchestrator.job;

import java.util.List;

/** Jobs whose number is known once the installed roles are: each is scheduled as a declared one. */
public interface JobSource {

  List<? extends Job> jobs();
}
