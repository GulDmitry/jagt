package dev.jagt.orchestrator.service;

import lombok.With;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TrackerMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
                             AutoReviewConfig autoReview, MasterConfig master, TrackerConfig tracker) {

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

        /**
         * The Master session: EXPERIMENTAL, and off unless a human turned it on. {@code brief} is the file
         * saying what it judges — the thing that outlives every session it runs.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record MasterConfig(String mode, String brief, String model, List<String> mine,
                                   List<String> withhold) {

            /** Copied from `master-brief.md.dist`, beside `jagt.yml`, and not versioned. */
            private static final String BRIEF = "master-brief.md";

            public static MasterConfig defaults() {
                return new MasterConfig(null, null, null, null, null);
            }

            /** Steps a human keeps for themselves, by name; anything not named here the Master holds in `act`. */
            public List<String> mineOrNone() {
                return mine == null ? List.of() : mine;
            }

            /** The name this list had before it was written from the human's side; refused rather than ignored. */
            public boolean usesTheRetiredName() {
                return withhold != null;
            }

            /** Whether the session may do this, which in any mode but `act` is never. */
            public boolean may(MasterRight right) {
                return modeOrOff() == MasterMode.ACT
                        && mineOrNone().stream().noneMatch(named -> MasterRight.of(named)
                                .filter(right::equals).isPresent());
            }

            /** The file it judges by. Named here so an install that copied the shipped one sets nothing. */
            public String briefOrDefault() {
                return brief == null || brief.isBlank() ? BRIEF : brief;
            }

            /** Blank inherits whatever the agent CLI would have used; reading is the half worth paying for. */
            public String modelOrInherited() {
                return model == null || model.isBlank() ? "" : model.strip();
            }

            /** OFF where the word is not one this machine has: an unreadable setting starts nothing. */
            public MasterMode modeOrOff() {
                return MasterMode.of(mode).orElse(MasterMode.OFF);
            }

            public boolean running() {
                return modeOrOff() != MasterMode.OFF;
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

        /**
         * The two ends the tracker drives: work arriving unasked, and a task closing on the stage that says it
         * landed. {@code workflow} names the vocabulary those stages are read in and is bound at STARTUP, unlike
         * everything beside it. Nothing carries a default stage name: no two installs spell their workflow
         * alike, and a guessed one starts work nobody asked for.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record TrackerConfig(String mode, String workflow, String assignee, String startStatus,
                                    String doneStatus, Integer everyMinutes, List<String> projects) {

            public TrackerConfig(String mode, String workflow, String assignee, String startStatus,
                                 String doneStatus, Integer everyMinutes) {
                this(mode, workflow, assignee, startStatus, doneStatus, everyMinutes, null);
            }

            public static TrackerConfig defaults() {
                return new TrackerConfig(null, null, null, null, null, null);
            }

            /** The tracker projects work is taken from; empty takes from every one. */
            public List<String> projectsOrAll() {
                return projects == null ? List.of() : projects.stream().filter(p -> p != null && !p.isBlank())
                        .map(String::strip).toList();
            }

            public TrackerMode modeOrOff() {
                return TrackerMode.of(mode).orElse(TrackerMode.OFF);
            }

            public int everyMinutesOrDefault() {
                return everyMinutes == null || everyMinutes <= 0 ? 10 : everyMinutes;
            }

            /**
             * The keys this mode needs and nobody filled in, so a startup check names them all at once rather
             * than one per restart. A stage only one end reads is asked for only where that end is on.
             */
            public List<String> missing() {
                TrackerMode running = modeOrOff();
                List<String> blank = new ArrayList<>();
                if (blank(workflow)) {
                    blank.add("workflow");
                }
                if (running.takes() && blank(assignee)) {
                    blank.add("assignee");
                }
                if (running.takes() && blank(startStatus)) {
                    blank.add("startStatus");
                }
                if (running.closes() && blank(doneStatus)) {
                    blank.add("doneStatus");
                }
                return List.copyOf(blank);
            }

            private static boolean blank(String value) {
                return value == null || value.isBlank();
            }
        }

        public static ConfigFile defaults() {
            return new ConfigFile(Map.of(), null, null, null, null, null, null, null);
        }

        // The raw field is still what the withers copy, so an omitted section stays null until set.
        @Override
        public MasterConfig master() {
            return master == null ? MasterConfig.defaults() : master;
        }

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

        @Override
        public TrackerConfig tracker() {
            return tracker == null ? TrackerConfig.defaults() : tracker;
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
            Object tree = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(new DumperOptions()),
                    new DumperOptions(), new WordsStayWords()).load(Files.readString(file));
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

    /** YAML 1.1 reads `off`, `on`, `yes` and `no` as booleans, and `mode: off` would arrive as "false". */
    private static final class WordsStayWords extends Resolver {

        private static final Pattern WORD = Pattern.compile("(?i)on|off|yes|no|y|n");

        @Override
        public Tag resolve(NodeId kind, String value, boolean implicit) {
            return kind == NodeId.scalar && implicit && WORD.matcher(value).matches()
                    ? Tag.STR : super.resolve(kind, value, implicit);
        }
    }
}
