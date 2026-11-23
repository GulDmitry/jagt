package dev.jagt.gradle

import dev.jagt.gradle.suitestats.Overrun
import dev.jagt.gradle.suitestats.Trend
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult

import java.lang.management.ManagementFactory
import java.time.Instant

abstract class SuiteStats implements BuildService<Params>, OperationCompletionListener, AutoCloseable {

    interface Params extends BuildServiceParameters {
        RegularFileProperty getLog()
        ListProperty<String> getTrended()
        ListProperty<String> getUntrended()
        Property<String> getCommand()
        Property<Boolean> getFresh()
        MapProperty<String, String> getCeilings()
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
        if (!Overrun.tracked(path, parameters.trended.get() + parameters.untrended.get(), parameters.ceilings.get().keySet())) {
            return
        }
        if (!(result instanceof TaskSuccessResult || result instanceof TaskFailureResult)
            || (result instanceof TaskSuccessResult && (result.upToDate || result.fromCache))) {
            return
        }
        runs << [at     : Instant.ofEpochMilli(result.endTime).toString(), task: path, command: parameters.command.get(),
                 seconds: (result.endTime - result.startTime).intdiv(1000), success: result instanceof TaskSuccessResult,
                 fresh  : parameters.fresh.get(), load: ManagementFactory.operatingSystemMXBean.systemLoadAverage]
    }

    @Override
    void close() {
        if (!runs) {
            return
        }
        def log = parameters.log.get().asFile
        def history = log.exists() ? log.readLines().findAll { it }.collect { new JsonSlurper().parseText(it) as Map } : []
        def power = power()
        runs.each { run ->
            run.power = power
            def ceiling = parameters.ceilings.get()[run.task]
            if (ceiling) {
                def minutes = ceiling.split('\\|')[1] as long
                run.ceiling = minutes
                Overrun.line(run.task, run.seconds as long, minutes, run.load as double, power).ifPresent {
                    run.overrun = true
                    println it
                }
            }
            if (run.success && run.task in parameters.trended.get()) {
                Trend.slower(history, run.task, run.command, run.seconds as long, run.fresh, parameters.trendRuns.get(),
                    parameters.slowdownFactor.get()).ifPresent { println it }
            }
        }
        log.withWriterAppend { writer -> runs.each { writer.writeLine(JsonOutput.toJson(it)) } }
    }

    private static String power() {
        try {
            def process = new ProcessBuilder('pmset', '-g', 'batt').redirectErrorStream(true).start()
            def report = process.inputStream.text
            process.waitFor()
            return Overrun.power(report)
        } catch (IOException ignored) {
            return 'unknown'
        }
    }
}
