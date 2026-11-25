package fitness.archunit;

@Stereotype
final class Flagged {

  public void tell(String text, boolean loud, Boolean urgent) {}

  public void tellPlainly(String text) {}

  void tellQuietly(boolean loud) {}

  public static String shout(String text) {
    return text;
  }

  static String whisper(String text) {
    return text;
  }

  @Stereotype
  static final class Nested {

    public void tell(boolean loud) {}

    public static String shout(String text) {
      return text;
    }
  }
}
