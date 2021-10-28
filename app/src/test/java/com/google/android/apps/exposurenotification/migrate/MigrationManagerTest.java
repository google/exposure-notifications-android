/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.android.apps.exposurenotification.migrate;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkManager;
import androidx.work.impl.utils.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.migrate.Migration.MigrationFailedException;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
public class MigrationManagerTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  private final Clock clock = new FakeClock();
  // Spy on the Context object as we want to stub some of its methods.
  private final Context context = spy(ApplicationProvider.getApplicationContext());

  @Mock
  PackageManager packageManager;

  @Mock
  Migration migration;

  MigrationManager migrationManager;

  WorkManager workManager;

  @Before
  public void setUp() {
    Configuration config = new Configuration.Builder()
        .setExecutor(new SynchronousExecutor())
        .build();
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context, config);
    workManager = WorkManager.getInstance(context);

    rules.hilt().inject();

    // Stub methods as needed.
    when(context.getPackageManager()).thenReturn(packageManager);
    when(migration.migrate(context)).thenReturn(Futures.immediateVoidFuture());
    // And the SUT.
    migrationManager = new MigrationManager(
        migration,
        MoreExecutors.newDirectExecutorService()
    );
  }

  @Test
  public void isMigrationEnabled_enableV1toENXMigrationIsFalse_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, false);

    assertThat(MigrationManager.isMigrationEnabled(context.getResources())).isFalse();
  }

  @Test
  public void isMigrationEnabled_enableV1toENXMigrationIsTrue_returnsTrue() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);

    assertThat(MigrationManager.isMigrationEnabled(context.getResources())).isTrue();
  }

  @Test
  public void isFirstInstall_notFirstInstall_returnsFalse() throws Exception {
    String packageName = context.getPackageName();
    when(packageManager.getPackageInfo(packageName, 0)).thenReturn(getAppUpdatePackageInfo());

    boolean isFirstInstall = migrationManager.isFirstInstall(packageManager, packageName);

    verify(packageManager).getPackageInfo(packageName, 0);
    assertThat(isFirstInstall).isFalse();
  }

  @Test
  public void isFirstInstall_firstInstall_returnsTrue() throws Exception {
    String packageName = context.getPackageName();
    when(packageManager.getPackageInfo(packageName, 0)).thenReturn(getAppFirstInstallPackageInfo());

    boolean isFirstInstall =
        migrationManager.isFirstInstall(packageManager, context.getPackageName());

    verify(packageManager).getPackageInfo(packageName, 0);
    assertThat(isFirstInstall).isTrue();
  }

  @Test
  public void shouldMigrate_enableV1toENXMigrationIsFalse_returnsFalse() throws Exception {
    // This is not the first app install.
    when(packageManager.getPackageInfo(context.getPackageName(), 0))
        .thenReturn(getAppUpdatePackageInfo());
    // But the migration is disabled.
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, false);

    boolean shouldMigrate = migrationManager.shouldMigrate(context);

    assertThat(shouldMigrate).isFalse();
  }

  @Test
  public void shouldMigrate_migrationAlreadyRun_returnsFalse() throws Exception {
    // Migration is enabled.
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    // And this is not the first app install.
    when(packageManager.getPackageInfo(context.getPackageName(), 0))
        .thenReturn(getAppUpdatePackageInfo());
    // But the migration has already run.
    when(migration.isMigrationRunOrNotNeeded()).thenReturn(true);

    boolean shouldMigrate = migrationManager.shouldMigrate(context);

    verify(migration).isMigrationRunOrNotNeeded();
    assertThat(shouldMigrate).isFalse();
  }

  @Test
  public void shouldMigrate_firstInstall_returnsFalse() throws Exception {
    String packageName = context.getPackageName();
    // Migration is enabled.
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    // And migration has not run yet.
    when(migration.isMigrationRunOrNotNeeded()).thenReturn(false);
    // But this is the first app install.
    when(packageManager.getPackageInfo(packageName, 0)).thenReturn(getAppFirstInstallPackageInfo());

    boolean shouldMigrate = migrationManager.shouldMigrate(context);

    verify(migration).isMigrationRunOrNotNeeded();
    verify(packageManager).getPackageInfo(packageName, 0);
    assertThat(shouldMigrate).isFalse();
  }

  @Test
  public void shouldMigrate_notFirstInstall_returnsTrue() throws Exception {
    setupConditionsForMigrationToRun();

    boolean shouldMigrate = migrationManager.shouldMigrate(context);

    verify(migration).isMigrationRunOrNotNeeded();
    verify(packageManager).getPackageInfo(context.getPackageName(), 0);
    assertThat(shouldMigrate).isTrue();
  }

  @Test
  public void maybeMigrate_enableV1toENXMigrationIsFalse_migrationNotRun() throws Exception {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, false);

    migrationManager.maybeMigrate(context).get();

    verify(migration, never()).migrate(context);
    verify(migration).markMigrationAsRunOrNotNeeded();
  }

  @Test
  public void maybeMigrate_migrationAlreadyRun_migrationNotRun() throws Exception {
    // Migration is enabled.
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    // And this is not the first app install.
    when(packageManager.getPackageInfo(context.getPackageName(), 0))
        .thenReturn(getAppUpdatePackageInfo());
    // But the migration has already run.
    when(migration.isMigrationRunOrNotNeeded()).thenReturn(true);

    migrationManager.maybeMigrate(context).get();

    verify(migration, never()).migrate(context);
  }

  @Test
  public void maybeMigrate_firstInstall_migrationNotRun() throws Exception {
    // Migration is enabled.
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    // And migration has not run yet.
    when(migration.isMigrationRunOrNotNeeded()).thenReturn(false);
    // But this is the first app install.
    when(packageManager.getPackageInfo(context.getPackageName(), 0))
        .thenReturn(getAppFirstInstallPackageInfo());

    migrationManager.maybeMigrate(context).get();

    verify(migration, never()).migrate(context);
    verify(migration).markMigrationAsRunOrNotNeeded();
  }

  @Test
  public void maybeMigrate_notFirstInstallAndMigrationSucceeds_migrationRunsAndMarkedAsRun()
      throws Exception {
    setupConditionsForMigrationToRun();

    migrationManager.maybeMigrate(context).get();

    verify(migration).migrate(context);
    verify(migration).markMigrationAsRunOrNotNeeded();
  }

  @Test
  public void maybeMigrate_notFirstInstallAndMigrationFails_throwsExceptionAndMigrationMarkedAsRun()
      throws Exception {
    setupConditionsForMigrationToRun();
    // But migration fails.
    when(migration.migrate(context))
        .thenReturn(Futures.immediateFailedFuture(new MigrationFailedException()));

    ThrowingRunnable execute = () -> migrationManager.maybeMigrate(context).get();

    Exception thrownException = assertThrows(ExecutionException.class, execute);
    assertThat(thrownException.getCause()).isInstanceOf(MigrationFailedException.class);
    verify(migration).migrate(context);
    verify(migration).markMigrationAsRunOrNotNeeded();
  }

  private void setupConditionsForMigrationToRun() throws Exception {
    String packageName = context.getPackageName();
    // Migration is enabled.
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    // And migration has not run yet.
    when(migration.isMigrationRunOrNotNeeded()).thenReturn(false);
    // And this is not the first app install.
    when(packageManager.getPackageInfo(packageName, 0)).thenReturn(getAppUpdatePackageInfo());
  }

  private PackageInfo getAppFirstInstallPackageInfo() {
    Instant now = clock.now();
    PackageInfo packageInfo = new PackageInfo();
    packageInfo.firstInstallTime = now.toEpochMilli();
    packageInfo.lastUpdateTime = now.toEpochMilli();
    return packageInfo;
  }

  private PackageInfo getAppUpdatePackageInfo() {
    PackageInfo packageInfo = new PackageInfo();
    packageInfo.firstInstallTime = clock.now().toEpochMilli();
    packageInfo.lastUpdateTime = clock.now().plus(Duration.ofDays(15)).toEpochMilli();
    return packageInfo;
  }

}
