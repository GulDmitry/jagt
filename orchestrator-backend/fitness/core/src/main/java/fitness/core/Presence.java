package fitness.core;

/** Whether a named library or tool a function needs is there. */
@FunctionalInterface
public interface Presence {

  boolean has(String need);
}
