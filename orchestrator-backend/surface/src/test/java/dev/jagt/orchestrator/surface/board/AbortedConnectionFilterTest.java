package dev.jagt.orchestrator.surface.board;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.SocketException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

class AbortedConnectionFilterTest {

    private final AbortedConnectionFilter filter = new AbortedConnectionFilter();

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

    @Test
    void registersItselfWithTheLoggerContextAndTakesItselfBackOutWhenTheBackendCloses() {
        LoggerContext context = new LoggerContext();

        filter.installInto(context);
        boolean installed = context.getTurboFilterList().contains(filter);
        filter.removeFrom(context);

        assertThat(installed).isTrue();
        assertThat(context.getTurboFilterList()).doesNotContain(filter);
    }

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
                        new LoggerContext().getLogger(BoardApiController.class.getName())), aborted),
                Arguments.of(named("an ordinary event carrying no exception at all", tomcat), null));
    }
}
