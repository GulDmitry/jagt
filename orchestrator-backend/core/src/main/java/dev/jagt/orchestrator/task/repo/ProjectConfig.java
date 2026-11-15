package dev.jagt.orchestrator.task.repo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(
    @JsonUnwrapped Repository repository,
    @JsonUnwrapped Branches branches,
    @JsonUnwrapped CodeHost host,
    @JsonUnwrapped Verify verify,
    @JsonUnwrapped Setup setup) {

  /**
   * {@code about}: one line saying what this repository IS, for placing an item no label places;
   * blank = its path. {@code url}: cloned into the data folder where {@code path} is blank or
   * missing. {@code localFiles}: the folder {@code worktree.copyGlobs} copies from; blank = its
   * path.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Repository(
      String path,
      List<String> labels,
      @Nullable String about,
      List<String> humanPaths,
      @Nullable String url,
      @Nullable String localFiles) {

    public Repository {
      labels = labels == null ? List.of() : labels;
      humanPaths = humanPaths == null ? List.of() : humanPaths;
    }
  }

  /**
   * {@code remote}: the remote branches are shared through; blank is origin, none a repository kept
   * on this machine.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Branches(
      String baseBranch, @Nullable String deployBranch, @Nullable String remote) {}

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record CodeHost(
      @JsonProperty("codeHost") @Nullable String kind,
      @JsonProperty("codeHostName") @Nullable String name,
      List<String> pipelineCommand,
      List<String> retryCommand,
      List<String> loginCommand,
      List<String> closeCommand,
      List<String> notesCommand) {

    public CodeHost {
      notesCommand = notesCommand == null ? List.of() : notesCommand;
      pipelineCommand = pipelineCommand == null ? List.of() : pipelineCommand;
      retryCommand = retryCommand == null ? List.of() : retryCommand;
      loginCommand = loginCommand == null ? List.of() : loginCommand;
      closeCommand = closeCommand == null ? List.of() : closeCommand;
    }
  }

  /**
   * {@code command}: run in the worktree before a hand-back reaches a human, empty = nothing;
   * {@code minutes}: null, zero or negative = 20.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Verify(
      @JsonProperty("verifyCommand") List<String> command,
      @JsonProperty("verifyMinutes") @Nullable Integer minutes) {

    public Verify {
      command = command == null ? List.of() : command;
    }
  }

  /**
   * {@code command}: run once in every new worktree of the project before its session starts, empty
   * = nothing; {@code minutes}: null, zero or negative = 20.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Setup(
      @JsonProperty("setupCommand") List<String> command,
      @JsonProperty("setupMinutes") @Nullable Integer minutes) {

    public static final Setup NONE = new Setup(List.of(), null);

    public Setup {
      command = command == null ? List.of() : command;
    }
  }

  /**
   * The {@code codeHost} value of a repository with no host to open a request on or read a pipeline
   * from.
   */
  public static final String NO_CODE_HOST = "none";

  /** A project declaring no command of its own: the four keys every install has always had. */
  public ProjectConfig(
      String path, String baseBranch, @Nullable String deployBranch, List<String> labels) {
    this(
        new Repository(path, labels, null, List.of(), null, null),
        new Branches(baseBranch, deployBranch, null),
        new CodeHost(null, null, List.of(), List.of(), List.of(), List.of(), List.of()),
        new Verify(List.of(), null),
        Setup.NONE);
  }

  public String path() {
    return repository.path();
  }

  public @Nullable String url() {
    return repository.url();
  }

  public boolean hasUrl() {
    return url() != null && !url().isBlank();
  }

  public String localFilesOrPath() {
    String folder = repository.localFiles();
    return folder == null || folder.isBlank() ? path() : folder;
  }

  public ProjectConfig withPath(String path) {
    Repository held = repository;
    return new ProjectConfig(
        new Repository(
            path, held.labels(), held.about(), held.humanPaths(), held.url(), held.localFiles()),
        branches,
        host,
        verify,
        setup);
  }

  public List<String> labels() {
    return repository.labels();
  }

  public @Nullable String about() {
    return repository.about();
  }

  public List<String> humanPaths() {
    return repository.humanPaths();
  }

  public String baseBranch() {
    return branches.baseBranch();
  }

  public @Nullable String deployBranch() {
    return branches.deployBranch();
  }

  public @Nullable String remote() {
    return branches.remote();
  }

  public @Nullable String codeHost() {
    return host.kind();
  }

  public List<String> verifyCommand() {
    return verify.command();
  }

  public @Nullable Integer verifyMinutes() {
    return verify.minutes();
  }

  /**
   * What a router is told this repository is; its path where nobody wrote a line, the name carrying
   * most.
   */
  public String aboutOrPath() {
    String about = about();
    return about == null || about.isBlank() ? String.valueOf(path()) : about;
  }

  public Duration verifyTimeoutOrDefault() {
    return minutesOrDefault(verifyMinutes());
  }

  public List<String> setupCommand() {
    return setup.command();
  }

  public Duration setupTimeoutOrDefault() {
    return minutesOrDefault(setup.minutes());
  }

  private static Duration minutesOrDefault(@Nullable Integer minutes) {
    return Duration.ofMinutes(minutes == null || minutes <= 0 ? 20 : minutes);
  }

  /** Repository paths, as globs, whose change only a human takes further. */
  public List<String> humanPathsOrNone() {
    return humanPaths();
  }

  public Remote gitRemote() {
    return Remote.of(remote());
  }

  /** The base branch as a LOCAL name, which is the form every other branch here is written in. */
  public String baseBranchName() {
    return gitRemote().branchOf(baseBranch());
  }

  /**
   * A deploy would merge into the branch tasks are cut from, which is the one write jagt must never
   * make.
   */
  public boolean deploysIntoTheBaseBranch() {
    String deploy = gitRemote().branchOf(deployBranch());
    return !deploy.isEmpty() && deploy.equals(baseBranchName());
  }
}
