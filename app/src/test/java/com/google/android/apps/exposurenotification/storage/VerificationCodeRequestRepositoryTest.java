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

package com.google.android.apps.exposurenotification.storage;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.collect.ImmutableList;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Tests for operations in {@link VerificationCodeRequestRepository} and the underlying DAO.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class VerificationCodeRequestRepositoryTest {

  // Number of requests to retrieve created in the last 30 minutes.
  private static final int THIRTY_MINUTES_NUM_OF_REQUESTS = 1;
  // Number of requests to retrieve created in the last 30 days.
  private static final int THIRTY_DAYS_NUM_OF_REQUESTS = 3;
  // How long verification code and, hence, a request created to get it are valid.
  private static final Duration VERIFICATION_CODE_DURATION_MIN = Duration.ofMinutes(15);

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  VerificationCodeRequestRepository verificationCodeRequestRepo;

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();

  @BindValue
  Clock clock = new FakeClock();

  private Instant now, thirtyMinutesThreshold, thirtyDaysThreshold;

  @Before
  public void setUp() {
    rules.hilt().inject();
    now = clock.now();
    thirtyMinutesThreshold = now.minus(Duration.ofMinutes(30));
    thirtyDaysThreshold = now.minus(Duration.ofDays(30));
  }

  @Test
  public void setExpiresAtTimeForRequest_updatesExpiresAtTime() throws Exception {
    // GIVEN
    Instant expiresAtTime = now.plus(Duration.ofMinutes(1));
    VerificationCodeRequestEntity request = VerificationCodeRequestEntity.newBuilder()
        .setRequestTime(now).setExpiresAtTime(null).setNonce("nonce")
        .build();
    long id = verificationCodeRequestRepo.upsertAsync(request).get();

    // WHEN
    verificationCodeRequestRepo.setExpiresAtTimeForRequest(id, expiresAtTime);
    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepo.getAll();

    // THEN
    assertThat(storedRequests).hasSize(1);
    VerificationCodeRequestEntity updatedRequest = storedRequests.get(0);
    assertThat(updatedRequest.getId()).isEqualTo(request.getId());
    assertThat(updatedRequest.getExpiresAtTime()).isEqualTo(expiresAtTime);
    assertThat(updatedRequest.getRequestTime()).isEqualTo(request.getRequestTime());
    assertThat(updatedRequest.getNonce()).isEqualTo(request.getNonce());
  }

  @Test
  public void deleteObsoleteRequestsIfAny_threshold30days() throws Exception {
    // GIVEN
    List<VerificationCodeRequestEntity> requests = getRequestEntities();
    List<VerificationCodeRequestEntity> nonObsoleteRequests =
        requests.subList(2, requests.size());
    for (VerificationCodeRequestEntity request : requests) {
      verificationCodeRequestRepo.upsertAsync(request).get();
    }

    // WHEN
    verificationCodeRequestRepo.deleteObsoleteRequestsIfAny(thirtyDaysThreshold);

    // THEN
    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepo.getAll();
    assertThat(storedRequests).containsExactlyElementsIn(nonObsoleteRequests);
  }

  @Test
  public void resetNonceForExpiredRequestsIfAny_resetsForAllWithExpiresAtTimeEarlierThanNow()
      throws Exception {
    // GIVEN
    List<VerificationCodeRequestEntity> requests = getRequestEntities();
    for (VerificationCodeRequestEntity request : requests) {
      verificationCodeRequestRepo.upsertAsync(request).get();
    }
    // Nonces for expired requests (expiresAtTime earlier than or equal to now).
    List<String> expiredNonces = requests.subList(0, requests.size() - 2)
        .stream()
        .map(VerificationCodeRequestEntity::getNonce)
        .collect(Collectors.toList());
    // Nonces for non-expired requests (expiresAtTime later than now).
    List<String> nonExpiredNonces = requests.subList(requests.size() - 2, requests.size())
        .stream()
        .map(VerificationCodeRequestEntity::getNonce)
        .collect(Collectors.toList());

    // WHEN
    verificationCodeRequestRepo.resetNonceForExpiredRequestsIfAny(clock.now());

    // THEN
    List<String> storedNonces = verificationCodeRequestRepo.getAllNonces();
    assertThat(storedNonces).containsNoneIn(expiredNonces);
    assertThat(storedNonces).containsExactlyElementsIn(nonExpiredNonces);
  }

  @Test
  public void getValidNoncesWithMostRecentFirstIfAnyAsync_returnsNoncesForNonExpiredRequests()
      throws Exception {
    // GIVEN
    List<VerificationCodeRequestEntity> requests = getRequestEntities();
    for (VerificationCodeRequestEntity request : requests) {
      verificationCodeRequestRepo.upsertAsync(request).get();
    }
    // Nonces for non-expired requests (expiresAtTime later than now). Sorted with
    // the-least-soon-to-expire nonce coming first.
    List<String> sortedNonExpiredNonces = requests.subList(requests.size() - 2, requests.size())
        .stream()
        .sorted((r1, r2) -> r2.getExpiresAtTime().compareTo(r1.getExpiresAtTime()))
        .map(VerificationCodeRequestEntity::getNonce)
        .collect(Collectors.toList());
    String mostRecentNonce = sortedNonExpiredNonces.get(0);

    // WHEN
    List<String> validNonces = verificationCodeRequestRepo
        .getValidNoncesWithLatestExpiringFirstIfAnyAsync(clock.now()).get();

    // THEN
    assertThat(validNonces).containsExactlyElementsIn(sortedNonExpiredNonces).inOrder();
    assertThat(validNonces.get(0)).isEqualTo(mostRecentNonce);
  }

  @Test
  public void getLastXRequestsNotOlderThanThresholdLiveData_threshold30minutes_oneRequest_returnsOneLatestRequest()
      throws Exception {
    // GIVEN
    List<VerificationCodeRequestEntity> requests = getRequestEntities();
    VerificationCodeRequestEntity expectedRequest = requests.get(requests.size() - 1);
    for (VerificationCodeRequestEntity request : requests) {
      verificationCodeRequestRepo.upsertAsync(request).get();
    }

    // WHEN
    List<VerificationCodeRequestEntity> retrievedRequests = verificationCodeRequestRepo
        .getLastXRequestsNotOlderThanThresholdAsync(
            thirtyMinutesThreshold, THIRTY_MINUTES_NUM_OF_REQUESTS)
        .get();

    // THEN
    assertThat(retrievedRequests).hasSize(THIRTY_MINUTES_NUM_OF_REQUESTS);
    assertThat(retrievedRequests.get(0)).isEqualTo(expectedRequest);
  }

  @Test
  public void getLastXRequestsNotOlderThanThresholdLiveData_threshold30Mins_returnsRequestMadeExactly30MinsAgo()
      throws Exception {
    // GIVEN
    VerificationCodeRequestEntity requestMadeExactly30MinsAgo =
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofMinutes(30)))
            .setExpiresAtTime(now.minus(Duration.ofMinutes(30)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce")
            .build();
    verificationCodeRequestRepo.upsertAsync(requestMadeExactly30MinsAgo).get();

    // WHEN
    List<VerificationCodeRequestEntity> retrievedRequests = verificationCodeRequestRepo
        .getLastXRequestsNotOlderThanThresholdAsync(
            thirtyMinutesThreshold, THIRTY_MINUTES_NUM_OF_REQUESTS)
        .get();

    // THEN
    assertThat(retrievedRequests).hasSize(1);
    assertThat(retrievedRequests.get(0)).isEqualTo(requestMadeExactly30MinsAgo);
  }

  @Test
  public void getLastXRequestsNotOlderThanThresholdLiveData_threshold30days_threeRequests_returnsThreeLatestRequests()
      throws Exception {
    // GIVEN
    List<VerificationCodeRequestEntity> requests = getRequestEntities();
    List<VerificationCodeRequestEntity> expectedRequests = requests
        .subList(requests.size() - THIRTY_DAYS_NUM_OF_REQUESTS, requests.size());
    for (VerificationCodeRequestEntity request : requests) {
      verificationCodeRequestRepo.upsertAsync(request).get();
    }

    // WHEN
    List<VerificationCodeRequestEntity> retrievedRequests = verificationCodeRequestRepo
        .getLastXRequestsNotOlderThanThresholdAsync(
            thirtyDaysThreshold, THIRTY_DAYS_NUM_OF_REQUESTS)
        .get();

    // THEN
    assertThat(retrievedRequests).hasSize(THIRTY_DAYS_NUM_OF_REQUESTS);
    assertThat(retrievedRequests).containsExactlyElementsIn(expectedRequests);
  }

  @Test
  public void getLastXRequestsNotOlderThanThresholdLiveData_threshold30Days_returnsRequestMadeExactly30DaysAgo()
      throws Exception {
    // GIVEN
    VerificationCodeRequestEntity requestMadeExactly30DaysAgo =
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofDays(30)))
            .setExpiresAtTime(now.minus(Duration.ofDays(30)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce")
            .build();
    verificationCodeRequestRepo.upsertAsync(requestMadeExactly30DaysAgo).get();

    // WHEN
    List<VerificationCodeRequestEntity> retrievedRequests = verificationCodeRequestRepo
        .getLastXRequestsNotOlderThanThresholdAsync(
            thirtyDaysThreshold, THIRTY_DAYS_NUM_OF_REQUESTS)
        .get();

    // THEN
    assertThat(retrievedRequests).hasSize(1);
    assertThat(retrievedRequests.get(0)).isEqualTo(requestMadeExactly30DaysAgo);
  }

  private List<VerificationCodeRequestEntity> getRequestEntities() {
    return ImmutableList.of(
        // Requests created in the last 45 days.
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofDays(45)))
            .setExpiresAtTime(now.minus(Duration.ofDays(45)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce-0")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofDays(31)))
            .setExpiresAtTime(now.minus(Duration.ofDays(31)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce-1")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofDays(16)))
            .setExpiresAtTime(now.minus(Duration.ofDays(16)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce-2")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofDays(15)))
            .setExpiresAtTime(now.minus(Duration.ofDays(15)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce-3")
            .build(),
        // Requests created in the last 31 minutes.
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofMinutes(31)))
            .setExpiresAtTime(now.minus(Duration.ofMinutes(31)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce-4")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now.minus(Duration.ofMinutes(1)))
            .setExpiresAtTime(now.minus(Duration.ofMinutes(1)).plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce-5")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(now)
            .setExpiresAtTime(now.plus(VERIFICATION_CODE_DURATION_MIN))
            .setNonce("nonce-6")
            .build()
    );
  }

}
