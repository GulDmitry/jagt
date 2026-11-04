package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.task.TokenUsage;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The newest finished pipeline of a branch and the commit it ran on; {@code sha} is blank where the branch has none. */
record BranchTip(String sha, CommitPipeline pipeline) {

    private static final CommitPipeline NONE = new CommitPipeline("none", "");

    /** The pipeline where its commit is {@code commit} or has it behind it, as {@code holds} reads of what {@code ancestry} asks; else none. */
    Answer<CommitPipeline> since(HostCli cli, String commit, Supplier<Answer<String>> ancestry, Predicate<String> holds) {
        if (sha.isEmpty() || sha.startsWith(commit)) {
            return new Answer<>(Optional.of(pipeline), TokenUsage.NONE);
        }
        return cli.parsed(ancestry.get(), said -> holds.test(said) ? pipeline : NONE);
    }
}
