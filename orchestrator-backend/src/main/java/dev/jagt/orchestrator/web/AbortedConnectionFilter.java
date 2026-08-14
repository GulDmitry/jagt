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
 * Drops the one ERROR the board prints for connections nobody ever meant to use.
 *
 * <p>Tomcat configures each accepted socket on the acceptor thread, and {@code SO_LINGER} is the FIRST option
 * it sets that is not guarded — every other one is either unset by default or, like {@code TCP_NODELAY},
 * wrapped in a catch of its own (see {@code SocketProperties#setProperties}). So when the peer has already
 * torn the connection down between {@code accept()} and that call, macOS answers the setsockopt with EINVAL,
 * and Tomcat reports "Error setting socket options" with a {@link SocketException} — for a connection that
 * carried no request. There is no knob for it: {@code AbstractProtocol} sets {@code connectionLinger} to its
 * default in the constructor, which is exactly what makes both linger properties non-null and the call
 * unconditional.
 *
 * <p>jagt hands out abandoned connections all day: a browser pre-connects to the board speculatively, Node
 * clients (Claude Code's MCP calls, {@code mcp_client.js}) race IPv6 and IPv4 to {@code localhost} and destroy
 * the loser, every {@code curl} probe of {@code /state} is one more. All of them are normal, none of them is
 * an error, and a log that cries wolf on them is a log nobody reads.
 *
 * <p>Scoped to precisely that event — Tomcat's own network logger, a {@code SocketException}, and a
 * {@code setSoLinger} frame in the trace; matching the frame rather than the message keeps it independent of
 * the C library's locale. A failure to configure any OTHER option still reaches the log. A systemic
 * {@code SO_LINGER} failure would be silent, and that is accepted: Tomcat destroys the socket in the same
 * catch, so the symptom is a board that answers nothing at all — impossible to miss, and impossible to
 * confuse with the per-connection noise this drops.
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
