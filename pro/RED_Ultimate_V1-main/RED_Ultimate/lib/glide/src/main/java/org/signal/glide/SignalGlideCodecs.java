package org.signal.glide;

import androidx.annotation.NonNull;

public final class REDGlideCodecs {

  private static Log.Provider logProvider = Log.Provider.EMPTY;

  private REDGlideCodecs() {}

  public static void setLogProvider(@NonNull Log.Provider provider) {
    logProvider = provider;
  }

  public static @NonNull Log.Provider getLogProvider() {
    return logProvider;
  }
}
