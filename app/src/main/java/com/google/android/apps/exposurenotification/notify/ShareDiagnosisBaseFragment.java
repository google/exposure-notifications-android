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

package com.google.android.apps.exposurenotification.notify;

import android.os.Bundle;
import android.os.Parcel;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.NestedScrollView.OnScrollChangeListener;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CalendarConstraints.DateValidator;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/** Base {@link Fragment} for the fragments in the Share Diagnosis flow. */
@AndroidEntryPoint
public abstract class ShareDiagnosisBaseFragment extends BaseFragment {

  protected ShareDiagnosisViewModel shareDiagnosisViewModel;

  private Optional<Boolean> lastUpdateAtBottom = Optional.absent();

  @Inject
  Clock clock;

  @CallSuper
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    shareDiagnosisViewModel = new ViewModelProvider(getParentFragment())
        .get(ShareDiagnosisViewModel.class);

    if (shareDiagnosisViewModel.isCloseOpen()) {
      showCloseShareDiagnosisFlowAlertDialog();
    }
  }

  @Override
  public boolean onBackPressed() {
    if (!shareDiagnosisViewModel.backStepIfPossible()) {
      @Nullable ShareDiagnosisFragment parent = (ShareDiagnosisFragment) getParentFragment();
      boolean showCloseWarningDialog = parent != null && parent.isShowCloseWarningDialog();
      maybeCloseShareDiagnosisFlow(showCloseWarningDialog);
    }
    return true;
  }

  /**
   * Set up UI components to update the shadow depending on the scrolling.
   */
  protected void setupShadowAtBottom(NestedScrollView scroller, ViewGroup buttonContainer) {
    scroller.setOnScrollChangeListener(
        (OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
          if (scroller.getChildAt(0).getBottom()
              <= (scroller.getHeight() + scroller.getScrollY())) {
            updateShadowAtBottom(buttonContainer, true);
          } else {
            updateShadowAtBottom( buttonContainer, false);
          }
        });
    ViewTreeObserver observer = scroller.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(() -> {
      if (scroller.getMeasuredHeight() >= scroller.getChildAt(0).getHeight()) {
        // Not scrollable so set at bottom.
        updateShadowAtBottom(buttonContainer, true);
      }
    });
  }

  /**
   * Update the shadow depending on whether scrolling is at the bottom or not.
   */
  private void updateShadowAtBottom(ViewGroup buttonContainer, boolean atBottom) {
    if (lastUpdateAtBottom.isPresent() && lastUpdateAtBottom.get() == atBottom) {
      // Don't update if already at set.
      return;
    }
    lastUpdateAtBottom = Optional.of(atBottom);
    if (atBottom) {
      buttonContainer.setElevation(0F);
    } else {
      buttonContainer
          .setElevation(getResources().getDimension(R.dimen.bottom_button_container_elevation));
    }
  }


  /**
   * Attempts to close the "Share diagnosis" flow. If we need to show a confirmation dialog, we
   * first show the dialog. Otherwise, we close the flow immediately.
   *
   * @param showCloseWarningDialog whether we need to show a confirmation dialog upon exiting the
   *                               flow.
   */
  public void maybeCloseShareDiagnosisFlow(boolean showCloseWarningDialog) {
    if (showCloseWarningDialog) {
      showCloseShareDiagnosisFlowAlertDialog();
    } else {
      closeShareDiagnosisFlowImmediately();
    }
  }

  /**
   * Shows an alert dialog warning of closing the sharing flow.
   */
  protected void showCloseShareDiagnosisFlowAlertDialog() {
    shareDiagnosisViewModel.setCloseOpen(true);
    new MaterialAlertDialogBuilder(requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.share_close_title)
        .setMessage(R.string.share_close_detail)
        .setPositiveButton(R.string.btn_resume_later, (d, w) -> {
          shareDiagnosisViewModel.setCloseOpen(false);
          closeShareDiagnosisFlowImmediately();
        })
        .setNegativeButton(R.string.btn_cancel,
            (d, w) -> {
              shareDiagnosisViewModel.setCloseOpen(false);
              d.dismiss();
            })
        .setOnCancelListener(d -> shareDiagnosisViewModel.setCloseOpen(false))
        .show();
  }

  /**
   * Closes the sharing flow immediately.
   */
  protected void closeShareDiagnosisFlowImmediately() {
    if (!getParentFragment().getParentFragmentManager().popBackStackImmediate()) {
      getParentFragment().requireActivity().finish();
    }
  }

  /**
   * Shows an alert dialog warning of deleting a given diagnosis.
   */
  protected void showDeleteDiagnosisAlertDialog(DiagnosisEntity diagnosis) {
    shareDiagnosisViewModel.setDeleteOpen(true);
    new MaterialAlertDialogBuilder(requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.delete_test_result_title)
        .setCancelable(true)
        .setPositiveButton(
            R.string.btn_delete,
            (d, w) -> {
              shareDiagnosisViewModel.setDeleteOpen(false);
              shareDiagnosisViewModel.deleteEntity(diagnosis);
            })
        .setNegativeButton(R.string.btn_cancel,
            (d, w) -> shareDiagnosisViewModel.setDeleteOpen(false))
        .setOnDismissListener(d -> shareDiagnosisViewModel.setDeleteOpen(false))
        .setOnCancelListener(d -> shareDiagnosisViewModel.setDeleteOpen(false))
        .show();
  }

  /**
   * Creates a material Date Picker.
   *
   * @param defaultDateSelection the default {@link Instant} date selection for the date picker
   * @return material date picker object.
   */
  protected MaterialDatePicker<Long> createMaterialDatePicker(Instant defaultDateSelection) {
    return MaterialDatePicker.Builder.datePicker()
        .setCalendarConstraints(
            new CalendarConstraints.Builder()
                .setEnd(System.currentTimeMillis())
                .setValidator(symptomOnsetOrTestDateValidator)
                .build())
        .setSelection(defaultDateSelection.toEpochMilli())
        .build();
  }

  @SuppressWarnings("unchecked")
  protected MaterialDatePicker<Long> findMaterialDatePicker(String datePickerTag) {
    Fragment datePickerFragment = getParentFragmentManager().findFragmentByTag(datePickerTag);
    if (datePickerFragment == null) {
      return null;
    }
    return (MaterialDatePicker<Long>) datePickerFragment;
  }

  /**
   * Shows relevant invalid date snackbar if the symptom onset date or test date user entered is not
   * within the last 14 days.
   */
  protected void maybeShowInvalidDateSnackbar(String dateStr) {
    if (!TextUtils.isEmpty(dateStr) && !isValidDate(
        dateStr, dateInMillis -> DiagnosisEntityHelper.isNotInFuture(clock, dateInMillis))) {
      SnackbarUtil.maybeShowRegularSnackbar(
          getView(), getString(R.string.input_error_onset_date_future));
    } else if (!TextUtils.isEmpty(dateStr) && !isValidDate(
        dateStr, dateInMillis -> DiagnosisEntityHelper.isWithinLast14Days(clock, dateInMillis))) {
      SnackbarUtil.maybeShowRegularSnackbar(
          getView(), getString(R.string.input_error_onset_date_past, "14"));
    }
  }

  /**
   * Checks if the given date is a valid date for either a symptoms onset date or a test date.
   *
   * <p> For either of these dates to be valid they must occur within the last 14 days.
   */
  protected boolean isValidDate(String dateStr, Function<Long, Boolean> dateValidationFn) {
    if (dateStr.isEmpty()) {
      return false;
    }
    try {
      long dateInMillis = LocalDate.parse(dateStr, getDateTimeFormatter())
          .atStartOfDay(ZoneOffset.UTC)
          .toInstant()
          .toEpochMilli();
      return dateValidationFn.apply(dateInMillis);
    } catch (RuntimeException e) {
      return false;
    }
  }

  protected DateTimeFormatter getDateTimeFormatter() {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(getResources().getConfiguration().locale);
  }

  protected final DateValidator symptomOnsetOrTestDateValidator =
      new DateValidator() {
        @Override
        public boolean isValid(long date) {
          return DiagnosisEntityHelper.isWithinLast14Days(clock, date);
        }

        @Override
        public int describeContents() {
          // Return no-op value. This validator has no state to describe
          return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
          // No-op. This validator has no state to parcelize
        }
      };
}
