package com.google.android.apps.exposurenotification.common.time;

import org.threeten.bp.Instant;

/**
 * Wrapper for times to use in place of {@link System#currentTimeMillis()} or {@link Instant#now()}
 */
public interface Clock {
  /**
   * Replacement for {@link System#currentTimeMillis()}
   */
  long currentTimeMillis();

  /**
   * Replacement for {@link Instant#now()}
   */
  Instant now();

}
