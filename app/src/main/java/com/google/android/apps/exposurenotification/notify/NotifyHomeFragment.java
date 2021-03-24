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

package com.google.android.apps.exposurenotification.notify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.edgecases.MainEdgeCaseFragment;
import com.google.android.apps.exposurenotification.databinding.FragmentNotifyHomeBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;

/**
 * Fragment for Notify tab on home screen
 */
@AndroidEntryPoint
public class NotifyHomeFragment extends Fragment {

  private static final String TAG = "NotifyHomeFragment";

  private static final int NOTIFY_BANNER_EDGE_CASE_CHILD = 0;
  private static final int NOTIFY_BANNER_SHARE_CHILD = 1;

  private static final int NOTIFY_VIEW_EDGE_CASE_CHILD = 0;
  private static final int NOTIFY_VIEW_SHARE_CHILD = 1;

  protected static final int DELETE_DIALOG_CLOSED = -1;

  private FragmentNotifyHomeBinding binding;
  private ExposureNotificationViewModel exposureNotificationViewModel;
  private NotifyHomeViewModel notifyHomeViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentNotifyHomeBinding.inflate(inflater, parent, false);
    View view = binding.getRoot();
    return view;
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    notifyHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(NotifyHomeViewModel.class);

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForState);

    binding.fragmentNotifyShareButton.setOnClickListener(
        v -> startActivity(ShareDiagnosisActivity.newIntentForAddFlow(requireContext())));

    DiagnosisEntityAdapter notifyViewAdapter =
        new DiagnosisEntityAdapter(
            diagnosis ->
                startActivity(
                    ShareDiagnosisActivity.newIntentForViewFlow(
                        requireContext(), diagnosis)), notifyHomeViewModel);
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    binding.notifyRecyclerView.setLayoutManager(layoutManager);
    binding.notifyRecyclerView.setAdapter(notifyViewAdapter);

    ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
        new SwipeDeleteCallback(requireContext(), notifyViewAdapter));
    itemTouchHelper.attachToRecyclerView(binding.notifyRecyclerView);

    notifyHomeViewModel
        .getAllDiagnosisEntityLiveData()
        .observe(
            getViewLifecycleOwner(),
            l -> {
              binding.diagnosisHistoryContainer.setVisibility(
                  l.isEmpty() ? View.GONE : View.VISIBLE);
              notifyViewAdapter.setDiagnosisEntities(l);
            });

    notifyHomeViewModel
        .getDeletedSingleLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> {
              if (getActivity() != null) {
                Toast.makeText(getContext(), R.string.delete_test_result_confirmed,
                    Toast.LENGTH_LONG).show();
              }
            });

    int deleteOpenPosition = notifyHomeViewModel.getDeleteOpenPosition();
    if (deleteOpenPosition > DELETE_DIALOG_CLOSED) {
      showDeleteDialog(notifyViewAdapter, deleteOpenPosition);
    }

    /*
     * Attach the edge-case logic as a fragment
     */
    FragmentManager childFragmentManager = getChildFragmentManager();
    if (childFragmentManager.findFragmentById(R.id.edge_case_fragment) == null) {
      Fragment childFragment = MainEdgeCaseFragment
          .newInstance(/* handleApiErrorLiveEvents= */ true, /* handleResolutions= */ false);
      childFragmentManager.beginTransaction()
          .replace(R.id.edge_case_fragment, childFragment)
          .commit();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Update UI state after Exposure Notifications client state changes
   */
  private void refreshUi() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Update UI to match Exposure Notifications state.
   *
   * @param state the {@link ExposureNotificationState} of the API
   */
  private void refreshUiForState(ExposureNotificationState state) {
    if (getView() == null) {
      return;
    }

    if (state == ExposureNotificationState.ENABLED) {
      binding.notifyHeaderBanner.setDisplayedChild(NOTIFY_BANNER_SHARE_CHILD);
      binding.notifyHeaderFlipper.setDisplayedChild(NOTIFY_VIEW_SHARE_CHILD);
    } else {
      binding.notifyHeaderBanner.setDisplayedChild(NOTIFY_BANNER_EDGE_CASE_CHILD);
      binding.notifyHeaderFlipper.setDisplayedChild(NOTIFY_VIEW_EDGE_CASE_CHILD);
    }
  }

  private void showDeleteDialog(DiagnosisEntityAdapter adapter, int position) {
    notifyHomeViewModel.setDeleteOpenPosition(position);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.delete_test_result_title)
        .setMessage(R.string.delete_test_result_detail)
        .setCancelable(true)
        .setNegativeButton(R.string.btn_cancel, (d, w) -> {
          d.cancel();
          notifyHomeViewModel.setDeleteOpenPosition(DELETE_DIALOG_CLOSED);
          adapter.notifyDataSetChanged();
        })
        .setPositiveButton(
            R.string.btn_delete,
            (d, w) -> adapter.deleteDiagnosisEntity(position))
        .setOnDismissListener(d -> {
          notifyHomeViewModel.setDeleteOpenPosition(DELETE_DIALOG_CLOSED);
          adapter.notifyDataSetChanged();
        })
        .setOnCancelListener(d -> {
          notifyHomeViewModel.setDeleteOpenPosition(DELETE_DIALOG_CLOSED);
          adapter.notifyDataSetChanged();
        })
        .show();
  }

  class SwipeDeleteCallback extends ItemTouchHelper.SimpleCallback {

    private final ColorDrawable swipeBackground;
    private final Drawable deleteIcon;
    private final Paint swipeClear;
    private final int deleteIconHeightPx;
    private final int deleteIconMarginPx;

    private final DiagnosisEntityAdapter adapter;

    public SwipeDeleteCallback(Context context, DiagnosisEntityAdapter adapter) {
      super(0, ItemTouchHelper.LEFT);
      this.adapter = adapter;

      // Set a red background.
      swipeBackground = new ColorDrawable();
      swipeBackground.setColor(ContextCompat.getColor(context, R.color.delete));

      // Set the Paint object used to clear the Canvas if the swipe is cancelled.
      swipeClear = new Paint();
      swipeClear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

      // Set the delete icon.
      deleteIcon = DrawableCompat.wrap(
          Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.ic_delete)));

      // Retrieve the delete icon's height and margin.
      deleteIconHeightPx = (int) getResources().getDimension(R.dimen.delete_icon_height);
      deleteIconMarginPx = (int) getResources().getDimension(R.dimen.delete_icon_margin);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder,
        @NonNull ViewHolder target) {
      return false; // We don't need to support moving items up/down
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
      int position = viewHolder.getAdapterPosition();
      showDeleteDialog(adapter, position);
    }

    @Override
    public void onChildDraw(
        @NonNull Canvas c, @NonNull RecyclerView recyclerView,
        @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
        int actionState, boolean isCurrentlyActive) {
      super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

      View itemView = viewHolder.itemView;
      View itemDivider = itemView.findViewById(R.id.horizontal_divider_view);

      // A flag to check if the swipe is cancelled.
      boolean isCancelled = dX == 0 && !isCurrentlyActive;
      if (isCancelled) {
        // The swipe is cancelled. Clear the canvas and make the divider visible.
        c.drawRect(itemView.getRight() + dX, (float) itemView.getTop(),
            (float) itemView.getRight(), (float) itemView.getBottom(), swipeClear);
        itemDivider.setVisibility(View.VISIBLE);
        return;
      }

      // Set a background with rounded corners for the itemView while swiping.
      itemView.setBackgroundResource(R.drawable.swipe_to_delete_item_view);
      // Hide the divider while swiping.
      itemDivider.setVisibility(View.GONE);

      // Draw the swipe background.
      int backgroundOffset =
          (int) getResources().getDimension(R.dimen.delete_item_view_corner_radius);
      swipeBackground.setBounds(itemView.getRight() - backgroundOffset + (int) dX,
          itemView.getTop(), itemView.getRight(), itemView.getBottom());
      swipeBackground.draw(c);

      // Draw the delete icon.
      int deleteIconTop = itemView.getTop() + (itemView.getHeight() - deleteIconHeightPx) / 2;
      int deleteIconBottom = deleteIconTop + deleteIconHeightPx;
      int deleteIconLeft = itemView.getRight() - deleteIconMarginPx - deleteIconHeightPx;
      int deleteIconRight = itemView.getRight() - deleteIconMarginPx;
      deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom);
      deleteIcon.draw(c);
    }
  }
}
