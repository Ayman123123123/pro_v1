package com.red.sovereign.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.Util;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Constraint;
import com.red.sovereign.jobs.JobManagerFactories;

import java.util.Map;

final class LogSectionConstraints implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "CONSTRAINTS";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    StringBuilder                   output    = new StringBuilder();
    Map<String, Constraint.Factory> factories = JobManagerFactories.getConstraintFactories(AppDependencies.getApplication());
    int                             keyLength = factories.keySet().stream().map(String::length).max(Integer::compareTo).orElse(0);

    for (Map.Entry<String, Constraint.Factory> entry : factories.entrySet()) {
      output.append(Util.rightPad(entry.getKey(), keyLength)).append(": ").append(entry.getValue().create().isMet()).append("\n");
    }

    return output;
  }
}
