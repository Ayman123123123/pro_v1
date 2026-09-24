package com.red.sovereign.push;

import android.content.Context;

import org.signal.core.util.logging.Log;
import com.red.sovereign.crypto.SecurityEvent;
import org.whispersystems.signalservice.api.REDServiceMessageSender;
import org.whispersystems.signalservice.api.push.REDServiceAddress;

public class SecurityEventListener implements REDServiceMessageSender.EventListener {

  private static final String TAG = Log.tag(SecurityEventListener.class);

  private final Context context;

  public SecurityEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(REDServiceAddress textSecureAddress) {
    SecurityEvent.broadcastSecurityUpdateEvent(context);
  }
}
