package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

public final class StandProject {

    private static final String GREET = """
            #!/bin/sh
            echo "Hello, $1"
            """;
    private static final String CHECK = """
            #!/bin/sh
            set -e
            cd "$(dirname "$0")"
            [ "$(./greet.sh Ada)" = "Hello, Ada" ] || { echo "greet.sh Ada: wrong greeting"; exit 1; }
            echo "all checks passed"
            """;
    private static final String README = """
            # greet

            `./greet.sh <name>` greets. `./check.sh` is the whole test suite and must pass.
            """;

    private final Path origin;
    private final Path repo;

    private StandProject(Path origin, Path repo) {
        this.origin = origin;
        this.repo = repo;
    }

    public static StandProject create(Path root) {
        Path origin = root.resolve("origin.git");
        Path repo = root.resolve("work").resolve("proj");
        try {
            Files.createDirectories(origin);
            Files.createDirectories(repo);
            StandShell.git(origin, "init", "--bare", "--initial-branch=main", ".");
            StandShell.git(origin, "config", "core.logAllRefUpdates", "always");
            StandShell.git(repo, "init", "--initial-branch=main", ".");
            StandShell.git(repo, "config", "user.email", "stand@example.com");
            StandShell.git(repo, "config", "user.name", "jagt stand");
            Files.writeString(repo.resolve("README.md"), README);
            script(repo.resolve("greet.sh"), GREET);
            script(repo.resolve("check.sh"), CHECK);
        } catch (IOException e) {
            throw new StandFailure("cannot write the stand project under " + root, e);
        }
        StandShell.git(repo, "add", "README.md", "greet.sh", "check.sh");
        StandShell.git(repo, "commit", "-m", "A greeting and its check");
        StandShell.git(repo, "remote", "add", "origin", "file://" + origin);
        StandShell.git(repo, "push", "-u", "origin", "main");
        StandShell.git(repo, "push", "origin", "main:refs/heads/dev");
        return new StandProject(origin, repo);
    }

    public Path repo() {
        return repo;
    }

    public boolean onDeployBranch(String branch) {
        return StandShell.gitSucceeds(origin, "merge-base", "--is-ancestor", "refs/heads/" + branch, "refs/heads/dev");
    }

    public String deployBranchFile(String file) {
        return StandShell.git(origin, "show", "refs/heads/dev:" + file);
    }

    public boolean deployBranchEverHeld(String text) {
        return !StandShell.git(origin, "log", "-S" + text, "--format=%H", "refs/heads/dev").isBlank();
    }

    public String head(String branch) {
        return StandShell.gitSucceeds(origin, "rev-parse", "--verify", "refs/heads/" + branch)
                ? StandShell.git(origin, "rev-parse", "refs/heads/" + branch).strip() : "";
    }

    public String commitOnDeployBranch(Path scratch, String file, String content, String message) {
        if (!Files.isDirectory(scratch)) {
            StandShell.git(origin.getParent(), "clone", "--branch", "dev", "file://" + origin, scratch.toString());
            StandShell.git(scratch, "config", "user.email", "outsider@example.com");
            StandShell.git(scratch, "config", "user.name", "someone else");
        }
        StandShell.git(scratch, "pull", "--ff-only", "origin", "dev");
        try {
            Files.writeString(scratch.resolve(file), content);
        } catch (IOException e) {
            throw new StandFailure("cannot write " + file + " under " + scratch, e);
        }
        StandShell.git(scratch, "commit", "-am", message);
        StandShell.git(scratch, "push", "origin", "HEAD:refs/heads/dev");
        return StandShell.git(scratch, "rev-parse", "HEAD").strip();
    }

    private static void script(Path file, String content) throws IOException {
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
}
