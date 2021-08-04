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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareHistoryBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Optional;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ShareHistoryFragment extends BaseFragment {

  private static final int VIEW_FLIPPER_EMPTY_HISTORY = 0;
  private static final int VIEW_FLIPPER_HISTORY = 1;

  private FragmentShareHistoryBinding binding;
  private NotifyHomeViewModel notifyHomeViewModel;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareHistoryBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.share_history_activity_title);

    binding.home.setOnClickListener(v -> requireActivity().onBackPressed());

    notifyHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(NotifyHomeViewModel.class);

    ApplessDiagnosisEntityAdapter notifyViewAdapter =
        new ApplessDiagnosisEntityAdapter(diagnosis -> transitionToFragmentWithBackStack(
            ShareDiagnosisFragment.newInstanceForDiagnosis(requireContext(), diagnosis)),
            notifyHomeViewModel);
    notifyViewAdapter
        .setOnDiagnosisDeleteListener(position -> showDeleteDialog(notifyViewAdapter, position));

    final LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
    binding.notifyRecyclerView.setLayoutManager(layoutManager);
    binding.notifyRecyclerView.setAdapter(notifyViewAdapter);

    // Handle changes to the number of shared diagnoses
    notifyHomeViewModel
        .getAllDiagnosisEntityLiveData()
        .observe(
            getViewLifecycleOwner(),
            l -> {
              // Populate recyclerView
              notifyViewAdapter.setDiagnosisEntities(l);

              // Handle other cases depending on the number of diagnoses specific to this activity
              if (notifyViewAdapter.getItemCount() == 0) {
                binding.emptyHistoryFlipper.setDisplayedChild(VIEW_FLIPPER_EMPTY_HISTORY);
              } else {
                binding.emptyHistoryFlipper.setDisplayedChild(VIEW_FLIPPER_HISTORY);
              }

              // Check if we need to show the delete dialog.
              Optional<Integer> deleteDialogOpenAtPosition =
                  notifyHomeViewModel.getDeleteDialogOpenAtPosition();
              if (deleteDialogOpenAtPosition.isPresent()) {
                showDeleteDialog(notifyViewAdapter, deleteDialogOpenAtPosition.get());
              }
            });

    notifyHomeViewModel
        .getDeletedSingleLiveEvent()
        .observe(
            this,
            unused -> Toast.makeText(requireContext(), R.string.delete_test_result_confirmed,
                Toast.LENGTH_LONG).show());
  }

  private void showDeleteDialog(ApplessDiagnosisEntityAdapter adapter, int position) {
    notifyHomeViewModel.setDeleteDialogOpenAtPosition(position);
    new MaterialAlertDialogBuilder(requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.share_history_delete_test_result_title)
        .setMessage(getString(R.string.share_history_delete_test_result_detail,
            adapter.getDiagnosisEntityDate(getResources().getConfiguration().locale, position)))
        .setCancelable(true)
        .setNegativeButton(R.string.btn_cancel, (d, w) -> {
          d.cancel();
          notifyHomeViewModel.setDeleteDialogClosed();
          adapter.notifyDataSetChanged();
        })
        .setPositiveButton(
            R.string.btn_delete,
            (d, w) -> adapter.deleteDiagnosisEntity(position))
        .setOnDismissListener(d -> {
          notifyHomeViewModel.setDeleteDialogClosed();
          adapter.notifyDataSetChanged();
        })
        .setOnCancelListener(d -> {
          notifyHomeViewModel.setDeleteDialogClosed();
          adapter.notifyDataSetChanged();
        })
        .show();
  }

}
