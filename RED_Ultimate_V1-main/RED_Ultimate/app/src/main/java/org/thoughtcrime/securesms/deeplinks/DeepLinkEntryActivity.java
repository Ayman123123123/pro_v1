package com.red.sovereign.deeplinks;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.red.sovereign.MainActivity;
import com.red.sovereign.PassphraseRequiredActivity;

public class DeepLinkEntryActivity extends PassphraseRequiredActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    Intent intent = MainActivity.clearTop(this);
    Uri    data   = getIntent().getData();
    intent.setData(data);
    startActivity(intent);
  }
}
