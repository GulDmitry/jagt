package dev.jagt.gradle.checkscope;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/** Fails with every source set that lacks a check, read once every project is evaluated. */
public abstract class CheckScopeTask extends DefaultTask {

  @Input
  public abstract ListProperty<String> getGaps();

  @TaskAction
  void verify() {
    CheckScope.verify(getGaps().get());
  }
}
