package dev.jawo.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jawo.orchestrator.config.OrchestratorPaths;
import dev.jawo.orchestrator.model.ProjectConfig;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * Reads config.json (user-maintained SSOT for projects). Re-read on every access
 * so edits are picked up without restarting the backend.
 */
@Service
public class ConfigService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfigFile(Map<String, ProjectConfig> projects, String tmuxSession, String viewMode) {
    }

    private final ObjectMapper mapper;
    private final OrchestratorPaths paths;

    public ConfigService(ObjectMapper mapper, OrchestratorPaths paths) {
        this.mapper = mapper;
        this.paths = paths;
    }

    public ConfigFile load() {
        if (!Files.exists(paths.configFile())) {
            throw new IllegalStateException("Missing " + paths.configFile()
                    + " — copy config.json.dist to config.json and fill in your projects.");
        }
        try {
            ConfigFile config = mapper.readValue(Files.readString(paths.configFile()), ConfigFile.class);
            return new ConfigFile(config.projects() == null ? Map.of() : config.projects(),
                    config.tmuxSession(), config.viewMode());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config file " + paths.configFile(), e);
        }
    }

    public ProjectConfig project(String projectKey) {
        Map<String, ProjectConfig> projects = load().projects();
        ProjectConfig project = projects.get(projectKey);
        if (project == null) {
            throw new IllegalArgumentException(
                    "Unknown project '" + projectKey + "'. Known projects: " + projects.keySet());
        }
        return project;
    }
}
