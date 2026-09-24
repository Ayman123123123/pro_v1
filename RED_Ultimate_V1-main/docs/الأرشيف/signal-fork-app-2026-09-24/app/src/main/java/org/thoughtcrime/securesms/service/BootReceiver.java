package com.red.sovereign.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobs.MessageFetchJob;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    AppDependencies.getJobManager().add(new MessageFetchJob());
  }
}
