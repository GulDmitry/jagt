package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Drawn at each start and left where only the root's own MCP config reads it: the Master is whoever presents it. */
@Component
public class MasterToken {

    public static final String HEADER = "X-Jagt-Master";
    public static final String FILE = ".jagt/master-token";

    private final byte[] token;

    public MasterToken(OrchestratorPaths paths) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String value = HexFormat.of().formatHex(random);
        this.token = value.getBytes(StandardCharsets.UTF_8);
        write(paths.root().resolve(FILE), value);
    }

    public boolean matches(String presented) {
        return presented != null && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path file, String value) {
        try {
            Files.createDirectories(file.getParent());
            Files.deleteIfExists(file);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(file, value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
