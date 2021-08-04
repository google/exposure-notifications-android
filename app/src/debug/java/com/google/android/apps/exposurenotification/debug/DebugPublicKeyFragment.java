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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentDebugPublicKeyBinding;
import dagger.hilt.android.AndroidEntryPoint;
import org.jetbrains.annotations.NotNull;

/**
 * Fragment to display the debug public keys.
 */
@AndroidEntryPoint
public class DebugPublicKeyFragment extends Fragment {

  private FragmentDebugPublicKeyBinding binding;

  @Override
  public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentDebugPublicKeyBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NotNull View view, Bundle savedInstanceState) {
    DebugPublicKeyViewModel debugPublicKeyViewModel = new ViewModelProvider(this)
        .get(DebugPublicKeyViewModel.class);

    debugPublicKeyViewModel
        .getSnackbarLiveEvent()
        .observe(getViewLifecycleOwner(), this::maybeShowSnackbar);

    debugPublicKeyViewModel
        .getSigningKeyInfoLiveData()
        .observe(
            getViewLifecycleOwner(),
            keyInfo -> {
              setTextAndCopyAction(binding.keyfileSignaturePublicKey, keyInfo.publicKeyBase64());
              setTextAndCopyAction(binding.keyfileSignaturePackageName, keyInfo.packageName());
              setTextAndCopyAction(binding.keyfileSignatureId, keyInfo.keyId());
              setTextAndCopyAction(binding.keyfileSignatureVersion, keyInfo.keyVersion());
            });
  }

  private void setTextAndCopyAction(TextView view, String text) {
    view.setText(text);
    view.setOnClickListener(
        v -> {
          ClipboardManager clipboard =
              (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = ClipData.newPlainText(text, text);
          clipboard.setPrimaryClip(clip);
          SnackbarUtil.maybeShowRegularSnackbar(v,
              getString(
                  R.string.debug_snackbar_copied_text,
                  StringUtils.truncateWithEllipsis(text, 35)));
        });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void maybeShowSnackbar(String message) {
    SnackbarUtil.maybeShowRegularSnackbar(getView(), message);
  }
}
