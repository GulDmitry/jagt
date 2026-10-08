package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.HostStamp;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CodeHostAssistant;
import dev.jagt.orchestrator.protocol.MergeRequestRead;
import dev.jagt.orchestrator.protocol.ReviewRead;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.jagt.orchestrator.adapter.assistant.HeadlessClaude.FAILURE_RULE;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeCodeHostAssistant implements CodeHostAssistant {

    /** The sweep makes several code-host calls, not one lookup. */
    private static final Duration REVIEW_TIMEOUT = Duration.ofMinutes(6);
    private static final int MAX_RELAYED = 2000;

    private final HeadlessClaude claude;

    @Override
    public Answer<MergeRequestFacts> readMergeRequest(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Answer.unavailable();
        }
        String prompt = "<role>You read one merge/pull request from whichever code host holds it.</role>\n"
                + "<task>Fetch the merge/pull request at " + mrUrl + " via the matching code-host MCP tools"
                + " (GitLab MR, GitHub PR, Bitbucket PR — whichever the URL points to).</task>\n"
                + "<rules>Return exists=true"
                + " with its source branch as sourceBranch, the branch it merges INTO as targetBranch, and its"
                + " title." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return claude.read(prompt, MergeRequestRead.SCHEMA.json(), mrUrl, AssistantCallKind.MR_READ)
                .map(n -> new MergeRequestFacts(n.path("exists").asBoolean(false),
                        n.path("sourceBranch").asString(""), n.path("targetBranch").asString(""),
                        n.path("title").asString("")));
    }

    @Override
    public Answer<ReviewFacts> readReview(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Answer.unavailable();
        }
        String prompt = "<role>You sweep one merge/pull request for its review state.</role>\n"
                + "<task>Review sweep of the merge/pull request at " + mrUrl + " via the matching code-host"
                + " MCP tools.</task>\n"
                + "<rules>Return exists; approved (true only if the request is actually APPROVED by a human"
                + " reviewer — not merely mergeable); pipelineStatus, the CI PIPELINE's own latest result: LIST"
                + " this request's pipelines (or its head commit's check runs) with the host's own tool and read"
                + " the newest one. Exactly one of success | failed | running | none | unknown,"
                + " never the merge status (mergeable, can_be_merged) and never a review bot's verdict; none ONLY"
                + " when that listing came back EMPTY, and unknown where you could not list them at all."
                + " Return pipelineFailure ONLY where pipelineStatus is failed: the failing job's name and the"
                + " error lines of its log, at most 20 lines, cut to what names the fault. A bridge or trigger"
                + " job is never that job: follow it into the pipeline it started and read the one failing there."
                + " Where that log only points at an external analysis (a quality gate, a scanner), read its"
                + " verdict with that tool's own MCP when one is available: the project key, then one line per"
                + " failed condition as \"<metric> <actual> / <threshold>\". Tools an MCP keeps behind a"
                + " discovery or category tool are yours to activate first. A log or verdict you could not read"
                + " fails nothing: pipelineFailure is then the job's name and the lines you did read, never a word"
                + " of your own about what you could not. No timestamps, run"
                + " ids, URLs or durations — one failure read twice must read the same, or every poll relays a"
                + " brief the agent has already answered. Empty string in every other case."
                + " Return openedAt, the request's OWN creation timestamp"
                + " as the host reports it (ISO-8601; empty string if it does not say), and threads — ONE entry"
                + " per DISCUSSION THREAD still awaiting an answer, never one per note: every thread holding a"
                + " resolvable note that is not resolved. A RESOLVED thread is CLOSED — leave it out, whatever"
                + " landed in it since. Read each of them WHOLE with the host's discussion tool and keep EVERY"
                + " note in it, oldest first, bots and humans alike — never drop a note because an earlier one"
                + " already answers it, an answer being what the exchange is. One string per thread: its own"
                + " link (or file:line) on the first line, then one line per note as \"<author>: <body>\"."
                + " Copy each body as the host gives it, cut at its first 60 words — never paraphrased and"
                + " never summarised, a thread read twice having to read the same or every poll relays a brief"
                + " the agent has already answered. Empty array where no thread awaits an answer."
                + FAILURE_RULE
                + " The pipelines are the ONE exception to the failure rule above, and they change nothing"
                + " about exists: a listing you could not get is pipelineStatus=unknown with failure=\"\"."
                + "</rules>\n"
                + "Respond directly, no preamble.";
        return claude.read(prompt, ReviewRead.SCHEMA.json(), mrUrl, AssistantCallKind.REVIEW_SWEEP, REVIEW_TIMEOUT)
                .map(n -> {
                    List<String> threads = new ArrayList<>();
                    n.path("threads").forEach(t -> threads.add(cappedTail(t.asString(""))));
                    return new ReviewFacts(n.path("exists").asBoolean(false), n.path("approved").asBoolean(false),
                            n.path("pipelineStatus").asString(""), capped(n.path("pipelineFailure").asString("")),
                            threads, HostStamp.epochMillis(n.path("openedAt").asString("")));
                });
    }

    /** Relayed into a worktree file, so a host that answered with a whole build log is cut here. */
    private static String capped(String excerpt) {
        String trimmed = excerpt.strip();
        return trimmed.length() <= MAX_RELAYED ? trimmed : trimmed.substring(0, MAX_RELAYED) + "…";
    }

    /** A thread runs oldest note first, and the round answers its NEWEST: an over-long one loses its head. */
    private static String cappedTail(String thread) {
        String trimmed = thread.strip();
        return trimmed.length() <= MAX_RELAYED
                ? trimmed
                : "…" + trimmed.substring(trimmed.length() - MAX_RELAYED);
    }
}
