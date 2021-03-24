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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.debug.TemporaryExposureKeyAdapter.TemporaryExposureKeyViewHolder;
import com.google.android.apps.exposurenotification.debug.TemporaryExposureKeyEncodingHelper.EncodeException;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.io.BaseEncoding;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import java.util.List;
import java.util.Locale;

/** Adapter for displaying keys in {@link KeysMatchingFragment}. */
class TemporaryExposureKeyAdapter extends RecyclerView.Adapter<TemporaryExposureKeyViewHolder> {

  private static final String TAG = "ViewKeysAdapter";

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();

  private List<TemporaryExposureKey> temporaryExposureKeys = null;

  void setTemporaryExposureKeys(List<TemporaryExposureKey> temporaryExposureKeys) {
    this.temporaryExposureKeys = temporaryExposureKeys;
    notifyDataSetChanged();
  }

  List<TemporaryExposureKey> getTemporaryExposureKeys() {
    return temporaryExposureKeys;
  }

  @NonNull
  @Override
  public TemporaryExposureKeyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new TemporaryExposureKeyViewHolder(
        LayoutInflater.from(viewGroup.getContext())
            .inflate(R.layout.item_temporary_exposure_key_entity, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(
      @NonNull TemporaryExposureKeyViewHolder temporaryExposureKeyViewHolder, int i) {
    temporaryExposureKeyViewHolder.bind(temporaryExposureKeys.get(i));
  }

  @Override
  public int getItemCount() {
    if (temporaryExposureKeys == null) {
      return 0;
    }
    return temporaryExposureKeys.size();
  }

  static class TemporaryExposureKeyViewHolder extends RecyclerView.ViewHolder {

    private static final long INTERVAL_TIME_MILLIS = 10 * 60 * 1000L;

    private final View view;
    private final TextView date;
    private final TextView key;
    private final TextView rollingPeriod;
    private final TextView intervalNumber;
    private final TextView transmissionRiskLevel;
    private final ImageView qrCode;

    TemporaryExposureKeyViewHolder(@NonNull View view) {
      super(view);
      this.view = view;
      date = view.findViewById(R.id.temporary_exposure_key_date);
      key = view.findViewById(R.id.temporary_exposure_key_key);
      intervalNumber = view.findViewById(R.id.temporary_exposure_key_interval_number);
      rollingPeriod = view.findViewById(R.id.temporary_exposure_key_rolling_period);
      transmissionRiskLevel = view.findViewById(R.id.temporary_exposure_key_risk_level);
      qrCode = view.findViewById(R.id.qr_code);
    }

    void bind(final TemporaryExposureKey entity) {
      String singleEncoding = "";
      try {
        singleEncoding = TemporaryExposureKeyEncodingHelper.encodeSingle(entity);
      } catch (EncodeException e) {
        Log.e(TAG, "Error encoding", e);
      }
      try {
        qrCode.setImageBitmap(encodeAsQRCode(singleEncoding));
      } catch (WriterException e) {
        Log.d(TAG, "WriterException making QR code", e);
      }

      date.setText(
          date.getResources()
              .getString(
                  R.string.debug_matching_view_item_date,
                  StringUtils.epochTimestampToMediumUTCDateString(
                      entity.getRollingStartIntervalNumber() * INTERVAL_TIME_MILLIS,
                      Locale.ENGLISH)));

      String keyHex = BASE16.encode(entity.getKeyData());
      key.setText(key.getResources().getString(R.string.debug_matching_view_item_key, keyHex));

      intervalNumber.setText(
          intervalNumber
              .getResources()
              .getString(
                  R.string.debug_matching_view_item_interval_number,
                  Integer.toString(entity.getRollingStartIntervalNumber())));

      rollingPeriod.setText(
          rollingPeriod
              .getResources()
              .getString(
                  R.string.debug_matching_view_item_rolling_period,
                  Integer.toString(entity.getRollingPeriod())));

      transmissionRiskLevel.setText(
          transmissionRiskLevel
              .getResources()
              .getString(
                  R.string.debug_matching_view_item_transmission_risk,
                  Integer.toString(entity.getTransmissionRiskLevel())));

      view.setOnClickListener(
          v -> {
            if (!TextUtils.isEmpty(keyHex)) {
              ClipboardManager clipboard =
                  (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);

              ClipData clip = ClipData.newPlainText(keyHex, keyHex);
              clipboard.setPrimaryClip(clip);
              Snackbar.make(view, R.string.debug_matching_view_share_success, Snackbar.LENGTH_LONG)
                  .show();
            }
          });
    }
  }

  private static Bitmap encodeAsQRCode(String data) throws WriterException {
    BitMatrix result;
    result = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 160, 160, null);
    int width = result.getWidth();
    int height = result.getHeight();
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++) {
      int offset = y * width;
      for (int x = 0; x < width; x++) {
        pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
      }
    }
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    return bitmap;
  }
}
