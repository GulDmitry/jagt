package dev.jagt.orchestrator.service;

import lombok.With;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads {@code jagt.yml} on every access, so edits are picked up without restarting — which is why this parses the
 * file itself instead of taking Spring's binding of it, that one happening once at startup. A whole section may be
 * omitted: {@link ConfigFile}'s accessors coalesce a missing one to its all-default instance.
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record ConfigFile(Map<String, ProjectConfig> projects, ViewerConfig viewer,
                             CodeReviewConfig codeReview, AgentConfig agent, WorktreeConfig worktree,
                             AutoReviewConfig autoReview) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record ViewerConfig(String tmuxSession, String viewMode, Boolean keepViewer) {

            public static ViewerConfig defaults() {
                return new ViewerConfig(null, null, null);
            }

            public boolean keepViewerOrDefault() {
                return keepViewer == null || keepViewer;
            }

            public boolean sharedView() {
                return viewMode == null || "shared".equalsIgnoreCase(viewMode);
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record CodeReviewConfig(String mrTitlePattern, Boolean postReviewReplies,
                                       List<String> reviewReplyAuthors,
                                       MergeRequestDefaults mergeRequestDefaults) {

            /** Defaulted true: a task branch's intermediate commits are review noise, not history. */
            @JsonIgnoreProperties(ignoreUnknown = true)
            @With
            public record MergeRequestDefaults(Boolean removeSourceBranch, Boolean squash) {

                public static MergeRequestDefaults defaults() {
                    return new MergeRequestDefaults(null, null);
                }

                public boolean removeSourceBranchOrDefault() {
                    return removeSourceBranch == null || removeSourceBranch;
                }

                public boolean squashOrDefault() {
                    return squash == null || squash;
                }
            }

            public static CodeReviewConfig defaults() {
                return new CodeReviewConfig(null, null, null, null);
            }

            public MergeRequestDefaults mergeRequestDefaultsOrDefault() {
                return mergeRequestDefaults == null ? MergeRequestDefaults.defaults() : mergeRequestDefaults;
            }

            public String mrTitlePatternOrDefault() {
                return mrTitlePattern == null || mrTitlePattern.isBlank() ? "{ticket} {title}" : mrTitlePattern;
            }

            /** Whether a ship posts EVERY drafted reply. False under an author filter, which leaves the rest. */
            public boolean shipPostsEveryDraft() {
                return postReviewRepliesOrDefault() && reviewReplyAuthorsOrEmpty().isEmpty();
            }

            /** False: drafted replies stay in the worktree for the human, and only code is pushed. */
            public boolean postReviewRepliesOrDefault() {
                return postReviewReplies == null || postReviewReplies;
            }

            /**
             * When non-empty, replies are posted ONLY to threads whose author matches one of these
             * (case-insensitive substring). Empty = every thread.
             */
            public List<String> reviewReplyAuthorsOrEmpty() {
                return reviewReplyAuthors == null ? List.of() : reviewReplyAuthors;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record AgentConfig(String outputStyle, Integer probeSeconds) {

            public static AgentConfig defaults() {
                return new AgentConfig(null, null);
            }

            /** Null: nothing is written and the agent resolves its own style. */
            public String outputStyleOrNull() {
                return outputStyle == null || outputStyle.isBlank() ? null : outputStyle.strip();
            }

            /** How often every running session is looked at; the cadence for one whose harness reports NOTHING. */
            public int probeSecondsOrDefault() {
                return probeSeconds == null || probeSeconds <= 0 ? 600 : probeSeconds;
            }
        }

        /** The numbers only; {@code AutoReviewCadence} is the policy that reads them. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record AutoReviewConfig(Boolean enabled, Integer windowHours, Integer minIntervalMinutes,
                                       Integer maxIntervalMinutes) {

            public static AutoReviewConfig defaults() {
                return new AutoReviewConfig(null, null, null, null);
            }

            public boolean enabledOrDefault() {
                return enabled != null && enabled;
            }

            public int windowHoursOrDefault() {
                return windowHours == null || windowHours <= 0 ? 24 : windowHours;
            }

            public int minIntervalMinutesOrDefault() {
                return minIntervalMinutes == null || minIntervalMinutes <= 0 ? 10 : minIntervalMinutes;
            }

            public int maxIntervalMinutesOrDefault() {
                int min = minIntervalMinutesOrDefault();
                if (maxIntervalMinutes == null || maxIntervalMinutes < min) {
                    return Math.max(60, min);
                }
                return maxIntervalMinutes;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record WorktreeConfig(List<String> copyGlobs) {

            public static WorktreeConfig defaults() {
                return new WorktreeConfig(null);
            }

            /** Glob patterns relative to the repository root. */
            public List<String> copyGlobsOrDefault() {
                return copyGlobs == null || copyGlobs.isEmpty() ? List.of("**/.env") : copyGlobs;
            }
        }

        public static ConfigFile defaults() {
            return new ConfigFile(Map.of(), null, null, null, null, null);
        }

        // The raw field is still what the withers copy, so an omitted section stays null until set.
        @Override
        public ViewerConfig viewer() {
            return viewer == null ? ViewerConfig.defaults() : viewer;
        }

        @Override
        public CodeReviewConfig codeReview() {
            return codeReview == null ? CodeReviewConfig.defaults() : codeReview;
        }

        @Override
        public AgentConfig agent() {
            return agent == null ? AgentConfig.defaults() : agent;
        }

        @Override
        public WorktreeConfig worktree() {
            return worktree == null ? WorktreeConfig.defaults() : worktree;
        }

        @Override
        public AutoReviewConfig autoReview() {
            return autoReview == null ? AutoReviewConfig.defaults() : autoReview;
        }

    }

    /** The one root everything a human writes lives under, Spring's keys and jagt's own alike. */
    private static final String ROOT = "orchestrator";

    private final JsonMapper mapper = new JsonMapper();
    private final OrchestratorPaths paths;

    public ConfigFile load() {
        Object section = section();
        if (section == null) {
            return ConfigFile.defaults();
        }
        ConfigFile config = mapper.convertValue(section, ConfigFile.class);
        return config.projects() == null ? config.withProjects(Map.of()) : config;
    }

    /** The names the human wrote under {@code orchestrator}, INCLUDING ones nothing binds — both readers drop those. */
    public Set<String> declaredKeys() {
        Object section = section();
        return section instanceof Map<?, ?> keys
                ? keys.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet())
                : Set.of();
    }

    private Object section() {
        Path file = paths.configFile();
        if (!Files.exists(file)) {
            throw new IllegalStateException("Missing " + file
                    + " — copy jagt.yml.dist to jagt.yml and fill in your projects.");
        }
        try {
            // SafeConstructor: the file is hand-edited, and a YAML tag naming a class is not a setting.
            Object tree = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
            return tree instanceof Map<?, ?> document ? document.get(ROOT) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("Cannot read " + file + ": " + malformed.getMessage(), malformed);
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
