package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

final class ServerFixtures {

  record Where(Path root, Path inside, int port, boolean image) {}

  private ServerFixtures() {}

  static void write(Path shipped, Where where, String password) throws IOException {
    Path root = where.root();
    StandShell.git(root, "init", "-q", "-b", "main", "work");
    Files.writeString(root.resolve("work/a.txt"), "hi\n");
    StandShell.git(root, "-C", "work", "add", "a.txt");
    StandShell.git(
        root,
        "-C",
        "work",
        "-c",
        "user.name=fixture",
        "-c",
        "user.email=fixture@localhost",
        "commit",
        "-qm",
        "init");
    StandShell.git(root, "clone", "-q", "--bare", "work", "origin.git");
    StandConfig.install(shipped, root);
    Files.writeString(root.resolve("password-hash"), new BCryptPasswordEncoder().encode(password));
    Path agent = root.resolve("fake-claude.sh");
    Files.writeString(
        agent,
        resource("/server/fake-claude.sh").replace("@PORT@", Integer.toString(where.port())));
    if (!agent.toFile().setExecutable(true, false)) {
      throw new StandFailure("cannot make " + agent + " executable");
    }
    Files.writeString(
        root.resolve("jagt.yml"),
        starter(Files.readString(shipped.resolve("jagt.server.yml.dist")), where));
  }

  private static String starter(String dist, Where where) {
    String text =
        swap(
            dist,
            "https://example.com/team/repo.git",
            where.inside().resolve("origin.git").toString());
    text =
        swap(
            text,
            "${JAGT_PASSWORD_HASH}",
            "${file:" + where.inside().resolve("password-hash") + "}");
    text = swap(text, "${JAGT_PUBLIC_URL}", "http://127.0.0.1:" + where.port());
    text = swap(text, "cookie.secure: true", "cookie.secure: false");
    text =
        swap(
            text,
            "  claude:\n",
            "  claude:\n    command: \"" + where.inside().resolve("fake-claude.sh") + "\"\n");
    text = where.image() ? text : swap(text, "  platform: linux", "");
    return swap(
        text,
        "    acceptance:\n      enabled: true",
        "    master: { mode: \"off\" }\n    acceptance:\n      enabled: false");
  }

  private static String swap(String text, String from, String to) {
    if (!text.contains(from)) {
      throw new StandFailure("jagt.server.yml.dist no longer holds " + from);
    }
    return text.replace(from, to);
  }

  private static String resource(String name) throws IOException {
    try (InputStream in = ServerFixtures.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new StandFailure("the stand lacks " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
