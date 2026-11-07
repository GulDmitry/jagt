package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class StateFileCompatibilityTest {

    @Test
    void aStateFileWrittenBeforeRolesLoadsWithQasPassAndTheMastersSeal() {
        String json = """
                {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "ticketUrl":"https://tracker.example/ABC-42",
                 "repos":[{"project":"proj","worktreePath":"/wt"}],
                 "turn":{"step":"ship","id":3,"digest":"","since":4,"marks":{"qa:digest":"d1","press:ship":"tree"}},
                 "qa":{"passed":"proj@abc"},
                 "master":{"seal":"abc","sealedRound":3}}
                """;

        TaskState task = new JsonMapper().readValue(json, TaskState.class);

        assertThat(task.roleOrNone("qa").passed()).isEqualTo("proj@abc");
        assertThat(task.masterOrNone()).isEqualTo(new Master("", "", "", "abc", 3, "", 0));
        assertThat(task.turnOrNone().marks()).containsOnlyKeys("press:ship");
    }

    @Test
    void aCurrentStateFileIsWrittenBackKeyForKey() {
        String json = """
                {
                "repos":[{"project":"proj","worktreePath":"/wt","remoteUrl":"https://remote.example/p.git","mrUrl":"https://host.example/mr/1","merged":true,"hostMerged":false}],
                "status":"DEPLOYED",
                "lastActiveTimestamp":11,
                "message":"line",
                "report":"QUESTION",
                "alias":"abc",
                "title":"A title",
                "ticketUrl":"https://tracker.example/ABC-42",
                "baseBranch":"develop",
                "mrCreatedAt":12,
                "requestOpenedAt":13,
                "lastPolledAt":14,
                "autoReview":true,
                "pipelineStatus":"failed",
                "checksUnread":[{"at":15,"cause":"timeout"}],
                "approved":true,
                "usage":{"calls":1,"inputTokens":2,"cachedInputTokens":3,"outputTokens":4,"costUsd":5.5,"coldStarts":6,"warmStarts":7,"unpricedCalls":8},
                "agentSpend":{"usage":{"calls":1,"inputTokens":2,"cachedInputTokens":3,"outputTokens":4,"costUsd":5.5,"coldStarts":0,"warmStarts":0},"marks":{"log":9}},
                "history":[{"status":"NEW","at":1,"origin":"BOARD","why":"why","tree":"tree"}],
                "refusals":{"SESSION_LINE":2},
                "gitTrust":{"recorded":{"hooks":"h1"},"changed":"hooks"},
                "probes":[{"kind":"COMMAND","repo":"proj","words":["make"],"exit":1,"passed":false,"excerpt":"out"}],
                "planGate":"REQUIRED",
                "trail":{"rounds":[{"at":16,"threads":2,"failing":"job","digest":"dg","notes":"nt"}],"since":17},
                "stop":{"trigger":"STALLED","at":18,"evidence":"ev","request":"rq"},
                "repoRequest":"other",
                "turn":{"step":"ship","id":3,"digest":"dg","since":19,"marks":{"k":"v"},"run":[20]},
                "switches":{"sw":"yes - why"},
                "infraRetries":[{"at":21,"project":"proj","pipelineUrl":"https://host.example/p/1","jobs":"j","reason":"r","sent":true,"attempt":1,"of":3}],
                "checksTip":"tip1",
                "headTip":"tip2",
                "deployment":{"pressedAt":22,"targets":[{"project":"proj","deployBranch":"","fixes":[],"pushedAt":0}],"at":0},
                "session":{"movedAt":23,"workStartedAt":0,"reportedAt":0,"halt":{"kind":"FAILED","at":24,"cause":"c"},"quietSince":0,"log":"log","stampedAsk":true,"generation":2,"window":{"state":"RUNNING","at":25,"printedAt":26}},
                "told":{"windowElapsed":27,"unreadable":28,"none":false},
                "waits":[{"since":29,"until":30,"why":"QUESTION"}],
                "roles":{"qa":{"reading":"r","passed":"p","roundDigest":"d","roundRows":"rows"}},
                "master":{"left":"l","unanswered":"u","pressedDeploy":"p","seal":"s","sealedRound":31,"askedStop":"a","liftedStops":2}
                }
                """;
        JsonMapper mapper = new JsonMapper();

        TaskState task = mapper.readValue(json, TaskState.class);

        assertThat(mapper.readTree(mapper.writeValueAsString(task))).isEqualTo(mapper.readTree(json));
    }

    @Test
    void aStateFileInTheOldFlatShapeIsWrittenBackAsTheCurrentOne() {
        String old = """
                {
                "project":"proj",
                "worktreePath":"/wt",
                "remoteUrl":"https://remote.example/p.git",
                "mrUrl":"https://host.example/mr/1",
                "deployCommit":"c0ffee",
                "status":"DEPLOYED",
                "lastActiveTimestamp":11,
                "message":"line",
                "report":"QUESTION",
                "alias":"abc",
                "title":"A title",
                "ticketUrl":"https://tracker.example/ABC-42",
                "baseBranch":"develop",
                "mrCreatedAt":12,
                "requestOpenedAt":13,
                "lastPolledAt":14,
                "silentSince":32,
                "autoReview":true,
                "pipelineStatus":"failed",
                "pipelineUnread":true,
                "approved":true,
                "usage":{"calls":1,"inputTokens":2,"cachedInputTokens":3,"outputTokens":4,"costUsd":5.5,"coldStarts":6,"warmStarts":7,"unpricedCalls":8},
                "agentSpend":{"usage":{"calls":1,"inputTokens":2,"cachedInputTokens":3,"outputTokens":4,"costUsd":5.5,"coldStarts":0,"warmStarts":0},"marks":{"log":9}},
                "history":[{"status":"NEW","at":1,"origin":"BOARD","why":"why","tree":"tree"}],
                "refusals":{"SESSION_LINE":2},
                "gitTrust":{"recorded":{"hooks":"h1"},"changed":"hooks"},
                "probes":[{"kind":"COMMAND","repo":"proj","words":["make"],"exit":1,"passed":false,"excerpt":"out"}],
                "planGate":"REQUIRED",
                "loop":{"rounds":[{"at":16,"threads":2,"failing":"job","digest":"dg","notes":"nt"}],"since":17,"stop":{"trigger":"STALLED","at":18,"evidence":"ev","request":"rq"}},
                "conflict":{"conflictSince":33,"worktree":"/dwt","movedAt":34},
                "repoRequest":"other",
                "turn":{"step":"ship","id":3,"digest":"dg","since":19,"marks":{"k":"v"},"run":[20]},
                "switches":{"sw":"yes - why"},
                "infraRetries":[{"at":21,"project":"proj","pipelineUrl":"https://host.example/p/1","jobs":"j","reason":"r","sent":true,"attempt":1,"of":3}],
                "checksTip":"tip1",
                "headTip":"tip2",
                "session":{"movedAt":23,"workStartedAt":0,"reportedAt":0,"quietSince":0,"log":"log","stampedAsk":true,"generation":2,"halt":{"kind":"FAILED","at":24,"cause":"c"},"window":{"state":"RUNNING","at":25,"printedAt":26}},
                "told":{"windowElapsed":27,"unreadable":28},
                "waits":[{"since":29,"until":30,"why":"QUESTION"}],
                "qa":{"reading":"r","passed":"p","roundDigest":"d","roundRows":"rows"},
                "master":{"left":"l","unanswered":"u","pressedDeploy":"p","seal":"s","sealedRound":31,"askedStop":"a","liftedStops":2}
                }
                """;
        String current = """
                {
                "repos":[{"project":"proj","worktreePath":"/wt","remoteUrl":"https://remote.example/p.git","mrUrl":"https://host.example/mr/1"}],
                "status":"DEPLOYED",
                "lastActiveTimestamp":11,
                "message":"line",
                "report":"QUESTION",
                "alias":"abc",
                "title":"A title",
                "ticketUrl":"https://tracker.example/ABC-42",
                "baseBranch":"develop",
                "mrCreatedAt":12,
                "requestOpenedAt":13,
                "lastPolledAt":14,
                "autoReview":true,
                "pipelineStatus":"failed",
                "checksUnread":[{"at":0,"cause":""}],
                "approved":true,
                "usage":{"calls":1,"inputTokens":2,"cachedInputTokens":3,"outputTokens":4,"costUsd":5.5,"coldStarts":6,"warmStarts":7,"unpricedCalls":8},
                "agentSpend":{"usage":{"calls":1,"inputTokens":2,"cachedInputTokens":3,"outputTokens":4,"costUsd":5.5,"coldStarts":0,"warmStarts":0},"marks":{"log":9}},
                "history":[{"status":"NEW","at":1,"origin":"BOARD","why":"why","tree":"tree"}],
                "refusals":{"SESSION_LINE":2},
                "gitTrust":{"recorded":{"hooks":"h1"},"changed":"hooks"},
                "probes":[{"kind":"COMMAND","repo":"proj","words":["make"],"exit":1,"passed":false,"excerpt":"out"}],
                "planGate":"REQUIRED",
                "trail":{"rounds":[{"at":16,"threads":2,"failing":"job","digest":"dg","notes":"nt"}],"since":17},
                "stop":{"trigger":"STALLED","at":18,"evidence":"ev","request":"rq"},
                "repoRequest":"other",
                "turn":{"step":"ship","id":3,"digest":"dg","since":19,"marks":{"k":"v"},"run":[20]},
                "switches":{"sw":"yes - why"},
                "infraRetries":[{"at":21,"project":"proj","pipelineUrl":"https://host.example/p/1","jobs":"j","reason":"r","sent":true,"attempt":1,"of":3}],
                "checksTip":"tip1",
                "headTip":"tip2",
                "deployment":{"pressedAt":1,"targets":[{"project":"proj","deployBranch":"","merge":"c0ffee","fixes":[],"pushedAt":1}],"at":1},
                "session":{"movedAt":23,"workStartedAt":0,"reportedAt":0,"halt":{"kind":"FAILED","at":24,"cause":"c"},"quietSince":0,"log":"log","stampedAsk":true,"generation":2,"window":{"state":"RUNNING","at":25,"printedAt":26}},
                "told":{"windowElapsed":27,"unreadable":28,"none":false},
                "waits":[{"since":29,"until":30,"why":"QUESTION"}],
                "roles":{"qa":{"reading":"r","passed":"p","roundDigest":"d","roundRows":"rows"}},
                "master":{"left":"l","unanswered":"u","pressedDeploy":"p","seal":"s","sealedRound":31,"askedStop":"a","liftedStops":2}
                }
                """;
        JsonMapper mapper = new JsonMapper();

        TaskState task = mapper.readValue(old, TaskState.class);

        assertThat(mapper.readTree(mapper.writeValueAsString(task))).isEqualTo(mapper.readTree(current));
    }
}
