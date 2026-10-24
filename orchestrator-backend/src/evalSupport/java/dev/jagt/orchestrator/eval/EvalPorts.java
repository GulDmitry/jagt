package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EvalPorts {

    private static final Set<Integer> HANDED_OUT = ConcurrentHashMap.newKeySet();

    private EvalPorts() {
    }

    public static int free() {
        while (true) {
            try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                int port = probe.getLocalPort();
                if (HANDED_OUT.add(port)) {
                    return port;
                }
            } catch (IOException noSocket) {
                throw new UncheckedIOException("no free loopback port", noSocket);
            }
        }
    }
}
