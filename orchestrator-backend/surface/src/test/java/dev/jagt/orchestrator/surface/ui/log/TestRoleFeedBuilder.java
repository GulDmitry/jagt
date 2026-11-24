package dev.jagt.orchestrator.surface.ui.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.util.LogbackMDCAdapter;

record TestRoleFeedBuilder(LoggerContext context, String role) {

  Logger build() {
    context.setMDCAdapter(new LogbackMDCAdapter());
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("%msg%n");
    encoder.start();
    RecentLines feed = new RecentLines();
    feed.setContext(context);
    feed.setName(RoleFeeds.PREFIX + role);
    feed.setEncoder(encoder);
    feed.start();
    Logger logger = context.getLogger("dev.jagt.test." + role);
    logger.addAppender(feed);
    return logger;
  }
}
