package dev.jagt.gradle.testsfor;

public final class UnknownPathException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  UnknownPathException(String path) {
    super("tests-for: no such file and not deleted in the branch: " + path);
  }
}
