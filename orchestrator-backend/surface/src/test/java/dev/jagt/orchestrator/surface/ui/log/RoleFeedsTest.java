package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import dev.jagt.orchestrator.service.role.model.ModelRoles;
import dev.jagt.orchestrator.surface.ui.log.RoleFeeds.RoleFeedWritten;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RoleFeedsTest {

  private final LoggerContext context = new LoggerContext();
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ModelRoles roles = mock(ModelRoles.class);

  @Test
  void keepsEachRolesLinesApart() {
    new TestRoleFeedBuilder(context, "master").build().info("answered ABC-42");
    new TestRoleFeedBuilder(context, "acceptance").build().info("checked ABC-42");

    RoleFeeds feeds = new RoleFeeds(context, events, roles);

    assertThat(feeds.after("master", 0).orElseThrow().lines()).containsExactly("answered ABC-42");
    assertThat(feeds.after("acceptance", 0).orElseThrow().lines())
        .containsExactly("checked ABC-42");
  }

  @Test
  void handsOnlyTheLinesWrittenAfterTheCursor() {
    Logger logger = new TestRoleFeedBuilder(context, "master").build();
    logger.info("one");
    logger.info("two");
    logger.info("three");

    RecentLines.Slice slice =
        new RoleFeeds(context, events, roles).after("master", 1).orElseThrow();

    assertThat(slice.lines()).containsExactly("two", "three");
    assertThat(slice.next()).isEqualTo(3);
  }

  @Test
  void handsNothingNewWhereTheCursorIsAtTheEnd() {
    new TestRoleFeedBuilder(context, "master").build().info("one");

    RecentLines.Slice slice =
        new RoleFeeds(context, events, roles).after("master", 1).orElseThrow();

    assertThat(slice.lines()).isEmpty();
    assertThat(slice.next()).isEqualTo(1);
  }

  @Test
  void hasNoFeedForRoleNoModuleDeclared() {
    assertThat(new RoleFeeds(context, events, roles).after("ghost", 0)).isEmpty();
  }

  @Test
  void announcesRolesNewLineOnceTheContextStarted() {
    Logger logger = new TestRoleFeedBuilder(context, "triage").build();
    new RoleFeeds(context, events, roles);

    logger.info("read a stop");

    verify(events).publishEvent(new RoleFeedWritten("triage"));
  }

  @Test
  void servesRunningRolesLinesAfterTheCursor() {
    new TestRoleFeedBuilder(context, "master").build().info("one");
    when(roles.runs("master")).thenReturn(true);

    RecentLines.Slice slice = new RoleFeeds(context, events, roles).log("master", 0);

    assertThat(slice.lines()).containsExactly("one");
  }

  @Test
  void refusesTheLogOfRoleThatDoesNotRun() {
    new TestRoleFeedBuilder(context, "triage").build();
    when(roles.runs("triage")).thenReturn(false);

    assertThatThrownBy(() -> new RoleFeeds(context, events, roles).log("triage", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesRunningRoleWithNoFeed() {
    when(roles.runs("master")).thenReturn(true);

    assertThatThrownBy(() -> new RoleFeeds(context, events, roles).log("master", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
