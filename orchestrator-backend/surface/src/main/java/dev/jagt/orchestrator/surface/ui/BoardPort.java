package dev.jagt.orchestrator.surface.ui;

import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

final class BoardPort {

    private BoardPort() {
    }

    static String configured(ConfigurableEnvironment environment) {
        return environment.getProperty("server.port", "8290").trim();
    }

    static boolean taken(String port) {
        try {
            int number = Integer.parseInt(port);
            try (ServerSocket probe = new ServerSocket()) {
                probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), number));
                return false;
            }
        } catch (IOException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
