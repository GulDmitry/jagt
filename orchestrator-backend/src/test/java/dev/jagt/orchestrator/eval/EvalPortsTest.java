package dev.jagt.orchestrator.eval;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EvalPortsTest {

    @Test
    void twoRunsInOneMachineBindTheirOwnPortAtTheSameTime() throws Exception {
        int first = EvalPorts.free();
        int second = EvalPorts.free();

        try (ServerSocket one = new ServerSocket(first, 1, InetAddress.getLoopbackAddress());
             ServerSocket other = new ServerSocket(second, 1, InetAddress.getLoopbackAddress())) {
            assertThat(one.getLocalPort()).isNotEqualTo(other.getLocalPort());
        }
    }

    @Test
    void aPortIsNeverHandedOutTwiceInOneJvm() {
        long distinct = Stream.generate(EvalPorts::free).limit(50).distinct().count();

        assertThat(distinct).isEqualTo(50);
    }
}
