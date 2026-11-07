package dev.jagt.orchestrator.task;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The git remote a repository's branches are shared through; {@link #LOCAL} is none, for a
 * repository kept on this machine.
 */
public record Remote(String name) {

  public static final String NONE = "none";
  public static final Remote ORIGIN = new Remote("origin");
  public static final Remote LOCAL = new Remote(NONE);

  public static Remote of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return ORIGIN;
    }
    String named = configured.strip();
    return NONE.equals(named.toLowerCase(Locale.ROOT)) ? LOCAL : new Remote(named);
  }

  public boolean local() {
    return NONE.equals(name);
  }

  /**
   * The branch as this repository holds it: the remote-tracking ref, or the local branch where
   * nothing is shared.
   */
  public String track(String branch) {
    return local() ? branch : name + "/" + branch;
  }

  public String trackingRef(String branch) {
    return local() ? "refs/heads/" + branch : "refs/remotes/" + name + "/" + branch;
  }

  /**
   * What {@code git push} is given to reach it: the repository's own refs where there is no remote.
   */
  public String pushTarget() {
    return local() ? "." : name;
  }

  /**
   * The sentence telling a session how its branch is shared: a push where there is a remote, none
   * where there is not.
   */
  public String pushAdvice(String branch) {
    return local()
        ? "Nothing is pushed from here: the repository stays on this machine, so commit "
            + branch
            + "."
        : "Push " + branch + " with a plain `git push " + name + " " + branch + "`.";
  }

  /**
   * The branch a configured ref names, with this remote's prefix and any {@code refs/heads/}
   * dropped.
   */
  public String branchOf(@Nullable String ref) {
    if (ref == null) {
      return "";
    }
    String bare = ref.strip().replaceFirst("^\\+?(refs/heads/|refs/remotes/)?", "");
    return local() ? bare : bare.replaceFirst("^" + java.util.regex.Pattern.quote(name) + "/", "");
  }
}
