package dev.jagt.orchestrator.tracker;

import dev.jagt.orchestrator.config.TrackerProperties;
import dev.jagt.orchestrator.http.JsonHttp;
import dev.jagt.orchestrator.model.TicketFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JiraTrackerTest {

    private static final String ISSUE_API =
            "https://tracker.example.com/rest/api/2/issue/ABC-42?fields=summary,labels,project";

    private final JsonMapper json = new JsonMapper();
    private final JsonHttp http = mock(JsonHttp.class);
    private final JiraTracker tracker = new JiraTracker(http,
            new TrackerProperties("jira", "https://tracker.example.com", null, "secret-token"));

    @Test
    void readsTheTitleLabelsAndProjectOfAnIssueKey() {
        when(http.get(eq(ISSUE_API), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"key": "ABC-42", "fields": {"summary": "Widget layout is off",
                 "labels": ["backend", "no-test"], "project": {"key": "ABC"}}}""")));

        var facts = tracker.readTicket("ABC-42").orElseThrow();

        assertThat(facts).isEqualTo(new TicketFacts(true, "ABC-42", "Widget layout is off", "ABC",
                java.util.List.of("backend", "no-test"), "https://tracker.example.com/browse/ABC-42"));
    }

    @Test
    void namesTheTaskByTheKeyTheIssueAnswersUnderRatherThanTheOneAskedFor() {
        when(http.get(anyString(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"key": "XYZ-9", "fields": {"summary": "moved ticket", "project": {"key": "XYZ"}}}""")));

        var facts = tracker.readTicket("ABC-42").orElseThrow();

        assertThat(facts.key()).isEqualTo("XYZ-9");
        assertThat(facts.url()).isEqualTo("https://tracker.example.com/browse/XYZ-9");
    }

    @Test
    void readsAnIssueByTheBrowseLinkTheTrackerItselfHandsOut() {
        when(http.get(eq(ISSUE_API), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"key": "ABC-42", "fields": {"summary": "t", "project": {"key": "ABC"}}}""")));

        assertThat(tracker.readTicket("https://tracker.example.com/browse/ABC-42")).isPresent();
    }

    /**
     * A launch tells "no such ticket" from "nobody read it" by this flag alone, and the second one creates the
     * task from the bare key — so a mistyped key answering nothing would provision a worktree, a branch and an
     * agent for a ticket that does not exist.
     */
    @Test
    void reportsAnIssueItCouldNotFetchAsOneThatDoesNotExist() {
        when(http.get(anyString(), anyMap())).thenReturn(Optional.empty());

        assertThat(tracker.readTicket("ABC-42").orElseThrow().exists()).isFalse();
    }

    @Test
    void claimsNoRefOfAnotherTrackerToReadAtAll() {
        assertThat(tracker.readTicket("https://other.example.com/browse/ABC-42")).isEmpty();
    }

    @Test
    void sendsAStandaloneTokenAsABearer() {
        when(http.get(anyString(), anyMap())).thenReturn(Optional.empty());

        tracker.readTicket("ABC-42");

        verify(http).get(eq(ISSUE_API), eq(Map.of("Authorization", "Bearer secret-token")));
    }

    @Test
    void sendsATokenThatBelongsToAnAccountAsThatAccountsCredentials() {
        var cloud = new JiraTracker(http, new TrackerProperties("jira", "https://tracker.example.com",
                "someone@example.com", "secret-token"));
        when(http.get(anyString(), anyMap())).thenReturn(Optional.empty());

        cloud.readTicket("ABC-42");

        verify(http).get(eq(ISSUE_API), eq(Map.of("Authorization",
                "Basic c29tZW9uZUBleGFtcGxlLmNvbTpzZWNyZXQtdG9rZW4=")));
    }

    @Test
    void claimsAnIssueKeyAndItsOwnBrowseLink() {
        assertThat(tracker.supports("ABC-42")).isTrue();
        assertThat(tracker.supports("https://tracker.example.com/browse/ABC-42")).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "https://other.example.com/browse/ABC-42",
            "https://tracker.example.com/browse/not-a-key",
            "https://tracker.example.com/some/other/page",
            "just words"
    })
    void claimsNothingItCannotActuallyFetch(String ref) {
        assertThat(tracker.supports(ref)).isFalse();
        verifyNoInteractions(http);
    }

    @Test
    void claimsNothingUntilBothItsBaseUrlAndTokenAreConfigured() {
        var tokenless = new JiraTracker(http,
                new TrackerProperties("jira", "https://tracker.example.com", null, " "));

        assertThat(tokenless.supports("ABC-42")).isFalse();
    }
}
