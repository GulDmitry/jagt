package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.ProjectConfig;


import java.nio.file.Path;
import java.util.Locale;

/** The kind of code host a project reads, the project's key over the orchestrator's over {@link #DEFAULT}. */
record CodeHostChoice(String kind, HostSettings host) {

    static final String DEFAULT = "github";
    private static final String ROOT = "orchestrator";

    static CodeHostChoice of(ConfigFile config, String project, ProjectConfig declared) {
        String selectedBy = ROOT + ".projects." + project + ".codeHost";
        String kind = declared.codeHost();
        if (kind == null || kind.isBlank()) {
            selectedBy = ROOT + ".codeHost";
            kind = config.codeHost();
        }
        if (kind == null || kind.isBlank()) {
            kind = declared.gitRemote().local() ? ProjectConfig.NO_CODE_HOST : DEFAULT;
        }
        return new CodeHostChoice(kind.strip().toLowerCase(Locale.ROOT), new HostSettings(project, selectedBy,
                declared.host().name(), Path.of(String.valueOf(declared.path())),
                declared.host().pipelineCommand(), declared.host().retryCommand(), declared.host().loginCommand(),
                declared.host().closeCommand()));
    }
}
