/*
 * Copyright 2020 Google LLC
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

package com.google.android.apps.exposurenotification.debug;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCodeCreateFailureException;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCodeCreateServerFailureException;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCodeParseResponseFailureException;
import com.google.android.apps.exposurenotification.keydownload.DownloadUriPair;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.HomeDownloadUriPair;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.UploadUri;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.storage.CountryRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Instant;
import org.threeten.bp.ZonedDateTime;

/**
 * View model for the {@link DebugActivity}.
 */
public class DebugViewModel extends ViewModel {

  private static final String TAG = "DebugViewModel";
  private static final Pattern DEFAULT_URI_PATTERN = Pattern.compile(".*example\\.com.*");
  private static final Splitter COMMA_SPLITER = Splitter.on(",");

  private static SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();
  private static MutableLiveData<NetworkMode> keySharingNetworkModeLiveData =
      new MutableLiveData<>(NetworkMode.DISABLED);
  private static MutableLiveData<NetworkMode> verificationNetworkModeLiveData =
      new MutableLiveData<>(NetworkMode.DISABLED);
  private final LiveData<List<WorkInfo>> provideDiagnosisKeysWorkLiveData;
  private static MutableLiveData<VerificationCode> verificationCodeLiveData =
      new MutableLiveData<>();
  private final MutableLiveData<ZonedDateTime> symptomOnSetDateLiveData = new MutableLiveData<>();

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final CountryRepository countryRepository;
  private final VerificationCodeCreator codeCreator;
  private final WorkManager workManager;
  private final Resources resources;
  private final DownloadUriPair homeDownloadUris;
  private final Uri uploadUri;
  private final ExecutorService lightweightExecutor;

  @ViewModelInject
  public DebugViewModel(
      @ApplicationContext Context context,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      CountryRepository countryRepository,
      RequestQueueWrapper requestQueueWrapper,
      WorkManager workManager,
      @HomeDownloadUriPair DownloadUriPair homeDownloadUris,
      @UploadUri Uri uploadUri,
      @LightweightExecutor ExecutorService lightweightExecutor) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.countryRepository = countryRepository;
    this.workManager = workManager;
    this.homeDownloadUris = homeDownloadUris;
    this.uploadUri = uploadUri;
    this.lightweightExecutor = lightweightExecutor;
    codeCreator = new VerificationCodeCreator(context, requestQueueWrapper);
    resources = context.getResources();

    provideDiagnosisKeysWorkLiveData =
        workManager.getWorkInfosForUniqueWorkLiveData(ProvideDiagnosisKeysWorker.WORKER_NAME);
  }

  public LiveData<List<WorkInfo>> getProvideDiagnosisKeysWorkLiveData() {
    return provideDiagnosisKeysWorkLiveData;
  }

  public SingleLiveEvent<String> getSnackbarSingleLiveEvent() {
    return snackbarLiveEvent;
  }

  public LiveData<NetworkMode> getKeySharingNetworkModeLiveData() {
    return keySharingNetworkModeLiveData;
  }

  public LiveData<VerificationCode> getVerificationCodeLiveData() {
    return verificationCodeLiveData;
  }

  public boolean hasDefaultUris() {
    return DEFAULT_URI_PATTERN.matcher(homeDownloadUris.indexUri().toString()).matches()
        || DEFAULT_URI_PATTERN.matcher(uploadUri.toString()).matches()
        || DEFAULT_URI_PATTERN.matcher(
        resources.getString(R.string.enx_adminVerificationCreateCode)).matches();
  }

  public void createVerificationCode(String testTypeStr) {
    ZonedDateTime symptomOnsetDate = symptomOnSetDateLiveData.getValue();
    Futures.addCallback(
        codeCreator.create(
            symptomOnsetDate != null ? symptomOnsetDate.toLocalDate() : null,
            convertTestTypeStrToServerValue(testTypeStr)),
        new FutureCallback<VerificationCode>() {
          @Override
          public void onSuccess(@NullableDecl VerificationCode result) {
            verificationCodeLiveData.postValue(result);
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            Log.e(TAG, "Failed to create a verification code: " + t.getMessage(), t);
            String snackbarErrorMessage = t.getMessage();
            if (t instanceof VerificationCodeCreateServerFailureException) {
              snackbarErrorMessage =
                  resources.getString(
                      R.string.debug_snackbar_verification_server_error_msg);
            } else if (t instanceof VerificationCodeParseResponseFailureException) {
              snackbarErrorMessage =
                  resources.getString(
                      R.string.debug_snackbar_failed_to_parse_verification_code_response_msg);
            } else if (t instanceof VerificationCodeCreateFailureException) {
              snackbarErrorMessage = t.getMessage();
            }
            snackbarLiveEvent.postValue(snackbarErrorMessage);
            verificationCodeLiveData.postValue(VerificationCode.EMPTY);
          }
        },
        lightweightExecutor);
  }

  @NonNull
  public LiveData<ZonedDateTime> getSymptomOnSetDateLiveData() {
    return Transformations.distinctUntilChanged(symptomOnSetDateLiveData);
  }

  public void onSymptomOnSetDateChanged(@NonNull ZonedDateTime timestamp) {
    if (Instant.now().isAfter(timestamp.toInstant())) {
      // If the value given is in the past we can use the value
      symptomOnSetDateLiveData.setValue(timestamp);
    }
  }

  /**
   * Triggers a one off provide keys job.
   */
  public void provideKeys() {
    workManager.enqueue(new OneTimeWorkRequest.Builder(ProvideDiagnosisKeysWorker.class).build());
  }

  public void markCountryCodesSeen(String countryCodesInput) {
    for (String countryCode : COMMA_SPLITER.split(countryCodesInput)) {
      if (countryCode.length() != 2) {
        snackbarLiveEvent
            .postValue(resources.getString(R.string.debug_roaming_country_code_invalid_message));
        return;
      }

      FluentFuture.from(countryRepository.markCountrySeenAsync(countryCode))
          .catching(Exception.class, e -> {
            snackbarLiveEvent.postValue(
                resources.getString(R.string.debug_roaming_country_code_database_error));
            Log.w(TAG, "Error updating country code database", e);
            return null;
          }, lightweightExecutor);
    }
  }

  public void clearCountryCodes() {
    FluentFuture.from(countryRepository.deleteObsoleteCountryCodesAsync(Instant.MAX))
        .catching(Exception.class, e -> {
          snackbarLiveEvent.postValue(
              resources.getString(R.string.debug_roaming_country_code_database_error));
          Log.w(TAG, "Error clearing country code database", e);
          return null;
        }, lightweightExecutor);
  }

  private String convertTestTypeStrToServerValue(String testTypeStr) {
    if (resources.getString(R.string.debug_test_type_confirmed).equals(testTypeStr)) {
      return VerificationCodeCreator.TEST_TYPE_CONFIRMED;
    } else if (resources.getString(R.string.debug_test_type_likely).equals(testTypeStr)) {
      return VerificationCodeCreator.TEST_TYPE_LIKELY;
    } else if (resources.getString(R.string.debug_test_type_negative).equals(testTypeStr)) {
      return VerificationCodeCreator.TEST_TYPE_NEGATIVE;
    } else {
      return null;
    }
  }
}
