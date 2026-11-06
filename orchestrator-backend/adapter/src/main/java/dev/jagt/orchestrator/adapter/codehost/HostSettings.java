package dev.jagt.orchestrator.adapter.codehost;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/** What a project's {@code jagt.yml} gives the driver answering for it; {@code selectedBy} is the key that chose the driver. */
public record HostSettings(String project, String selectedBy, @Nullable String hostName, Path dir,
                           List<String> pipelineCommand, List<String> retryCommand,
                           List<String> loginCommand, List<String> closeCommand) {

    private static final String ROOT = "orchestrator";

    public String key(String name) {
        return ROOT + ".projects." + project + "." + name;
    }
}
