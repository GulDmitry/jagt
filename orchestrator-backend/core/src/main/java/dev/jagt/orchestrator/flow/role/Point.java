package dev.jagt.orchestrator.flow.role;

/**
 * A fact worth telling a human: its id and what makes it stand. A raised point has no stored fact,
 * so whoever raises it supplies its title and body.
 */
public record Point(String id, String stands, boolean raised) {

  public static final Point YOUR_MOVE =
      new Point("your-move", "the turn has been the human's for five seconds", false);
  public static final Point STOPPED =
      new Point("stopped", "a stop is set or the watchdog read silence", false);
  public static final Point AUTO_REVIEW =
      new Point(
          "auto-review", "an auto-review window elapsed, a poll gave up, a request is gone", false);
  public static final Point CONFLICT_STALLED =
      new Point(
          "conflict-stalled", "a handed conflict stopped moving and the human was told", false);
  public static final Point REQUEST_OPENED =
      new Point("request-opened", "the task carries requests", false);
  public static final Point CHECKS_GREEN =
      new Point("checks-green", "the pipeline on the tip passed", false);
  public static final Point CHECKS_FAILED =
      new Point("checks-failed", "the pipeline on the tip failed with no infra retry armed", false);
  public static final Point APPROVED = new Point("approved", "the round is approved", false);
  public static final Point DEPLOY_PRESSED =
      new Point("deploy-pressed", "a deployment is set", false);
  public static final Point DEPLOYED = new Point("deployed", "a deploy target landed", false);
  public static final Point DEPLOY_CONFLICT =
      new Point("deploy-conflict", "the deploy stands in conflict", false);
  public static final Point REVERTED = new Point("reverted", "a revert commit exists", false);
  public static final Point MERGED = new Point("merged", "every request is merged", false);
  public static final Point DONE = new Point("done", "the task is done", false);
  public static final Point MASTER_SEALED =
      new Point("master-sealed", "the Master sealed a plan or a round", false);
  public static final Point MASTER_ASKED_STOP =
      new Point("master-asked-stop", "the Master asked for a stop", false);
  public static final Point MASTER_LEFT =
      new Point("master-left", "the Master left a plan or a round for the human", false);
  public static final Point MASTER_UNANSWERED =
      new Point("master-unanswered", "the Master left a question unanswered", false);
  public static final Point MASTER_PRESSED_DEPLOY =
      new Point("master-pressed-deploy", "the Master pressed deploy in act", false);
  public static final Point MASTER_SHIPPED_COMMENTED =
      new Point("master-shipped-commented", "the Master shipped a round with a comment", false);
  public static final Point HOUSEKEEPING =
      new Point("housekeeping", "something on disk wants a human eventually", true);
  public static final Point INSTALL =
      new Point("install", "jagt cannot go on correctly until the human acts", true);
  public static final Point USAGE =
      new Point("usage", "a subscription window filled or paid usage is spent", true);

  static Point of(String role, RolePointKind kind) {
    return new Point(role + "-" + kind.id(), kind.stands(), false);
  }
}
