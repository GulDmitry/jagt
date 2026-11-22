package fitness.core;

public record Section(String dimension, String characteristic, String subcharacteristic) {

  public String path() {
    return dimension + "." + characteristic + "." + subcharacteristic;
  }

  public String packageName() {
    return path().replace("-", "");
  }
}
