package dev.jagt.orchestrator.surface.board;

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
 * Drops the ERROR from a peer tearing the connection down between {@code accept()} and Tomcat's
 * {@code setSoLinger}, for which Tomcat offers no knob. Matched by the frame, not the message: the message is
 * locale-dependent.
 */
@Component
public class AbortedConnectionFilter extends TurboFilter {

    private static final String TOMCAT_NET_LOGGER = "org.apache.tomcat.util.net.";
    private static final String LINGER_CALL = "setSoLinger";

    /** Bean creation is before the connector binds its port, so no abandoned connection can arrive earlier. */
    @PostConstruct
    void install() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            start();
            context.addTurboFilter(this);
        }
    }

    /** The logger context is the JVM's, so a filter left installed outlives this application context. */
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
