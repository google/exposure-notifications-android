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

import androidx.annotation.AnyThread;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Instant;

/**
 * Abstracts database access to {@link VerificationCodeRequestDao} data source.
 */
public class VerificationCodeRequestRepository {

  private final VerificationCodeRequestDao verificationCodeRequestDao;

  @Inject
  VerificationCodeRequestRepository(ExposureNotificationDatabase exposureNotificationDatabase) {
    verificationCodeRequestDao = exposureNotificationDatabase.verificationCodeRequestDao();
  }

  /**
   * Return all stored requests.
   */
  @VisibleForTesting
  @WorkerThread
  public List<VerificationCodeRequestEntity> getAll() {
    return verificationCodeRequestDao.getAll();
  }

  /**
   * Return all stored nonces.
   */
  @VisibleForTesting
  @WorkerThread
  List<String> getAllNonces() {
    return verificationCodeRequestDao.getAllNonces();
  }

  /**
   * Store a given VerificationCodeRequestEntity in the database to capture timestamp of the request
   * and nonce generated for this request.
   */
  @AnyThread
  public ListenableFuture<Long> upsertAsync(VerificationCodeRequestEntity entity) {
    return verificationCodeRequestDao.upsert(entity);
  }

  /**
   * Set a given expiresAt timestamp for a given VerificationCodeRequestEntity (identified by its
   * id).
   *
   * @param id            ID for the request, whose expiresAtTime field needs to be updated
   * @param expiresAtTime a value with which to update the expiresAtTime field of a request
   */
  @WorkerThread
  @VisibleForTesting
  void setExpiresAtTimeForRequest(long id, Instant expiresAtTime) {
    verificationCodeRequestDao.setExpiresAtTime(id, expiresAtTime);
  }

  /**
   * Delete outdated VerificationCodeRequestEntities if any (i.e. those sent earlier than a given
   * threshold).
   *
   * @param earliestThreshold delete all the {@link VerificationCodeRequestEntity}s sent earlier
   *                          than this threshold value
   */
  @WorkerThread
  public void deleteOutdatedRequestsIfAny(Instant earliestThreshold) {
    verificationCodeRequestDao.deleteOlderThanThreshold(earliestThreshold);
  }


  /**
   * Reset nonces for all the expired VerificationCodeRequestEntities if any.
   *
   * <p>VerificationCodeRequestEntity effectively gets expired as soon as we pass its
   * {@code expiresAtTime} timestamp (which is the expiration time of a verification code issued
   * for this request).
   *
   * @param currentTime reset nonces for all {@link VerificationCodeRequestEntity}s with
   *                    expiresAtTime earlier that now
   */
  @WorkerThread
  public void resetNonceForExpiredRequestsIfAny(Instant currentTime) {
    verificationCodeRequestDao.resetNonceForExpiredRequests(currentTime);
  }

  /**
   * Retrieve all valid nonces with the latest expiring first (if any).
   *
   * <p>Nonce is valid if the verification code generated for this nonce's request has not expired
   * yet.
   *
   * @param currentTime retrieve nonces for all {@link VerificationCodeRequestEntity}s with
   *                    expiresAtTime later that this value
   */
  @AnyThread
  public ListenableFuture<List<String>> getValidNoncesWithLatestExpiringFirstIfAnyAsync(
      Instant currentTime) {
    return verificationCodeRequestDao.getAllValidNoncesWithLatestExpiringFirstAsync(currentTime);
  }

  /**
   * Retrieve a specified number of requests that were made later than a given threshold.
   *
   * @param earliestThreshold       requests made later than this value should be returned
   * @param numOfRequestsToRetrieve number of requests to retrieve
   * @return list of the recently created {@link VerificationCodeRequestEntity}s
   */
  @AnyThread
  public ListenableFuture<List<VerificationCodeRequestEntity>> getLastXRequestsNotOlderThanThresholdAsync(
      Instant earliestThreshold, int numOfRequestsToRetrieve) {
    return verificationCodeRequestDao
        .getLastXRequestsNotOlderThanThresholdAsync(earliestThreshold, numOfRequestsToRetrieve);
  }

  /**
   * Delete all stored requests.
   */
  @AnyThread
  public ListenableFuture<Void> deleteVerificationCodeRequestEntities() {
    return verificationCodeRequestDao.deleteAll();
  }

}
