package dev.jagt.orchestrator.codehost;

import dev.jagt.orchestrator.config.CodeHostProperties;
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
import static org.mockito.Mockito.when;

class GitLabCodeHostTest {

    private static final String MR_API =
            "https://git.example.com/api/v4/projects/group%2Fsub%2Fproject/merge_requests/42";
    private static final String MR_URL = "https://git.example.com/group/sub/project/-/merge_requests/42";

    private final JsonMapper json = new JsonMapper();
    private final JsonHttp http = mock(JsonHttp.class);
    private final GitLabCodeHost host = new GitLabCodeHost(http,
            new CodeHostProperties("gitlab", "https://git.example.com", "secret-token"));

    @Test
    void relaysOnlyTheUnresolvedNotesEachAsOneLineForTheAgent() {
        when(http.get(eq(MR_API), anyMap())).thenReturn(Optional.of(json.readTree("{\"iid\": 42}")));
        when(http.get(eq(MR_API + "/approvals"), anyMap())).thenReturn(Optional.of(json.readTree("{}")));
        when(http.get(eq(MR_API + "/discussions?per_page=100&page=1"), anyMap()))
                .thenReturn(Optional.of(json.readTree("""
                [{"notes": [{"resolvable": true, "resolved": false, "author": {"username": "coderabbit"},
                             "body": "Rename x\\nto y", "position": {"new_path": "src/A.java", "new_line": 12}}]},
                 {"notes": [{"resolvable": true, "resolved": true, "author": {"username": "lead"},
                             "body": "already handled"}]},
                 {"notes": [{"system": true, "resolvable": false, "body": "changed the description"}]}]""")));

        var facts = host.readReview(MR_URL);

        assertThat(facts.orElseThrow().comments())
                .containsExactly("coderabbit (src/A.java:12): Rename x to y");
    }

    @Test
    void readsTheApprovalAndPipelineStateOffTheHostsApi() {
        when(http.get(eq(MR_API), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"iid": 42, "head_pipeline": {"status": "failed"}}""")));
        when(http.get(eq(MR_API + "/approvals"), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"approved_by": [{"user": {"username": "lead"}}]}""")));
        when(http.get(eq(MR_API + "/discussions?per_page=100&page=1"), anyMap()))
                .thenReturn(Optional.of(json.readTree("[]")));

        var facts = host.readReview(MR_URL).orElseThrow();

        assertThat(facts.approved()).isTrue();
        assertThat(facts.pipelineStatus()).isEqualTo("failed");
    }

    @Test
    void authenticatesWithTheConfiguredTokenAsAPrivateHeader() {
        when(http.get(anyString(), anyMap())).thenReturn(Optional.of(json.readTree("[]")));

        host.readReview(MR_URL);

        verify(http).get(eq(MR_API), eq(Map.of("PRIVATE-TOKEN", "secret-token")));
    }

    @Test
    void failsTheWholeReadWhenTheDiscussionsCannotBeFetchedRatherThanLookingClean() {
        when(http.get(eq(MR_API), anyMap())).thenReturn(Optional.of(json.readTree("""
                {"iid": 42, "head_pipeline": {"status": "success"}}""")));
        when(http.get(eq(MR_API + "/approvals"), anyMap())).thenReturn(Optional.of(json.readTree("{}")));
        when(http.get(eq(MR_API + "/discussions?per_page=100&page=1"), anyMap())).thenReturn(Optional.empty());

        assertThat(host.readReview(MR_URL)).isEmpty();
    }

    @Test
    void reportsNotApprovedWhenTheApprovalsEndpointIsUnreadable() {
        when(http.get(eq(MR_API), anyMap())).thenReturn(Optional.of(json.readTree("{\"iid\": 42}")));
        when(http.get(eq(MR_API + "/approvals"), anyMap())).thenReturn(Optional.empty());
        when(http.get(eq(MR_API + "/discussions?per_page=100&page=1"), anyMap()))
                .thenReturn(Optional.of(json.readTree("[]")));

        assertThat(host.readReview(MR_URL).orElseThrow().approved()).isFalse();
    }

    @Test
    void reportsAMergeRequestWithoutAnyPipelineAsNone() {
        when(http.get(eq(MR_API), anyMap())).thenReturn(Optional.of(json.readTree("{\"iid\": 42}")));
        when(http.get(eq(MR_API + "/approvals"), anyMap())).thenReturn(Optional.of(json.readTree("{}")));
        when(http.get(eq(MR_API + "/discussions?per_page=100&page=1"), anyMap()))
                .thenReturn(Optional.of(json.readTree("[]")));

        assertThat(host.readReview(MR_URL).orElseThrow().pipelineStatus()).isEqualTo("none");
    }

    @Test
    void claimsTheMergeRequestUrlsOfTheHostItWasPointedAt() {
        assertThat(host.supports(MR_URL)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "https://gitlab.com/other/project/-/merge_requests/1",
            "https://git.example.com/group/project/-/issues/7",
            "https://git.example.com/group/project/-/merge_requests/"
    })
    void claimsNothingItCannotActuallyFetch(String url) {
        assertThat(host.supports(url)).isFalse();
    }

    @Test
    void claimsNothingUntilBothItsBaseUrlAndTokenAreConfigured() {
        var tokenless = new GitLabCodeHost(http, new CodeHostProperties("gitlab", "https://git.example.com", " "));

        assertThat(tokenless.supports(MR_URL)).isFalse();
    }
}
