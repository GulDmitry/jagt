package dev.jagt.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.protocol.SwitchJudgements;
import dev.jagt.orchestrator.task.LoopBreaker;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TrackerMode;
import dev.jagt.orchestrator.task.Turn;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code jagt.yml} on every access, so edits are picked up without restarting — which is why
 * this parses the file itself instead of taking Spring's binding of it, that one happening once at
 * startup. A whole section may be omitted: {@link ConfigFile}'s accessors coalesce a missing one to
 * its all-default instance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigService implements LoopBreaker.Current, SessionReach {

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @With
  public record ConfigFile(
      Map<String, ProjectConfig> projects,
      @Nullable ViewerConfig viewer,
      @Nullable ArchiveConfig archive,
      @JsonUnwrapped Sessions sessions,
      @JsonUnwrapped Reviewing reviewing,
      @JsonUnwrapped Outside outside) {

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sessions(
        @Nullable AgentConfig agent,
        @Nullable WorktreeConfig worktree,
        @Nullable Map<String, RoleConfig> roles,
        @Nullable LoopBreakerConfig loopBreaker,
        @Nullable Integer conflictStallMinutes) {}

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reviewing(
        @Nullable CodeReviewConfig codeReview,
        @Nullable AutoReviewConfig autoReview,
        @Nullable MasterConfig master,
        @Nullable QaConfig qa) {}

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Outside(
        @Nullable TrackerConfig tracker,
        @Nullable String codeHost,
        @Nullable NotificationsConfig notifications) {}

    /**
     * Which events raise a desktop banner, and which a team channel's command receives; unset keeps
     * each event's own default for the desktop, an unknown id is ignored.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record NotificationsConfig(
        @Nullable List<String> desktop, @Nullable ChannelConfig channel) {

      public static NotificationsConfig defaults() {
        return new NotificationsConfig(null, null);
      }

      public Set<Notification.Event> desktopEvents() {
        if (desktop == null) {
          return Arrays.stream(Notification.Event.values())
              .filter(Notification.Event::onByDefault)
              .collect(Collectors.toUnmodifiableSet());
        }
        return eventsOf(desktop);
      }

      @Override
      public ChannelConfig channel() {
        return channel == null ? ChannelConfig.defaults() : channel;
      }

      private static Set<Notification.Event> eventsOf(List<String> ids) {
        return ids.stream()
            .map(Notification.Event::ofId)
            .flatMap(Optional::stream)
            .collect(Collectors.toUnmodifiableSet());
      }

      /** An empty command is off; unset events are a request opening and a deploy landing. */
      @JsonIgnoreProperties(ignoreUnknown = true)
      @With
      public record ChannelConfig(@Nullable List<String> command, @Nullable List<String> events) {

        public static ChannelConfig defaults() {
          return new ChannelConfig(null, null);
        }

        public List<String> commandOrEmpty() {
          boolean blank =
              command == null
                  || command.isEmpty()
                  || command.get(0) == null
                  || command.get(0).isBlank();
          return blank ? List.of() : List.copyOf(command);
        }

        public Set<Notification.Event> eventSet() {
          return events == null
              ? Set.of(Notification.Event.REQUEST_OPENED, Notification.Event.DEPLOYED)
              : eventsOf(events);
        }
      }
    }

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

    /** Each ceiling read as at most one below what a task keeps of its rounds, turns or moves. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record LoopBreakerConfig(
        @Nullable Integer stalledRounds,
        @Nullable Integer ships,
        @Nullable Integer rounds,
        @Nullable Integer perHour) {

      public static LoopBreakerConfig defaults() {
        return new LoopBreakerConfig(null, null, null, null);
      }

      public int perHourOrDefault() {
        return perHour == null || perHour <= 0 ? 20 : Math.min(perHour, TaskState.MAX_HISTORY - 1);
      }

      public int roundsOrDefault() {
        return rounds == null || rounds <= 0 ? 3 : Math.min(rounds, Turn.MAX_RUN - 1);
      }

      public int stalledRoundsOrDefault() {
        return stalledRounds == null || stalledRounds <= 0
            ? 3
            : Math.min(stalledRounds, RoundTrail.MAX_ROUNDS - 1);
      }

      public int shipsOrDefault() {
        return ships == null || ships <= 0 ? 4 : ships;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record ViewerConfig(
        @Nullable String tmuxSession, @Nullable String viewMode, @Nullable Boolean keepViewer) {

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
    public record CodeReviewConfig(
        @Nullable String mrTitlePattern,
        @Nullable Boolean postReviewReplies,
        @Nullable List<String> reviewReplyAuthors) {

      public static CodeReviewConfig defaults() {
        return new CodeReviewConfig(null, null, null);
      }

      public String mrTitlePatternOrDefault() {
        return mrTitlePattern == null || mrTitlePattern.isBlank()
            ? "{ticket} {title}"
            : mrTitlePattern;
      }

      /**
       * Whether a ship posts EVERY drafted reply. False under an author filter, which leaves the
       * rest.
       */
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @With
    public record AgentConfig(
        @JsonUnwrapped Models selection,
        @JsonUnwrapped Cadence cadence,
        @Nullable SandboxConfig sandbox,
        @Nullable AutoplanConfig autoplan,
        @Nullable VisualizeConfig visualize,
        @Nullable DiagnoseConfig diagnose) {

      @With
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public record Models(
          @Nullable String model, @Nullable List<String> models, @Nullable List<String> tiers) {}

      @With
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public record Cadence(
          @Nullable Integer probeSeconds,
          @Nullable String cacheLifetime,
          @Nullable String notesEvery) {}

      public static AgentConfig defaults() {
        return new AgentConfig(
            new Models(null, null, null), new Cadence(null, null, null), null, null, null, null);
      }

      public AgentConfig withModel(@Nullable String model) {
        return withSelection(selection.withModel(model));
      }

      public AgentConfig withModels(@Nullable List<String> names) {
        return withSelection(selection.withModels(names));
      }

      public AgentConfig withProbeSeconds(@Nullable Integer seconds) {
        return withCadence(cadence.withProbeSeconds(seconds));
      }

      public AgentConfig withCacheLifetime(@Nullable String lifetime) {
        return withCadence(cadence.withCacheLifetime(lifetime));
      }

      public AgentConfig withNotesEvery(@Nullable String every) {
        return withCadence(cadence.withNotesEvery(every));
      }

      /**
       * Idle for this long, a session's prompt cache is gone; zero means a session is never judged
       * cold.
       */
      public Duration cacheLifetimeOrDefault() {
        return duration(cadence.cacheLifetime(), Duration.ofHours(1));
      }

      /**
       * How stale task_notes.md may be at a turn end before the session is asked once to rewrite
       * it; zero: never.
       */
      public Duration notesEveryOrDefault() {
        return duration(cadence.notesEvery(), Duration.ofMinutes(30));
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

      /** Written names replace the agent CLI's own list whole; none written keeps it. */
      public List<String> modelsFor(AgentRuntime runtime) {
        List<String> written = selection.models();
        return written == null || written.isEmpty() ? runtime.models() : written;
      }

      /**
       * The models a stopped session may be moved up through, lowest first, the agent's own order
       * unless written; the first stands for the one it starts on. Only names it accepts count.
       */
      public List<String> tiersFor(AgentRuntime runtime) {
        List<String> accepted = modelsFor(runtime);
        return (selection.tiers() == null ? runtime.tiers() : selection.tiers())
            .stream().map(String::strip).filter(accepted::contains).toList();
      }

      /** Blank: the agent CLI's own default. */
      public String modelOrInherited() {
        return selection.model() == null ? "" : selection.model().strip();
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
        Map<String, Boolean> on =
            Map.of(
                "autoplan",
                autoplanOrDefault().enabledOrDefault(),
                "visualize",
                visualizeOrDefault().enabledOrDefault(),
                "diagnose",
                diagnoseOrDefault().enabledOrDefault());
        return SwitchJudgements.NAMES.stream().filter(on::get).toList();
      }

      public SandboxConfig sandboxOrDefault() {
        return sandbox == null ? SandboxConfig.defaults() : sandbox;
      }

      /**
       * How often every running session is looked at; the cadence for one whose harness reports
       * NOTHING.
       */
      public int probeSecondsOrDefault() {
        Integer written = cadence.probeSeconds();
        return written == null || written <= 0 ? 600 : written;
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
    public record SandboxConfig(
        @Nullable Boolean enabled,
        @Nullable List<String> allowWrite,
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

      public List<Path> allowWritePaths(Path home) {
        return allowWriteOrEmpty().stream()
            .map(String::strip)
            .map(
                entry ->
                    entry.equals("~")
                        ? home
                        : entry.startsWith("~/")
                            ? home.resolve(entry.substring(2))
                            : Path.of(entry))
            .toList();
      }

      public List<String> excludedCommandsOrEmpty() {
        return excludedCommands == null ? List.of() : excludedCommands;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record QaConfig(
        @Nullable Boolean enabled,
        @Nullable String model,
        @Nullable List<QaEnvironment> environments) {

      public static QaConfig defaults() {
        return new QaConfig(null, null, null);
      }

      public boolean enabledOrDefault() {
        return enabled != null && enabled;
      }

      public List<QaEnvironment> environmentsOrEmpty() {
        return environments == null ? List.of() : environments;
      }
    }

    /**
     * What {@code orchestrator.roles.<id>} says of one checker role; the old {@code
     * orchestrator.qa} reads as {@code roles.qa}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record RoleConfig(
        @Nullable Boolean enabled,
        @Nullable String model,
        @Nullable List<QaEnvironment> environments) {

      public static RoleConfig defaults() {
        return new RoleConfig(null, null, null);
      }

      static RoleConfig of(QaConfig legacy) {
        return new RoleConfig(legacy.enabled(), legacy.model(), legacy.environments());
      }

      public boolean enabledOrDefault() {
        return enabled != null && enabled;
      }

      public String modelOrInherited() {
        return model == null ? "" : model.strip();
      }

      public List<QaEnvironment> environmentsOrEmpty() {
        return environments == null ? List.of() : environments;
      }

      public boolean allows(String baseUrl) {
        return environmentsOrEmpty().stream()
            .anyMatch(environment -> baseUrl.equals(environment.url()));
      }
    }

    /**
     * {@code landed} is a URL whose answer proves the change is up where the host reports no deploy
     * job.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record QaEnvironment(@Nullable String url, @Nullable String landed) {}

    /**
     * The Master session: judging unless a human turned it off or up to act. {@code brief} is the
     * file saying what it judges — the thing that outlives every session it runs.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record MasterConfig(
        @Nullable String mode,
        @Nullable String brief,
        @Nullable String model,
        @Nullable List<String> mine,
        @Nullable List<String> withhold) {

      /** Copied from `roles/master/brief.md.dist`, and not versioned. */
      private static final String BRIEF = "roles/master/brief.md";

      public static MasterConfig defaults() {
        return new MasterConfig(null, null, null, null, null);
      }

      /**
       * Steps and verbs a human keeps for themselves, by name; anything not named here the Master
       * holds in `act`.
       */
      public List<String> mineOrDefault() {
        return mine == null ? List.of(MasterRight.PLAN.id()) : mine;
      }

      /**
       * The name this list had before it was written from the human's side; refused rather than
       * ignored.
       */
      public boolean usesTheRetiredName() {
        return withhold != null;
      }

      /** Whether the session may do this, which in any mode but `act` is never. */
      public boolean may(MasterRight right) {
        return profile().may(right);
      }

      /**
       * The file it judges by. Named here so an install that copied the shipped one sets nothing.
       */
      public String briefOrDefault() {
        return brief == null || brief.isBlank() ? BRIEF : brief;
      }

      /**
       * Blank inherits whatever the agent CLI would have used; reading is the half worth paying
       * for.
       */
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

      public Arbiter.Profile profile() {
        return new Arbiter.Profile(
            modeOrDefault(),
            mineOrDefault().stream()
                .map(word -> word.strip().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet()));
      }
    }

    /** The numbers only; {@code AutoReviewCadence} is the policy that reads them. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record AutoReviewConfig(
        @Nullable Boolean enabled,
        @Nullable Integer windowHours,
        @Nullable Integer minIntervalMinutes,
        @Nullable Integer maxIntervalMinutes,
        @Nullable Integer waitingMaxIntervalMinutes) {

      public static AutoReviewConfig defaults() {
        return new AutoReviewConfig(null, null, null, null, null);
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

      public int waitingMaxIntervalMinutesOrDefault() {
        int max = maxIntervalMinutesOrDefault();
        return waitingMaxIntervalMinutes == null || waitingMaxIntervalMinutes < max
            ? Math.max(240, max)
            : waitingMaxIntervalMinutes;
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
     * The two ends the tracker drives: work arriving unasked, and a task closing on the stage that
     * says it landed. {@code workflow} names the vocabulary those stages are read in and is bound
     * at STARTUP, unlike everything beside it. Nothing carries a default stage name: no two
     * installs spell their workflow alike, and a guessed one starts work nobody asked for.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @With
    public record TrackerConfig(
        @Nullable String mode,
        @Nullable String workflow,
        @Nullable String assignee,
        @Nullable String startStatus,
        @Nullable String doneStatus,
        @Nullable Integer everyMinutes,
        @Nullable List<String> projects,
        @Nullable String file) {

      private static final String FILE_WORKFLOW = "file";

      public TrackerConfig(
          @Nullable String mode,
          @Nullable String workflow,
          @Nullable String assignee,
          @Nullable String startStatus,
          @Nullable String doneStatus,
          @Nullable Integer everyMinutes) {
        this(mode, workflow, assignee, startStatus, doneStatus, everyMinutes, null, null);
      }

      public static TrackerConfig defaults() {
        return new TrackerConfig(null, null, null, null, null, null);
      }

      /** The tracker projects work is taken from; empty takes from every one. */
      public List<String> projectsOrAll() {
        return projects == null
            ? List.of()
            : projects.stream().filter(p -> p != null && !p.isBlank()).map(String::strip).toList();
      }

      public TrackerMode modeOrOff() {
        return TrackerMode.of(mode).orElse(TrackerMode.OFF);
      }

      public int everyMinutesOrDefault() {
        return everyMinutes == null || everyMinutes <= 0 ? 10 : everyMinutes;
      }

      /**
       * The keys this mode needs and nobody filled in, so a startup check names them all at once
       * rather than one per restart. A stage only one end reads is asked for only where that end is
       * on.
       */
      public List<String> missing() {
        TrackerMode running = modeOrOff();
        List<String> blank = new ArrayList<>();
        if (blank(workflow)) {
          blank.add("workflow");
        }
        boolean fileTracker = FILE_WORKFLOW.equals(workflow);
        if (running.takes() && !fileTracker && blank(assignee)) {
          blank.add("assignee");
        }
        if (running.takes() && blank(startStatus)) {
          blank.add("startStatus");
        }
        if (running.closes() && blank(doneStatus)) {
          blank.add("doneStatus");
        }
        if (fileTracker && running != TrackerMode.OFF && blank(file)) {
          blank.add("file");
        }
        return List.copyOf(blank);
      }

      private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
      }
    }

    /**
     * Whether the project has no code host: it has no remote, or its {@code codeHost} says none.
     */
    public boolean hasNoCodeHost(String project) {
      ProjectConfig declared = projects.get(project);
      return (declared != null && declared.gitRemote().local())
          || codeHostNamed(project)
              .filter(ProjectConfig.NO_CODE_HOST::equalsIgnoreCase)
              .isPresent();
    }

    /**
     * The kind the project's own {@code codeHost} names, else the orchestrator's; empty where
     * neither does.
     */
    public Optional<String> codeHostNamed(String project) {
      ProjectConfig declared = projects.get(project);
      String own = declared == null ? null : declared.codeHost();
      String kind = own == null || own.isBlank() ? outside.codeHost() : own;
      return kind == null || kind.isBlank() ? Optional.empty() : Optional.of(kind.strip());
    }

    public static ConfigFile defaults() {
      return new ConfigFile(
          Map.of(),
          null,
          null,
          new Sessions(null, null, null, null, null),
          new Reviewing(null, null, null, null),
          new Outside(null, null, null));
    }

    public ConfigFile withAgent(@Nullable AgentConfig agent) {
      return withSessions(sessions.withAgent(agent));
    }

    public ConfigFile withWorktree(@Nullable WorktreeConfig worktree) {
      return withSessions(sessions.withWorktree(worktree));
    }

    public ConfigFile withRoles(@Nullable Map<String, RoleConfig> roles) {
      return withSessions(sessions.withRoles(roles));
    }

    public ConfigFile withConflictStallMinutes(@Nullable Integer minutes) {
      return withSessions(sessions.withConflictStallMinutes(minutes));
    }

    public ConfigFile withCodeReview(@Nullable CodeReviewConfig codeReview) {
      return withReviewing(reviewing.withCodeReview(codeReview));
    }

    public ConfigFile withAutoReview(@Nullable AutoReviewConfig autoReview) {
      return withReviewing(reviewing.withAutoReview(autoReview));
    }

    public ConfigFile withMaster(@Nullable MasterConfig master) {
      return withReviewing(reviewing.withMaster(master));
    }

    public ConfigFile withQa(@Nullable QaConfig qa) {
      return withReviewing(reviewing.withQa(qa));
    }

    public ConfigFile withTracker(@Nullable TrackerConfig tracker) {
      return withOutside(outside.withTracker(tracker));
    }

    public ConfigFile withCodeHost(@Nullable String codeHost) {
      return withOutside(outside.withCodeHost(codeHost));
    }

    public ConfigFile withNotifications(@Nullable NotificationsConfig notifications) {
      return withOutside(outside.withNotifications(notifications));
    }

    public @Nullable String codeHost() {
      return outside.codeHost();
    }

    public NotificationsConfig notifications() {
      return outside.notifications() == null
          ? NotificationsConfig.defaults()
          : outside.notifications();
    }

    public Duration conflictStallOrDefault() {
      Integer minutes = sessions.conflictStallMinutes();
      return Duration.ofMinutes(minutes == null || minutes <= 0 ? 20 : minutes);
    }

    /** The ship ceiling counts over the auto-review window. */
    public LoopBreaker loopBreakerOrDefault() {
      LoopBreakerConfig config =
          sessions.loopBreaker() == null ? LoopBreakerConfig.defaults() : sessions.loopBreaker();
      return new LoopBreaker(
          config.stalledRoundsOrDefault(),
          config.shipsOrDefault(),
          config.roundsOrDefault(),
          config.perHourOrDefault(),
          Duration.ofHours(autoReview().windowHoursOrDefault()));
    }

    public MasterConfig master() {
      return reviewing.master() == null ? MasterConfig.defaults() : reviewing.master();
    }

    @Override
    public ViewerConfig viewer() {
      return viewer == null ? ViewerConfig.defaults() : viewer;
    }

    public CodeReviewConfig codeReview() {
      return reviewing.codeReview() == null ? CodeReviewConfig.defaults() : reviewing.codeReview();
    }

    public AgentConfig agent() {
      return sessions.agent() == null ? AgentConfig.defaults() : sessions.agent();
    }

    public WorktreeConfig worktree() {
      return sessions.worktree() == null ? WorktreeConfig.defaults() : sessions.worktree();
    }

    public AutoReviewConfig autoReview() {
      return reviewing.autoReview() == null ? AutoReviewConfig.defaults() : reviewing.autoReview();
    }

    public TrackerConfig tracker() {
      return outside.tracker() == null ? TrackerConfig.defaults() : outside.tracker();
    }

    public QaConfig qa() {
      return reviewing.qa() == null ? QaConfig.defaults() : reviewing.qa();
    }

    public boolean anyCheckerEnabled() {
      return Checkers.withJobs().stream().anyMatch(id -> role(id).enabledOrDefault());
    }

    public List<String> unknownRoles(Collection<String> installed) {
      Map<String, RoleConfig> roles = sessions.roles();
      return roles == null
          ? List.of()
          : roles.keySet().stream().filter(id -> !installed.contains(id)).sorted().toList();
    }

    /**
     * The role as {@code roles.<id>} writes it; where it names none, QA falls back to the old
     * {@code qa} key.
     */
    public RoleConfig role(String id) {
      Map<String, RoleConfig> roles = sessions.roles();
      RoleConfig written = roles == null ? null : roles.get(id);
      if (written != null) {
        return written;
      }
      QaConfig legacy = reviewing.qa();
      return Checkers.QA.equals(id) && legacy != null
          ? RoleConfig.of(legacy)
          : RoleConfig.defaults();
    }

    @Override
    public ArchiveConfig archive() {
      return archive == null ? ArchiveConfig.defaults() : archive;
    }
  }

  /** The one root everything a human writes lives under, Spring's keys and jagt's own alike. */
  private static final String ROOT = "orchestrator";

  private static final String LEGACY_QA = "qa";

  private static final String MASTER = Arbiter.ID;

  private static final List<String> LEGACY_ROLE_KEYS = List.of(MASTER, LEGACY_QA);

  private final JsonMapper mapper = new JsonMapper();
  private final OrchestratorPaths paths;
  private final AgentRuntime agentRuntime;
  private final ActStops actStops;
  private volatile @Nullable Parsed parsed;

  private record Parsed(FileVersion version, @Nullable Object section) {}

  public ConfigFile load() {
    Object section = section();
    if (section == null) {
      return masterByDefault(ConfigFile.defaults());
    }
    ConfigFile config = mapper.convertValue(layered(section), ConfigFile.class);
    return masterByDefault(config.projects() == null ? config.withProjects(Map.of()) : config);
  }

  /**
   * Per role, key by key, {@code roles.<id>} wins over the old top-level key, which fills what it
   * leaves unwritten.
   */
  private static Object layered(Object section) {
    if (!(section instanceof Map<?, ?> keys)) {
      return section;
    }
    Map<Object, Object> layered = new java.util.LinkedHashMap<>(keys);
    Map<Object, Object> roles =
        new java.util.LinkedHashMap<>(
            keys.get("roles") instanceof Map<?, ?> written ? written : Map.of());
    for (String id : LEGACY_ROLE_KEYS) {
      Map<Object, Object> merged = merge(keys.get(id), roles.get(id));
      if (!merged.isEmpty()) {
        layered.put(id, merged);
      }
      if (roles.remove(id) != null && !MASTER.equals(id)) {
        roles.put(id, merged);
      }
    }
    if (keys.containsKey("roles")) {
      layered.put("roles", roles);
    }
    return layered;
  }

  private static Map<Object, Object> merge(@Nullable Object... layers) {
    Map<Object, Object> merged = new java.util.LinkedHashMap<>();
    Stream.of(layers)
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .forEach(
            layer ->
                layer.forEach(
                    (key, value) -> {
                      if (value != null) {
                        merged.put(key, value);
                      }
                    }));
    return merged;
  }

  /**
   * Unset, the Master judges where the runtime reads the human's words, else is off; a stopped act
   * judges.
   */
  private ConfigFile masterByDefault(ConfigFile config) {
    ConfigFile.MasterConfig master = config.master();
    if (master.modeOrDefault() == MasterMode.ACT && actStops.standing().isPresent()) {
      return config.withMaster(master.withMode(MasterMode.JUDGE.id()));
    }
    return master.mode() != null && !master.mode().isBlank()
        ? config
        : config.withMaster(
            master.withMode(
                (agentRuntime.readsWhatTheHumanTyped() ? MasterMode.JUDGE : MasterMode.OFF).id()));
  }

  @Override
  public LoopBreaker current() {
    return load().loopBreakerOrDefault();
  }

  @Override
  public List<Path> beside() {
    return load()
        .agent()
        .sandboxOrDefault()
        .allowWritePaths(Path.of(System.getProperty("user.home")))
        .stream()
        .filter(Files::isDirectory)
        .map(WorktreeSandbox::real)
        .toList();
  }

  public Optional<ActStop> actStop() {
    return actStops.standing();
  }

  /**
   * The names the human wrote under {@code orchestrator}, INCLUDING ones nothing binds — both
   * readers drop those.
   */
  public Set<String> declaredKeys() {
    Object section = section();
    return section instanceof Map<?, ?> keys
        ? keys.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet())
        : Set.of();
  }

  private @Nullable Object section() {
    Path file = paths.configFile();
    if (!Files.exists(file)) {
      throw new IllegalStateException(
          "Missing " + file + " — copy jagt.yml.dist to jagt.yml and fill in your projects.");
    }
    FileVersion version = FileVersion.of(file);
    Parsed last = parsed;
    if (last != null && last.version().equals(version)) {
      return last.section();
    }
    try {
      // SafeConstructor: the file is hand-edited, and a YAML tag naming a class is not a setting.
      Object tree =
          new Yaml(
                  new SafeConstructor(new LoaderOptions()),
                  new Representer(new DumperOptions()),
                  new DumperOptions(),
                  new WordsStayWords())
              .load(Files.readString(file));
      Object section = tree instanceof Map<?, ?> document ? document.get(ROOT) : null;
      parsed = version == null ? null : new Parsed(version, section);
      if (section instanceof Map<?, ?> keys && keys.containsKey(LEGACY_QA)) {
        log.atInfo()
            .setMessage("config key deprecated")
            .addKeyValue("key", "orchestrator.qa")
            .addKeyValue("use", "orchestrator.roles.qa")
            .log();
      }
      if (section instanceof Map<?, ?> keys && keys.containsKey(MASTER)) {
        log.atInfo()
            .setMessage("config key deprecated")
            .addKeyValue("key", "orchestrator.master")
            .addKeyValue("use", "orchestrator.roles.master")
            .log();
      }
      return section;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    } catch (RuntimeException malformed) {
      throw new IllegalStateException(
          "Cannot read " + file + ": " + malformed.getMessage(), malformed);
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

  /**
   * YAML 1.1 reads `off`, `on`, `yes` and `no` as booleans, and `mode: off` would arrive as
   * "false".
   */
  private static final class WordsStayWords extends Resolver {

    private static final Pattern WORD = Pattern.compile("(?i)on|off|yes|no|y|n");

    @Override
    public Tag resolve(NodeId kind, String value, boolean implicit) {
      return kind == NodeId.scalar && implicit && WORD.matcher(value).matches()
          ? Tag.STR
          : super.resolve(kind, value, implicit);
    }
  }
}
