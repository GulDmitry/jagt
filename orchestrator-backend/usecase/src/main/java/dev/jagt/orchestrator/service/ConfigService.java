package dev.jagt.orchestrator.service;

import lombok.With;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.protocol.SwitchJudgements;
import dev.jagt.orchestrator.task.LoopBreaker;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.Turn;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TrackerMode;
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
import org.springframework.boot.convert.DurationStyle;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads {@code jagt.yml} on every access, so edits are picked up without restarting — which is why this parses the
 * file itself instead of taking Spring's binding of it, that one happening once at startup. A whole section may be
 * omitted: {@link ConfigFile}'s accessors coalesce a missing one to its all-default instance.
 */
@Service
public class ConfigService implements LoopBreaker.Current {

    public ConfigService(OrchestratorPaths paths, AgentRuntime agentRuntime, ActStops actStops) {
        this.paths = paths;
        this.agentRuntime = agentRuntime;
        this.actStops = actStops;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record ConfigFile(Map<String, ProjectConfig> projects, @Nullable ViewerConfig viewer,
                             @Nullable CodeReviewConfig codeReview, @Nullable AgentConfig agent,
                             @Nullable WorktreeConfig worktree, @Nullable AutoReviewConfig autoReview,
                             @Nullable MasterConfig master, @Nullable TrackerConfig tracker,
                             @Nullable Integer maxStartsPerDay, @Nullable LoopBreakerConfig loopBreaker,
                             @Nullable Integer conflictStallMinutes, @Nullable ArchiveConfig archive) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record ArchiveConfig(@Nullable Integer maxMegabytes) {

            public static ArchiveConfig defaults() {
                return new ArchiveConfig(null);
            }

            public long maxBytesOrDefault() {
                return (maxMegabytes == null || maxMegabytes <= 0 ? 20L : maxMegabytes) * 1024 * 1024;
            }
        }

        /** Each round ceiling read as at most one below what a task keeps of its rounds. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record LoopBreakerConfig(@Nullable Integer stalledRounds, @Nullable Integer ships,
                                        @Nullable Integer rounds) {

            public static LoopBreakerConfig defaults() {
                return new LoopBreakerConfig(null, null, null);
            }

            public int roundsOrDefault() {
                return rounds == null || rounds <= 0 ? 3 : Math.min(rounds, Turn.MAX_RUN - 1);
            }

            public int stalledRoundsOrDefault() {
                return stalledRounds == null || stalledRounds <= 0 ? 3
                        : Math.min(stalledRounds, RoundTrail.MAX_ROUNDS - 1);
            }

            public int shipsOrDefault() {
                return ships == null || ships <= 0 ? 4 : ships;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record ViewerConfig(@Nullable String tmuxSession, @Nullable String viewMode,
                                   @Nullable Boolean keepViewer) {

            public static ViewerConfig defaults() {
                return new ViewerConfig(null, null, null);
            }

            /** The session the viewer attaches to, as written; the session host names a blank one. */
            public @Nullable String session() {
                return tmuxSession;
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
        public record CodeReviewConfig(@Nullable String mrTitlePattern, @Nullable Boolean postReviewReplies,
                                       @Nullable List<String> reviewReplyAuthors) {

            public static CodeReviewConfig defaults() {
                return new CodeReviewConfig(null, null, null);
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
        public record AgentConfig(@Nullable String outputStyle, @Nullable Integer probeSeconds,
                                  @Nullable SandboxConfig sandbox, @Nullable AutoplanConfig autoplan,
                                  @Nullable VisualizeConfig visualize, @Nullable DiagnoseConfig diagnose,
                                  @Nullable String model, @Nullable String cacheLifetime,
                                  @Nullable String notesEvery) {

            public static AgentConfig defaults() {
                return new AgentConfig(null, null, null, null, null, null, null, null, null);
            }

            /** Idle for this long, a session's prompt cache is gone; zero means a session is never judged cold. */
            public Duration cacheLifetimeOrDefault() {
                return duration(cacheLifetime, Duration.ofHours(1));
            }

            /** How stale task_notes.md may be at a turn end before the session is asked once to rewrite it; zero: never. */
            public Duration notesEveryOrDefault() {
                return duration(notesEvery, Duration.ofMinutes(30));
            }

            private static Duration duration(@Nullable String written, Duration unset) {
                if (written == null || written.isBlank()) {
                    return unset;
                }
                try {
                    return DurationStyle.detectAndParse(written.strip(), ChronoUnit.SECONDS);
                } catch (IllegalArgumentException unreadable) {
                    return unset;
                }
            }

            /** Blank: the agent CLI's own default. */
            public String modelOrInherited() {
                return model == null ? "" : model.strip();
            }

            public AutoplanConfig autoplanOrDefault() {
                return autoplan == null ? AutoplanConfig.defaults() : autoplan;
            }

            public VisualizeConfig visualizeOrDefault() {
                return visualize == null ? VisualizeConfig.defaults() : visualize;
            }

            public DiagnoseConfig diagnoseOrDefault() {
                return diagnose == null ? DiagnoseConfig.defaults() : diagnose;
            }

            /** The switches a session judges that are on, in {@link SwitchJudgements#NAMES} order. */
            public List<String> switchesOn() {
                Map<String, Boolean> on = Map.of("autoplan", autoplanOrDefault().enabledOrDefault(),
                        "visualize", visualizeOrDefault().enabledOrDefault(),
                        "diagnose", diagnoseOrDefault().enabledOrDefault());
                return SwitchJudgements.NAMES.stream().filter(on::get).toList();
            }

            public SandboxConfig sandboxOrDefault() {
                return sandbox == null ? SandboxConfig.defaults() : sandbox;
            }

            /** Null: nothing is written and the agent resolves its own style. */
            public @Nullable String outputStyleOrNull() {
                return outputStyle == null || outputStyle.isBlank() ? null : outputStyle.strip();
            }

            /** How often every running session is looked at; the cadence for one whose harness reports NOTHING. */
            public int probeSecondsOrDefault() {
                return probeSeconds == null || probeSeconds <= 0 ? 600 : probeSeconds;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record AutoplanConfig(@Nullable Boolean enabled, @Nullable Integer files) {

            public static AutoplanConfig defaults() {
                return new AutoplanConfig(null, null);
            }

            public boolean enabledOrDefault() {
                return enabled != null && enabled;
            }

            public int filesOrDefault() {
                return files == null || files <= 0 ? 30 : files;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record VisualizeConfig(@Nullable Boolean enabled) {

            public static VisualizeConfig defaults() {
                return new VisualizeConfig(null);
            }

            public boolean enabledOrDefault() {
                return enabled != null && enabled;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record DiagnoseConfig(@Nullable Boolean enabled) {

            public static DiagnoseConfig defaults() {
                return new DiagnoseConfig(null);
            }

            public boolean enabledOrDefault() {
                return enabled != null && enabled;
            }
        }

        /** Both lists add to what jagt always allows; neither replaces it. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record SandboxConfig(@Nullable Boolean enabled, @Nullable List<String> allowWrite,
                                    @Nullable List<String> excludedCommands) {

            public static SandboxConfig defaults() {
                return new SandboxConfig(null, null, null);
            }

            public boolean enabledOrDefault() {
                return enabled != null && enabled;
            }

            public List<String> allowWriteOrEmpty() {
                return allowWrite == null ? List.of() : allowWrite;
            }

            public List<String> excludedCommandsOrEmpty() {
                return excludedCommands == null ? List.of() : excludedCommands;
            }
        }

        /**
         * The Master session: judging unless a human turned it off or up to act. {@code brief} is the file
         * saying what it judges — the thing that outlives every session it runs.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record MasterConfig(@Nullable String mode, @Nullable String brief, @Nullable String model,
                                   @Nullable List<String> mine, @Nullable List<String> withhold) {

            /** Copied from `master-brief.md.dist`, beside `jagt.yml`, and not versioned. */
            private static final String BRIEF = "master-brief.md";

            public static MasterConfig defaults() {
                return new MasterConfig(null, null, null, null, null);
            }

            /** Steps a human keeps for themselves, by name; anything not named here the Master holds in `act`. */
            public List<String> mineOrDefault() {
                return mine == null ? List.of(MasterRight.PLAN.id()) : mine;
            }

            /** The name this list had before it was written from the human's side; refused rather than ignored. */
            public boolean usesTheRetiredName() {
                return withhold != null;
            }

            /** Whether the session may do this, which in any mode but `act` is never. */
            public boolean may(MasterRight right) {
                return profile().may(right);
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
            public MasterMode modeOrDefault() {
                return MasterMode.of(mode).orElse(MasterMode.OFF);
            }

            public boolean running() {
                return modeOrDefault() != MasterMode.OFF;
            }

            public Turns.Profile profile() {
                return new Turns.Profile(modeOrDefault(), mineOrDefault().stream().map(MasterRight::of)
                        .flatMap(Optional::stream).collect(java.util.stream.Collectors.toSet()));
            }
        }

        /** The numbers only; {@code AutoReviewCadence} is the policy that reads them. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        @With
        public record AutoReviewConfig(@Nullable Boolean enabled, @Nullable Integer windowHours,
                                       @Nullable Integer minIntervalMinutes,
                                       @Nullable Integer maxIntervalMinutes) {

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
        public record WorktreeConfig(@Nullable List<String> copyGlobs) {

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
        public record TrackerConfig(@Nullable String mode, @Nullable String workflow, @Nullable String assignee,
                                    @Nullable String startStatus, @Nullable String doneStatus,
                                    @Nullable Integer everyMinutes, @Nullable List<String> projects) {

            public TrackerConfig(@Nullable String mode, @Nullable String workflow, @Nullable String assignee,
                                 @Nullable String startStatus, @Nullable String doneStatus,
                                 @Nullable Integer everyMinutes) {
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

            private static boolean blank(@Nullable String value) {
                return value == null || value.isBlank();
            }
        }

        public static ConfigFile defaults() {
            return new ConfigFile(Map.of(), null, null, null, null, null, null, null, null, null, null, null);
        }

        /** Unattended starts one local day may hold before the rest wait for tomorrow or for you. */
        public int maxStartsPerDayOrDefault() {
            return maxStartsPerDay == null || maxStartsPerDay <= 0 ? 50 : maxStartsPerDay;
        }

        public Duration conflictStallOrDefault() {
            return Duration.ofMinutes(conflictStallMinutes == null || conflictStallMinutes <= 0 ? 20 : conflictStallMinutes);
        }

        /** The ship ceiling counts over the auto-review window. */
        public LoopBreaker loopBreakerOrDefault() {
            LoopBreakerConfig config = loopBreaker == null ? LoopBreakerConfig.defaults() : loopBreaker;
            return new LoopBreaker(config.stalledRoundsOrDefault(), config.shipsOrDefault(), config.roundsOrDefault(),
                    Duration.ofHours(autoReview().windowHoursOrDefault()));
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

        @Override
        public ArchiveConfig archive() {
            return archive == null ? ArchiveConfig.defaults() : archive;
        }

    }

    /** The one root everything a human writes lives under, Spring's keys and jagt's own alike. */
    private static final String ROOT = "orchestrator";

    private final JsonMapper mapper = new JsonMapper();
    private final OrchestratorPaths paths;
    private final AgentRuntime agentRuntime;
    private final ActStops actStops;
    private volatile @Nullable Parsed parsed;

    private record Parsed(FileVersion version, @Nullable Object section) {
    }

    public ConfigFile load() {
        Object section = section();
        if (section == null) {
            return masterByDefault(ConfigFile.defaults());
        }
        ConfigFile config = mapper.convertValue(section, ConfigFile.class);
        return masterByDefault(config.projects() == null ? config.withProjects(Map.of()) : config);
    }

    /** Unset, the Master judges where the runtime reads the human's words, else is off; a stopped act judges. */
    private ConfigFile masterByDefault(ConfigFile config) {
        ConfigFile.MasterConfig master = config.master();
        if (master.modeOrDefault() == MasterMode.ACT && actStops.standing().isPresent()) {
            return config.withMaster(master.withMode(MasterMode.JUDGE.id()));
        }
        return master.mode() != null && !master.mode().isBlank() ? config : config.withMaster(master.withMode(
                (agentRuntime.readsWhatTheHumanTyped() ? MasterMode.JUDGE : MasterMode.OFF).id()));
    }

    @Override
    public LoopBreaker current() {
        return load().loopBreakerOrDefault();
    }

    public Optional<ActStop> actStop() {
        return actStops.standing();
    }

    public boolean startsStopped() {
        return actStops.startsStopped();
    }

    /** One unattended start taken; reaching the day's ceiling writes the stop. */
    public void startTaken() {
        actStops.take(load().maxStartsPerDayOrDefault());
    }

    /** The names the human wrote under {@code orchestrator}, INCLUDING ones nothing binds — both readers drop those. */
    public Set<String> declaredKeys() {
        Object section = section();
        return section instanceof Map<?, ?> keys
                ? keys.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet())
                : Set.of();
    }

    private @Nullable Object section() {
        Path file = paths.configFile();
        if (!Files.exists(file)) {
            throw new IllegalStateException("Missing " + file
                    + " — copy jagt.yml.dist to jagt.yml and fill in your projects.");
        }
        FileVersion version = FileVersion.of(file);
        Parsed last = parsed;
        if (last != null && last.version().equals(version)) {
            return last.section();
        }
        try {
            // SafeConstructor: the file is hand-edited, and a YAML tag naming a class is not a setting.
            Object tree = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(new DumperOptions()),
                    new DumperOptions(), new WordsStayWords()).load(Files.readString(file));
            Object section = tree instanceof Map<?, ?> document ? document.get(ROOT) : null;
            parsed = version == null ? null : new Parsed(version, section);
            return section;
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
