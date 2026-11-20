package fitness.core;

/** A function and the library or tool it needs but does not find. */
public record Missing(FitnessFunction function, String need) {

  public String key() {
    return function.section() + "." + function.id() + " needs " + need;
  }
}
