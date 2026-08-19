package dev.jagt.orchestrator.surface.board;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

class AbortedConnectionFilterTest {

    private final AbortedConnectionFilter filter = new AbortedConnectionFilter();

    /**
     * A browser pre-connect, the losing half of a Node client's IPv6/IPv4 race, a probe of {@code /state}: the
     * peer is gone before Tomcat sets {@code SO_LINGER} on the socket it just accepted, macOS answers EINVAL,
     * and the board's log gets an ERROR for a connection that carried no request.
     */
    @Test
    void dropsTomcatsErrorForAConnectionTheClientAbortedBeforeItCouldBeConfigured() {
        SocketException aborted = new SocketException("Invalid argument");
        aborted.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("sun.nio.ch.SocketAdaptor", "setSoLinger", "SocketAdaptor.java", 254),
                new StackTraceElement("org.apache.tomcat.util.net.SocketProperties", "setProperties", null, 203)});

        FilterReply reply = filter.decide(null, new LoggerContext().getLogger("org.apache.tomcat.util.net.NioEndpoint"),
                Level.ERROR, "Error setting socket options", null, aborted);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    /**
     * The logger context belongs to the JVM, not to the backend that installs the filter into it — so a run
     * that closes one application context and opens another (every test run does) must not leave its filter
     * behind to be consulted on someone else's log calls.
     */
    @Test
    void registersItselfWithTheLoggerContextAndTakesItselfBackOutWhenTheBackendCloses() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        filter.install();
        boolean installed = context.getTurboFilterList().contains(filter);
        filter.uninstall();

        assertThat(installed).isTrue();
        assertThat(context.getTurboFilterList()).doesNotContain(filter);
    }

    /**
     * The trap this filter must not become: one that also swallows the socket errors worth waking up for. A
     * connector that cannot be configured for a REAL reason still has to say so.
     */
    @ParameterizedTest
    @MethodSource("eventsThatMustStillBeLogged")
    void keepsEverySocketErrorThatIsNotThatOne(Logger logger, Throwable error) {
        FilterReply reply = filter.decide(null, logger, Level.ERROR, "Error setting socket options", null, error);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    static Stream<Arguments> eventsThatMustStillBeLogged() {
        Logger tomcat = new LoggerContext().getLogger("org.apache.tomcat.util.net.NioEndpoint");
        SocketException otherOption = new SocketException("No buffer space available");
        otherOption.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("java.net.Socket", "setReceiveBufferSize", "Socket.java", 1),
                new StackTraceElement("org.apache.tomcat.util.net.SocketProperties", "setProperties", null, 183)});
        SocketException aborted = new SocketException("Invalid argument");
        aborted.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("sun.nio.ch.SocketAdaptor", "setSoLinger", "SocketAdaptor.java", 254)});

        return Stream.of(
                Arguments.of(named("another socket option failing on the same line", tomcat), otherOption),
                Arguments.of(named("the same failure reported by anyone but Tomcat's connector",
                        new LoggerContext().getLogger("dev.jagt.orchestrator.surface.board.BoardApiController")), aborted),
                Arguments.of(named("an ordinary event carrying no exception at all", tomcat), null));
    }
}
