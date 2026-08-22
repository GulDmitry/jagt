package dev.jagt.orchestrator.service;

import lombok.With;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Reads config.json on every access, so edits are picked up without restarting the backend.
 *
 * <p>A whole section may be omitted — {@link ConfigFile}'s accessors coalesce a missing section to its
 * all-default instance, so callers never null-check.
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record ConfigFile(Map<String, ProjectConfig> projects, ViewerConfig viewer, DashboardConfig dashboard,
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
        public record DashboardConfig(Integer refreshSeconds, Integer reservedRows) {

            public static DashboardConfig defaults() {
                return new DashboardConfig(null, null);
            }

            /** {@code <= 0} disables the periodic refresh: nothing redraws except on input or resize. */
            public int refreshSecondsOrDefault() {
                if (refreshSeconds == null) {
                    return 10;
                }
                return refreshSeconds < 0 ? 0 : refreshSeconds;
            }

            /** A MINIMUM of rows kept free for output and input, so it CAPS how tall the dashboard may grow. */
            public int reservedRowsOrDefault() {
                if (reservedRows == null) {
                    return 17;
                }
                return reservedRows < 0 ? 0 : reservedRows;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record CodeReviewConfig(String mrTitlePattern, Boolean postReviewReplies,
                                       List<String> reviewReplyAuthors,
                                       MergeRequestDefaults mergeRequestDefaults) {

            /**
             * Defaulted true: a task branch is disposable once merged, and its intermediate commits are review
             * noise, not history.
             */
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

            /**
             * Whether a ship posts EVERY drafted reply, so the file it left behind is spent. False also for an
             * author filter: there the agent posts some and leaves the rest, and those are the human's to send.
             */
            public boolean shipPostsEveryDraft() {
                return postReviewRepliesOrDefault() && reviewReplyAuthorsOrEmpty().isEmpty();
            }

            /** False: drafted replies stay in the worktree for the human, and only code is pushed. */
            public boolean postReviewRepliesOrDefault() {
                return postReviewReplies == null || postReviewReplies;
            }

            /**
             * Optional whitelist: when non-empty, drafted review replies are posted ONLY to threads whose
             * author matches one of these (case-insensitive substring, e.g. "coderabbit"). Empty = post to
             * every thread (the default). Only meaningful when {@link #postReviewRepliesOrDefault()} is true.
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

            /**
             * A fresh worktree is an untrusted project where the human's own resolved style may not apply, so it
             * can be forced here. Null: nothing is written and the agent resolves its own.
             */
            public String outputStyleOrNull() {
                return outputStyle == null || outputStyle.isBlank() ? null : outputStyle.strip();
            }

            /**
             * How often every running session is looked at. It is the cadence for a session whose harness
             * reports NOTHING — one that does says so in seconds either way, which is what makes ten minutes
             * a sane default rather than a gamble.
             */
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
            return new ConfigFile(Map.of(), null, null, null, null, null, null);
        }

        // The raw field is still what the withers copy, so an omitted section stays null until set.
        @Override
        public ViewerConfig viewer() {
            return viewer == null ? ViewerConfig.defaults() : viewer;
        }

        @Override
        public DashboardConfig dashboard() {
            return dashboard == null ? DashboardConfig.defaults() : dashboard;
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

    // Hand-edited, so it may carry comments.
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build();
    private final OrchestratorPaths paths;

    public ConfigFile load() {
        if (!Files.exists(paths.configFile())) {
            throw new IllegalStateException("Missing " + paths.configFile()
                    + " — copy config.json.dist to config.json and fill in your projects.");
        }
        try {
            ConfigFile config = mapper.readValue(Files.readString(paths.configFile()), ConfigFile.class);
            return config.projects() == null ? config.withProjects(Map.of()) : config;
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
