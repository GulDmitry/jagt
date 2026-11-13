package dev.jagt.orchestrator.testing;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.LoggerFactory;

/** SLF4J hands out substitute loggers to every thread asking while another still initialises it. */
public final class LoggingReadyFirst implements LauncherSessionListener {

  @Override
  public void launcherSessionOpened(LauncherSession session) {
    LoggerFactory.getILoggerFactory();
  }
}
