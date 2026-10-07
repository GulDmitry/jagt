package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

final class ScriptedProcesses extends ProcessRunner {

    private final List<String> prefix;
    private final Supplier<Processes.Result> answer;

    private ScriptedProcesses(List<String> prefix, Supplier<Processes.Result> answer) {
        this.prefix = prefix;
        this.answer = answer;
    }

    static ScriptedProcesses answering(List<String> prefix, Processes.Result result) {
        return new ScriptedProcesses(prefix, () -> result);
    }

    static ScriptedProcesses failingToStart(String program) {
        return new ScriptedProcesses(List.of(program), () -> {
            throw new IllegalStateException("Failed to start command: " + program + " (not installed)");
        });
    }

    @Override
    public Processes.Result run(Path workingDir, Duration timeout, List<String> command) {
        return command.size() >= prefix.size() && command.subList(0, prefix.size()).equals(prefix)
                ? answer.get() : super.run(workingDir, timeout, command);
    }
}
