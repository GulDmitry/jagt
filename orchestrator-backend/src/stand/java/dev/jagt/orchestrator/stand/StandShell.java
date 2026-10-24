package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StandShell {

    private StandShell() {
    }

    public record Result(int exit, String output) {
    }

    public static String git(Path cwd, String... args) {
        Result result = run(cwd, gitCommand(args));
        if (result.exit() != 0) {
            throw new StandFailure("git " + String.join(" ", args) + " failed in " + cwd + ": " + result.output());
        }
        return result.output();
    }

    public static boolean gitSucceeds(Path cwd, String... args) {
        return run(cwd, gitCommand(args)).exit() == 0;
    }

    public static Result run(Path cwd, List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
            builder.environment().keySet().removeIf(name -> name.startsWith("CLAUDE"));
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, output);
            }
            return new Result(process.exitValue(), output);
        } catch (IOException e) {
            return new Result(-1, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "interrupted");
        }
    }

    public static void stopTree(ProcessHandle process) {
        List<ProcessHandle> tree = new ArrayList<>(process.descendants().toList());
        tree.add(process);
        tree.forEach(ProcessHandle::destroy);
        for (ProcessHandle handle : tree) {
            handle.onExit().completeOnTimeout(handle, 20, TimeUnit.SECONDS).join();
        }
        tree.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    public static void killTmux(Path root) {
        sockets(root).forEach(socket -> run(root, List.of("tmux", "-S", socket.toString(), "kill-server")));
    }

    public static String windows(Path root) {
        return sockets(root).stream().map(socket -> run(root, List.of("tmux", "-S", socket.toString(), "list-windows", "-a",
                "-F", "#{window_name}")).output().strip().replace('\n', ' ')).collect(Collectors.joining(" "));
    }

    public static String screens(Path root) {
        StringBuilder screens = new StringBuilder();
        for (Path socket : sockets(root)) {
            String windows = run(root, List.of("tmux", "-S", socket.toString(), "list-windows", "-a", "-F",
                    "#{session_name}:#{window_index} #{window_name}")).output();
            windows.lines().map(line -> line.split(" ", 2)).forEach(window -> screens.append("=== ")
                    .append(String.join(" ", window)).append('\n')
                    .append(run(root, List.of("tmux", "-S", socket.toString(), "capture-pane", "-p", "-S", "-300",
                            "-t", window[0])).output()));
        }
        return screens.toString();
    }

    private static List<Path> sockets(Path root) {
        Path tmux = root.resolve("tmux");
        if (!Files.isDirectory(tmux)) {
            return List.of();
        }
        try (Stream<Path> sockets = Files.list(tmux)) {
            return sockets.map(dir -> dir.resolve("default")).filter(Files::exists).toList();
        } catch (IOException e) {
            throw new StandFailure("cannot list " + tmux, e);
        }
    }

    public static void deleteRoot(Path root) {
        if (!StandRoots.named(root)) {
            throw new StandFailure("refusing to delete " + root + ": not a stand root");
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new StandFailure("cannot delete " + root, e);
        }
    }

    private static List<String> gitCommand(String... args) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        return command;
    }
}
