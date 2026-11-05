package dev.jagt.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult

import java.time.Instant

abstract class SuiteStats implements BuildService<Params>, OperationCompletionListener, AutoCloseable {

    interface Params extends BuildServiceParameters {
        RegularFileProperty getLog()
        ListProperty<String> getTrended()
        ListProperty<String> getUntrended()
        Property<String> getCommand()
        Property<Integer> getTrendRuns()
        Property<BigDecimal> getSlowdownFactor()
    }

    protected final List<Map> runs = Collections.synchronizedList([])

    @Override
    void onFinish(FinishEvent event) {
        if (!(event instanceof TaskFinishEvent)) {
            return
        }
        def path = event.descriptor.taskPath
        def result = event.result
        if (!(path in parameters.trended.get() || path in parameters.untrended.get())) {
            return
        }
        if (!(result instanceof TaskSuccessResult || result instanceof TaskFailureResult)
            || (result instanceof TaskSuccessResult && (result.upToDate || result.fromCache))) {
            return
        }
        runs << [at     : Instant.ofEpochMilli(result.endTime).toString(), task: path, command: parameters.command.get(),
                 seconds: (result.endTime - result.startTime).intdiv(1000), success: result instanceof TaskSuccessResult]
    }

    @Override
    void close() {
        if (!runs) {
            return
        }
        def log = parameters.log.get().asFile
        def history = log.exists() ? log.readLines().findAll { it }.collect { new JsonSlurper().parseText(it) as Map } : []
        runs.findAll { it.success && it.task in parameters.trended.get() }.each { run ->
            def earlier = history.findAll { it.success && it.task == run.task && it.command == run.command }
                .takeRight(parameters.trendRuns.get())*.seconds.sort()
            if (!earlier) {
                return
            }
            def median = earlier[earlier.size().intdiv(2)]
            if (run.seconds > median * parameters.slowdownFactor.get()) {
                println "suiteStats: ${run.task} took ${run.seconds} s, its trend ${median} s over ${earlier.size()} runs"
            }
        }
        log.withWriterAppend { writer -> runs.each { writer.writeLine(JsonOutput.toJson(it)) } }
    }
}
