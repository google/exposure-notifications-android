package com.google.android.apps.exposurenotification.common.time;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;

/**
 * Module providing a {@link Clock} for use in prod code, not in tests. Tests should use FakeClock.
 */
@Module
@InstallIn(ApplicationComponent.class)
public class RealTimeModule {

  @Provides
  public Clock provideSystemClock() {
    return new SystemClock();
  }
}
