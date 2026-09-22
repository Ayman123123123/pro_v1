package org.signal.glide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Log {

  private Log() {}

  public static void v(@NonNull String tag, @NonNull String message) {
    REDGlideCodecs.getLogProvider().v(tag, message);
  }

  public static void d(@NonNull String tag, @NonNull String message) {
    REDGlideCodecs.getLogProvider().d(tag, message);
  }

  public static void i(@NonNull String tag, @NonNull String message) {
    REDGlideCodecs.getLogProvider().i(tag, message);
  }

  public static void w(@NonNull String tag, @NonNull String message) {
    REDGlideCodecs.getLogProvider().w(tag, message);
  }

  public static void e(@NonNull String tag, @NonNull String message) {
    REDGlideCodecs.getLogProvider().e(tag, message, null);
  }

  public static void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
    REDGlideCodecs.getLogProvider().e(tag, message, throwable);
  }

  public interface Provider {
    void v(@NonNull String tag, @NonNull String message);
    void d(@NonNull String tag, @NonNull String message);
    void i(@NonNull String tag, @NonNull String message);
    void w(@NonNull String tag, @NonNull String message);
    void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable);

    Provider EMPTY = new Provider() {
      @Override
      public void v(@NonNull String tag, @NonNull String message) { }

      @Override
      public void d(@NonNull String tag, @NonNull String message) { }

      @Override
      public void i(@NonNull String tag, @NonNull String message) { }

      @Override
      public void w(@NonNull String tag, @NonNull String message) { }

      @Override
      public void e(@NonNull String tag, @NonNull String message, @NonNull Throwable throwable) { }
    };
  }
}
