package com.red.sovereign.util;

import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;

import org.signal.core.util.ExceptionUtil;
import org.signal.core.util.logging.Log;
import com.red.sovereign.database.LogDatabase;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.keyvalue.REDStore;

import java.io.IOException;

import javax.net.ssl.SSLException;

import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException;

public class REDUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

  private static final String TAG = Log.tag(REDUncaughtExceptionHandler.class);

  private final Thread.UncaughtExceptionHandler originalHandler;

  public REDUncaughtExceptionHandler(@NonNull Thread.UncaughtExceptionHandler originalHandler) {
    this.originalHandler = originalHandler;
  }

  @Override
  public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
    // Seeing weird situations where SSLExceptions aren't being caught as IOExceptions
    if (e instanceof SSLException) {
      if (e instanceof IOException) {
        Log.w(TAG, "Uncaught SSLException! It *is* an IOException!", e);
      } else {
        Log.w(TAG, "Uncaught SSLException! It is *not* an IOException!", e);
      }
      return;
    }

    if (e instanceof SQLiteDatabaseCorruptException) {
      if (e.getMessage() != null && e.getMessage().contains("message_fts")) {
        Log.w(TAG, "FTS corrupted! Resetting FTS index.");
        REDDatabase.messageSearch().fullyResetTables();
      } else {
        Log.w(TAG, "Some non-FTS related corruption?");
      }
    }

    if (e instanceof SQLiteException && e.getMessage() != null) {
      if (e.getMessage().contains("invalid fts5 file format")) {
        Log.w(TAG, "FTS in invalid state! Resetting FTS index.");
        REDDatabase.messageSearch().fullyResetTables();
      } else if (e.getMessage().contains("no such table: message_fts")) {
        Log.w(TAG, "FTS table not found! Resetting FTS index.");
        REDDatabase.messageSearch().fullyResetTables();
      }
    }

    if (e instanceof OnErrorNotImplementedException && e.getCause() != null) {
      e = e.getCause();
    }

    String exceptionName = e.getClass().getCanonicalName();
    if (exceptionName == null) {
      exceptionName = e.getClass().getName();
    }

    Log.e(TAG, "", e, true);
    LogDatabase.getInstance(AppDependencies.getApplication()).crashes().saveCrash(System.currentTimeMillis(), exceptionName, e.getMessage(), ExceptionUtil.convertThrowableToString(e));
    REDStore.blockUntilAllWritesFinished();
    Log.blockUntilAllWritesFinished();
    AppDependencies.getJobManager().flush();
    originalHandler.uncaughtException(t, ExceptionUtil.joinStackTraceAndMessage(e));
  }
}
