package org.whispersystems.signalservice.internal.push.http;

/**
 * Used to communicate to observers whether or not something is canceled.
 */
public interface CancelationRED {
  boolean isCanceled();
}
