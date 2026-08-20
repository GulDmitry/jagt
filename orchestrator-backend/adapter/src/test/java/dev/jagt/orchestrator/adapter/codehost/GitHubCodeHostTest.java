package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.config.CodeHostProperties;
import dev.jagt.orchestrator.port.JsonHttp;
import dev.jagt.orchestrator.task.MergeRequestRef;
import dev.jagt.orchestrator.task.MergeRequestSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubCodeHostTest {

    private static final String PR_URL = "https://github.com/acme/widgets/pull/42";
    private static final String GRAPHQL = "https://api.github.com/graphql";
    private static final String PULLS = "https://api.github.com/repos/acme/widgets/pulls";

    private final JsonMapper json = new JsonMapper();
    private final JsonHttp http = mock(JsonHttp.class);
    private final GitHubCodeHost host = new GitHubCodeHost(http,
            new CodeHostProperties("github", "https://github.com", "secret-token"));

    @Test
    void relaysEveryCommentOfAnOpenThreadAndNothingFromAResolvedOne() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {"reviewThreads": {
                  "pageInfo": {"hasNextPage": false},
                  "nodes": [
                    {"isResolved": false, "path": "src/A.java", "line": 12, "comments": {"nodes": [
                       {"author": {"login": "coderabbit"}, "body": "Rename x\\nto y", "line": 12},
                       {"author": {"login": "lead"}, "body": "no, extract it", "line": 12}]}},
                    {"isResolved": true, "path": "src/B.java", "line": 3, "comments": {"nodes": [
                       {"author": {"login": "lead"}, "body": "already handled", "line": 3}]}}]}}}}}""")));

        var facts = host.readReview(PR_URL);

        assertThat(facts.orElseThrow().comments()).containsExactly(
                "coderabbit (src/A.java:12): Rename x to y",
                "lead (src/A.java:12): no, extract it");
    }

    @Test
    void readsTheApprovalDecisionAndTheHeadCommitsCheckRollup() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "reviewDecision": "APPROVED",
                  "commits": {"nodes": [{"commit": {"statusCheckRollup": {"state": "SUCCESS"}}}]},
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        var facts = host.readReview(PR_URL).orElseThrow();

        assertThat(facts.approved()).isTrue();
        assertThat(facts.pipelineStatus()).isEqualTo("success");
    }

    @Test
    void readsWhenTheHostSaysTheRequestWasOpenedSoTheWaitIsMeasurableFromTheHostNotFromJagt() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "createdAt": "2026-08-18T11:41:00Z",
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL).orElseThrow().openedAt())
                .isEqualTo(java.time.Instant.parse("2026-08-18T11:41:00Z").toEpochMilli());
    }

    @Test
    void reportsChangesRequestedAsNotApproved() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "reviewDecision": "CHANGES_REQUESTED",
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL).orElseThrow().approved()).isFalse();
    }

    @Test
    void treatsAChecksErrorAsAFailureAndNotAsSomethingStillRunning() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "commits": {"nodes": [{"commit": {"statusCheckRollup": {"state": "ERROR"}}}]},
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL).orElseThrow().pipelineStatus()).isEqualTo("failed");
    }

    @Test
    void reportsAPullRequestWithoutAnyChecksAsNone() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "commits": {"nodes": [{"commit": {"statusCheckRollup": null}}]},
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL).orElseThrow().pipelineStatus()).isEqualTo("none");
    }

    @Test
    void failsTheWholeReadWhenTheHostAnswersWithErrorsRatherThanLookingClean() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": null},
                 "errors": [{"message": "Could not resolve to a Repository named 'acme/widgets'."}]}""")));

        assertThat(host.readReview(PR_URL)).isEmpty();
    }

    @Test
    void relaysWhatAReviewerWroteInTheReviewItselfWithoutAnyInlineThread() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "reviewDecision": "CHANGES_REQUESTED",
                  "latestReviews": {"nodes": [{"state": "CHANGES_REQUESTED", "author": {"login": "lead"},
                                              "body": "Split this\\nservice first"}]},
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        var facts = host.readReview(PR_URL).orElseThrow();

        assertThat(facts.comments()).containsExactly("lead: Split this service first");
    }

    /**
     * With an empty list and a green rollup the sweep advances the task to REVIEWED and tells the human to
     * deploy — while the host is blocking the merge.
     */
    @Test
    void neverReportsARequestWithChangesRequestedAsHavingNothingToAnswer() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "reviewDecision": "CHANGES_REQUESTED",
                  "commits": {"nodes": [{"commit": {"statusCheckRollup": {"state": "SUCCESS"}}}]},
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL).orElseThrow().comments()).isNotEmpty();
    }

    @Test
    void readsAnApprovalOnARepositoryThatRequiresNoReview() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "latestReviews": {"nodes": [{"state": "APPROVED", "author": {"login": "lead"}, "body": ""}]},
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL).orElseThrow().approved()).isTrue();
    }

    @Test
    void reportsNotApprovedWhileAnyReviewerStillAsksForChanges() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "latestReviews": {"nodes": [{"state": "APPROVED", "author": {"login": "lead"}, "body": ""},
                                              {"state": "CHANGES_REQUESTED", "author": {"login": "other"},
                                               "body": "not yet"}]},
                  "reviewThreads": {"pageInfo": {"hasNextPage": false}, "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL).orElseThrow().approved()).isFalse();
    }

    @Test
    void followsTheThreadCursorInsteadOfRefusingALongReview() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(
                Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {"reviewThreads": {
                  "pageInfo": {"hasNextPage": true, "endCursor": "page-2"},
                  "nodes": [{"isResolved": false, "path": "src/A.java", "line": 1, "comments": {"nodes": [
                     {"author": {"login": "lead"}, "body": "first page", "line": 1}]}}]}}}}}""")),
                Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {"reviewThreads": {
                  "pageInfo": {"hasNextPage": false},
                  "nodes": [{"isResolved": false, "path": "src/B.java", "line": 2, "comments": {"nodes": [
                     {"author": {"login": "lead"}, "body": "second page", "line": 2}]}}]}}}}}""")));

        var facts = host.readReview(PR_URL).orElseThrow();

        assertThat(facts.comments()).containsExactly(
                "lead (src/A.java:1): first page", "lead (src/B.java:2): second page");
    }

    @Test
    void refusesToRelayAReviewWhoseThreadsNeverEnd() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {"reviewThreads": {
                  "pageInfo": {"hasNextPage": true, "endCursor": "next"},
                  "nodes": [{"isResolved": false, "path": "src/A.java", "line": 1, "comments": {"nodes": [
                     {"author": {"login": "lead"}, "body": "one of many", "line": 1}]}}]}}}}}""")));

        assertThat(host.readReview(PR_URL)).isEmpty();
    }

    @Test
    void refusesToRelayWhenTheHostPromisesMoreThreadsWithoutACursorToReadThemWith() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {"reviewThreads": {
                  "pageInfo": {"hasNextPage": true},
                  "nodes": []}}}}}""")));

        assertThat(host.readReview(PR_URL)).isEmpty();
    }

    @Test
    void failsTheReadWhenNoThreadListCameBackRatherThanLookingClean() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "commits": {"nodes": [{"commit": {"statusCheckRollup": {"state": "SUCCESS"}}}]}}}}}""")));

        assertThat(host.readReview(PR_URL)).isEmpty();
    }

    @Test
    void refusesToRelayAThreadThatIsLongerThanOneReadCarries() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {"reviewThreads": {
                  "pageInfo": {"hasNextPage": false},
                  "nodes": [{"isResolved": false, "path": "src/A.java", "line": 1, "comments": {
                     "pageInfo": {"hasNextPage": true},
                     "nodes": [{"author": {"login": "lead"}, "body": "one of many", "line": 1}]}}]}}}}}""")));

        assertThat(host.readReview(PR_URL)).isEmpty();
    }

    @Test
    void authenticatesWithTheConfiguredTokenAsABearer() {
        when(http.post(anyString(), anyMap(), anyMap())).thenReturn(Optional.empty());

        host.readReview(PR_URL);

        verify(http).post(eq(GRAPHQL), eq(Map.of("Authorization", "Bearer secret-token",
                "Accept", "application/vnd.github+json")), anyMap());
    }

    @Test
    void asksTheReviewOfAnEnterpriseHostUnderThatHostsOwnApiRoot() {
        var enterprise = new GitHubCodeHost(http,
                new CodeHostProperties("github", "https://ghe.example.com", "secret-token"));
        when(http.post(anyString(), anyMap(), anyMap())).thenReturn(Optional.empty());

        enterprise.readReview("https://ghe.example.com/acme/widgets/pull/42");

        verify(http).post(eq("https://ghe.example.com/api/graphql"), anyMap(), anyMap());
    }

    @Test
    void claimsThePullRequestUrlsOfTheHostItWasPointedAt() {
        assertThat(host.supports(PR_URL)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "https://github.example.com/acme/widgets/pull/1",
            "https://github.com/acme/widgets/issues/7",
            "https://github.com/acme/widgets/pull/"
    })
    void claimsNothingItCannotActuallyFetch(String url) {
        assertThat(host.supports(url)).isFalse();
    }

    @Test
    void opensAPullRequestWhenTheBranchHasNoneYet() {
        when(http.get(eq(PULLS + "?state=open&head=acme:ABC-1&base=main"), anyMap()))
                .thenReturn(Optional.of(json.readTree("[]")));
        when(http.post(eq(PULLS), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"html_url": "https://github.com/acme/widgets/pull/42"}""")));

        var opened = host.createOrUpdateMergeRequest(new MergeRequestSpec(
                "git@github.com:acme/widgets.git", "ABC-1", "main", "ABC-1 Widget layout is off", true, true));

        assertThat(opened).contains(new MergeRequestRef("https://github.com/acme/widgets/pull/42", true));
        verify(http).post(eq(PULLS), anyMap(), eq(Map.of(
                "title", "ABC-1 Widget layout is off", "head", "ABC-1", "base", "main")));
    }

    @Test
    void matchesAnOpenPullRequestByBranchNamesThatCarrySlashes() {
        when(http.get(anyString(), anyMap())).thenReturn(Optional.of(json.readTree("[]")));
        when(http.post(anyString(), anyMap(), anyMap())).thenReturn(Optional.empty());

        host.createOrUpdateMergeRequest(new MergeRequestSpec("git@github.com:acme/widgets.git",
                "ABC-1", "release/1.2", "ABC-1 Widget layout is off", true, true));

        verify(http).get(eq(PULLS + "?state=open&head=acme:ABC-1&base=release%2F1.2"), anyMap());
    }

    @Test
    void reusesTheOpenPullRequestInsteadOfOpeningASecondForTheSameBranch() {
        when(http.get(eq(PULLS + "?state=open&head=acme:ABC-1&base=main"), anyMap()))
                .thenReturn(Optional.of(json.readTree("""
                [{"html_url": "https://github.com/acme/widgets/pull/42"}]""")));

        var found = host.createOrUpdateMergeRequest(new MergeRequestSpec(
                "git@github.com:acme/widgets.git", "ABC-1", "main", "a retitled thing", true, true));

        assertThat(found).contains(new MergeRequestRef("https://github.com/acme/widgets/pull/42", false));
        verify(http, never()).post(anyString(), anyMap(), anyMap());
        verify(http, never()).put(anyString(), anyMap(), anyMap());
    }

    @Test
    void reportsNothingWhenTheHostRefusesToOpenThePullRequest() {
        when(http.get(eq(PULLS + "?state=open&head=acme:ABC-1&base=main"), anyMap()))
                .thenReturn(Optional.of(json.readTree("[]")));
        when(http.post(eq(PULLS), anyMap(), anyMap())).thenReturn(Optional.empty());

        var opened = host.createOrUpdateMergeRequest(new MergeRequestSpec(
                "git@github.com:acme/widgets.git", "ABC-1", "main", "ABC-1 Widget layout is off", true, true));

        assertThat(opened).isEmpty();
    }

    @Test
    void refusesToOpenAPullRequestOnARepositoryOfAnotherHost() {
        var opened = host.createOrUpdateMergeRequest(new MergeRequestSpec(
                "git@gitlab.com:someone/else.git", "ABC-1", "main", "ABC-1 Widget layout is off", true, true));

        assertThat(opened).isEmpty();
        verify(http, never()).post(anyString(), anyMap(), anyMap());
    }

    @ParameterizedTest
    @CsvSource({
        "git@github.com:acme/widgets.git, true",
        "https://github.com/acme/widgets.git, true",
        "git@git.example.com:acme/widgets.git, false"
    })
    void hostsOnlyTheRepositoriesOfTheHostItWasPointedAt(String remote, boolean mine) {
        assertThat(host.hostsRepository(remote)).isEqualTo(mine);
    }

    @Test
    void claimsNothingUntilBothItsBaseUrlAndTokenAreConfigured() {
        var tokenless = new GitHubCodeHost(http, new CodeHostProperties("github", "https://github.com", " "));

        assertThat(tokenless.supports(PR_URL)).isFalse();
    }

    @Test
    void readsTheBranchesAndTitleOfAnOpenRequestForATaskResumingOnIt() {
        when(http.post(eq(GRAPHQL), anyMap(), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"data": {"repository": {"pullRequest": {
                  "headRefName": "ABC-1", "baseRefName": "release/2",
                  "title": "ABC-1 Widget layout is off"}}}}""")));

        var request = host.readRequest(PR_URL).orElseThrow();

        assertThat(request.sourceBranch()).isEqualTo("ABC-1");
        assertThat(request.targetBranch()).isEqualTo("release/2");
        assertThat(request.title()).isEqualTo("ABC-1 Widget layout is off");
    }
}
