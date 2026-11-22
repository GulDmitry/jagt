package dev.jagt.orchestrator.surface.mcp.refusal;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Drawn at each start and left where only the root's own MCP config reads it: the Master is whoever
 * presents it.
 */
@Component
public class MasterToken implements RootToken {

  public static final String HEADER = "X-Jagt-Master";

  private final byte[] token;

  public MasterToken(OrchestratorPaths paths) {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String value = HexFormat.of().formatHex(random);
    this.token = value.getBytes(StandardCharsets.UTF_8);
    write(paths.token(), value);
  }

  @Override
  public boolean presentedBy(Function<String, @Nullable String> header) {
    return matches(header.apply(HEADER));
  }

  public boolean matches(@Nullable String presented) {
    return presented != null
        && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
  }

  private static void write(Path file, String value) {
    try {
      Files.createDirectories(file.getParent());
      Files.deleteIfExists(file);
      createOwnerOnly(file);
      Files.writeString(file, value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void createOwnerOnly(Path file) throws IOException {
    if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.createFile(
          file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      return;
    }
    Files.createFile(file);
    AclEntry ownerOnly =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(Files.getOwner(file))
            .setPermissions(EnumSet.allOf(AclEntryPermission.class))
            .build();
    Files.getFileAttributeView(file, AclFileAttributeView.class).setAcl(List.of(ownerOnly));
  }
}
