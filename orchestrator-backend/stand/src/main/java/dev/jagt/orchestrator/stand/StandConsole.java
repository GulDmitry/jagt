package dev.jagt.orchestrator.stand;

final class StandConsole {

  private StandConsole() {}

  static void line(String text) {
    System.out.println(text); // the stand has no logger; its console is its report
  }
}
