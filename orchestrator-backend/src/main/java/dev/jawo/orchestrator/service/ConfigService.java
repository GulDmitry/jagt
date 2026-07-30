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
    public record ConfigFile(Map<String, ProjectConfig> projects, String tmuxSession, String viewMode,
                             Boolean keepViewer, String mrTitlePattern, String agentOutputStyle,
                             Boolean postReviewReplies, java.util.List<String> reviewReplyAuthors,
                             java.util.List<String> worktreeCopyGlobs) {

        /** Default true: the agents window/tab stays open (reserved) after the last task is done. */
        public boolean keepViewerOrDefault() {
            return keepViewer == null || keepViewer;
        }

        /** Placeholders {ticket} and {title}. Default: the ticket id, a space, then the Jira title. */
        public String mrTitlePatternOrDefault() {
            return mrTitlePattern == null || mrTitlePattern.isBlank() ? "{ticket} {title}" : mrTitlePattern;
        }

        /**
         * Optional Claude output style pinned into each agent worktree's settings. Default null:
         * nothing is written and agents use Claude's own resolved style. A fresh worktree is an
         * untrusted project where the human's global style may not apply, so this lets the human
         * force one (e.g. "sob-ai:Engineer") without jawo reading their global config.
         */
        public String agentOutputStyleOrNull() {
            return agentOutputStyle == null || agentOutputStyle.isBlank() ? null : agentOutputStyle.strip();
        }

        /**
         * Default true: on `ship`, the agent posts its drafted review replies to the MR threads (current
         * behaviour). False: the replies stay in {@code review_replies.md} for the human — only code is
         * pushed. Either way the agent drafts a per-comment "comment -> intended reply" block each round.
         */
        public boolean postReviewRepliesOrDefault() {
            return postReviewReplies == null || postReviewReplies;
        }

        /**
         * Optional whitelist: when non-empty, drafted review replies are posted ONLY to threads whose
         * author matches one of these (case-insensitive substring, e.g. "coderabbit"). Empty = post to
         * every thread (the default). Only meaningful when {@link #postReviewRepliesOrDefault()} is true.
         */
        public java.util.List<String> reviewReplyAuthorsOrEmpty() {
            return reviewReplyAuthors == null ? java.util.List.of() : reviewReplyAuthors;
        }

        /**
         * Glob patterns (relative to the repo root) of gitignored local files — secrets, keys, module
         * {@code .env}, SSL certs — to copy from the base repo into each new worktree so the app can
         * run there. Default {@code ["**}{@code /.env"]}; projects add their own (e.g. {@code
         * "**}{@code /*.pem"}, {@code "**}{@code /gcs-key-file.json"}). Not hardcoded — per project.
         */
        public java.util.List<String> worktreeCopyGlobsOrDefault() {
            return worktreeCopyGlobs == null || worktreeCopyGlobs.isEmpty()
                    ? java.util.List.of("**/.env") : worktreeCopyGlobs;
        }
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
                    config.tmuxSession(), config.viewMode(), config.keepViewer(), config.mrTitlePattern(),
                    config.agentOutputStyle(), config.postReviewReplies(), config.reviewReplyAuthors(),
                    config.worktreeCopyGlobs());
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
