package fitness.archunit;

@Stereotype
final class Flagged {

  public void tell(String text, boolean loud, Boolean urgent) {}

  public void tellPlainly(String text) {}

  void tellQuietly(boolean loud) {}

  @Stereotype
  static final class Nested {

    public void tell(boolean loud) {}
  }
}
