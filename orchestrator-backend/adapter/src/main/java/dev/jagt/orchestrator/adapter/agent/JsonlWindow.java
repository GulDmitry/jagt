package dev.jagt.orchestrator.adapter.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The whole lines of a bounded read of a log another process appends to. {@code bytes} is how far the next read
 * moves: through the last newline, never into a half-written record.
 */
record JsonlWindow(String[] lines, long bytes) {

    static JsonlWindow of(Path log, long from, long limit) throws IOException {
        byte[] window;
        try (InputStream in = Files.newInputStream(log)) {
            in.skipNBytes(from);
            window = in.readNBytes((int) Math.min(limit, Integer.MAX_VALUE));
        }
        int lastLine = window.length - 1;
        while (lastLine >= 0 && window[lastLine] != '\n') {
            lastLine--;
        }
        if (lastLine < 0) {
            // A record longer than the window: waiting for a newline that is not coming would freeze the spend
            // for good. A SHORT window is the tail being written.
            return new JsonlWindow(new String[0], window.length < limit ? 0 : window.length);
        }
        return new JsonlWindow(new String(window, 0, lastLine, StandardCharsets.UTF_8).split("\n"), lastLine + 1L);
    }
}
