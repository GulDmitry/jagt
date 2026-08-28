package dev.jagt.orchestrator.prompteval;

import java.util.List;

/**
 * One operator request and the single command it must map onto, against the two tasks
 * {@link CommandMappingEvalTest} registers. Widening coverage is adding a row here.
 *
 * @param taskId    what the tasks list spells the answer as
 * @param taskAlias the other spelling of the same task, which the request may equally well be answered with
 */
record CommandMappingCase(String request, String command, String taskId, String taskAlias) {

    /**
     * Every row is a way a human actually asks, never the command word in a sentence: a request that already
     * contains "ship" proves the model can copy a word.
     */
    static List<CommandMappingCase> matrix() {
        return List.of(
                new CommandMappingCase("the login work is ready for someone to look at", "ship", "ABC-42", "a1"),
                new CommandMappingCase("let me read the login diff in my editor", "ide", "ABC-42", "a1"),
                new CommandMappingCase("did the pipeline say anything about the cart one", "sweep", "ABC-7", "a2"),
                new CommandMappingCase("get going on ABC-99", "do", "", ""),
                new CommandMappingCase("the checkout one is finished, tidy it away", "done", "ABC-15", "a3"),
                new CommandMappingCase("land the login change on the release branch", "deploy", "ABC-42", "a1"),
                new CommandMappingCase("take the checkout release back out", "revert", "ABC-15", "a3"),
                new CommandMappingCase("I want to talk to the agent working on the cart", "focus", "ABC-7", "a2"),
                new CommandMappingCase("the cart agent is stuck, give it a fresh session", "respawn", "ABC-7",
                        "a2"),
                new CommandMappingCase("what has changed against the release branch on login", "diff", "ABC-42",
                        "a1"),
                new CommandMappingCase("sort out that thing we discussed", "none", "", ""),
                new CommandMappingCase("send it for review", "none", "", ""));
    }

    @Override
    public String toString() {
        return request;
    }
}
