package com.red.sovereign.crypto;

import org.whispersystems.signalservice.api.REDSessionLock;

import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of {@link REDSessionLock} that is backed by a {@link ReentrantLock}.
 */
public enum ReentrantSessionLock implements REDSessionLock {

  INSTANCE;

  private static final ReentrantLock LOCK = new ReentrantLock();

  @Override
  public Lock acquire() {
    LOCK.lock();
    return LOCK::unlock;
  }

  public boolean isHeldByCurrentThread() {
    return LOCK.isHeldByCurrentThread();
  }
}
