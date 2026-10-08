package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TicketFacts;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The one place a task is started. It owns the decisions launching needs and nothing about how the request arrived.
 */
@Service
public class TaskLauncher {

    private final ConfigService configService;
    private final TaskLaunches launches;
    private final TaskResume resumes;

    public TaskLauncher(ConfigService configService, TaskLaunches launches, TaskResume resumes) {
        this.configService = configService;
        this.launches = launches;
        this.resumes = resumes;
    }

    /**
     * Spins up a task for {@code ref}, an issue key or a URL to it in any tracker, or for the words a line
     * opening on a project key carries instead. Throws {@link IllegalArgumentException} when the request itself
     * is unusable. NO TASK NAMING AN ITEM IS CREATED WITHOUT THAT ITEM'S OWN FACTS: a later read cannot tell an
     * item that has no link from one that was never reached.
     */
    public Launched launchLine(String line) {
        return launch(LaunchRequest.ofLine(line, configService.load().projects().keySet()));
    }

    public Launched launch(LaunchRequest request) {
        if (request.ref() != null) {
            // An unknown project is settled before the read, not after paying for one.
            return launches.ticket(request, request.project() != null ? resolveProjects(request.project()) : null);
        }
        if (request.notes() == null || request.notes().isBlank()) {
            return Launched.refused("error: no ticket and nothing to do — say what the task is: "
                    + LaunchRequest.OWN_GRAMMAR);
        }
        return launches.written(request, resolveProjects(request.project()));
    }

    /** {@code read} is the item's facts already read and paid for, as intake reads them. */
    public Launched launch(LaunchRequest request, Answer<TicketFacts> read) {
        return launches.ticket(request, request.project() != null ? resolveProjects(request.project()) : null,
                read);
    }

    public Launched resume(String reviewRequestUrl) {
        return resumes.resume(reviewRequestUrl);
    }

    /** In the order given, the FIRST being where the agent's session runs; or the only project configured. */
    public List<String> resolveProjects(String project) {
        Set<String> keys = configService.load().projects().keySet();
        if (project != null && !project.isBlank()) {
            List<String> named = Arrays.stream(project.split(",")).map(String::strip)
                    .filter(key -> !key.isEmpty()).distinct().toList();
            List<String> unknown = named.stream().filter(key -> !keys.contains(key)).toList();
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("unknown project " + unknown + ". Configured: " + keys);
            }
            if (named.isEmpty()) {
                throw new IllegalArgumentException("no project named. Configured: " + keys);
            }
            return named;
        }
        if (keys.size() == 1) {
            return List.of(keys.iterator().next());
        }
        throw new IllegalArgumentException("multiple projects " + keys + " — specify one");
    }
}
