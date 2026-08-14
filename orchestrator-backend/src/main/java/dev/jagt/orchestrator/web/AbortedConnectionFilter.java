package dev.jagt.orchestrator.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;

import java.net.SocketException;
import java.util.Arrays;

/**
 * Drops the one ERROR the board prints for connections nobody ever meant to use: the peer tore the connection
 * down between {@code accept()} and Tomcat's {@code setSoLinger}, so the setsockopt fails for a connection that
 * carried no request. Tomcat offers no knob — it sets {@code connectionLinger} in its constructor.
 *
 * <p>Matched by the {@code setSoLinger} frame rather than the message, which keeps it independent of the C
 * library's locale. A failure to configure any OTHER socket option still reaches the log.
 */
@Component
public class AbortedConnectionFilter extends TurboFilter {

    private static final String TOMCAT_NET_LOGGER = "org.apache.tomcat.util.net.";
    private static final String LINGER_CALL = "setSoLinger";

    /**
     * Installed while beans are created, which is before the connector binds its port — the first abandoned
     * connection cannot arrive earlier than that.
     */
    @PostConstruct
    void install() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            start();
            context.addTurboFilter(this);
        }
    }

    /**
     * The logger context is the JVM's, not this application context's: a test run boots several backends in one
     * JVM, and a filter left behind by a closed one would be asked about every log call made by the next.
     */
    @PreDestroy
    void uninstall() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.getTurboFilterList().remove(this);
            stop();
        }
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        return isAbortedBeforeConfigured(logger, t) ? FilterReply.DENY : FilterReply.NEUTRAL;
    }

    private boolean isAbortedBeforeConfigured(Logger logger, Throwable t) {
        return t instanceof SocketException
                && logger != null
                && logger.getName().startsWith(TOMCAT_NET_LOGGER)
                && Arrays.stream(t.getStackTrace()).anyMatch(frame -> LINGER_CALL.equals(frame.getMethodName()));
    }
}
