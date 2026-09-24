package com.red.sovereign.push;

import android.content.Context;

import com.red.sovereign.R;
import org.signal.network.config.TrustStore;

import java.io.InputStream;

public class REDServiceTrustStore implements TrustStore {

  private final Context context;

  public REDServiceTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.whisper);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }
}
