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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.debug.TemporaryExposureKeyEncodingHelper.DecodeException;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.common.io.BaseEncoding;
import java.io.IOException;

/** Activity for scanning a QR code and returning the result. */
public final class QRScannerActivity extends AppCompatActivity {

  private static final String TAG = "QRScannerActivity";

  private static final int CAMERA_PERMISSION_REQUEST_CODE = 2;
  public static final String RESULT_KEY = "RESULT_KEY";

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();

  private SurfaceView scannerSurfaceView;
  private Button returnButton;
  private BarcodeDetector barcodeDetector;
  private CameraSource cameraSource;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_qr_code_scanner);
    scannerSurfaceView = findViewById(R.id.scanner);
    returnButton = findViewById(R.id.return_button);
    Button backButton = findViewById(R.id.back_button);
    backButton.setOnClickListener(v -> finish());
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        resumeCamera();
      } else {
        Toast.makeText(this, getString(R.string.qr_scan_permission_failed), Toast.LENGTH_LONG)
            .show();
        finish();
      }
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (cameraSource != null) {
      cameraSource.release();
    }
    if (barcodeDetector != null) {
      barcodeDetector.release();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      scannerSurfaceView.setVisibility(View.GONE);
      ActivityCompat.requestPermissions(
          this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    } else {
      resumeCamera();
    }
  }

  private void resumeCamera() {
    barcodeDetector =
        new BarcodeDetector.Builder(this).setBarcodeFormats(Barcode.ALL_FORMATS).build();
    if (!barcodeDetector.isOperational()) {
      Log.e(TAG, "Detector is not operational");
      Toast.makeText(this, getString(R.string.qr_scan_detector_not_operational), Toast.LENGTH_LONG)
          .show();
    }
    cameraSource =
        new CameraSource.Builder(this, barcodeDetector).setAutoFocusEnabled(true).build();

    scannerSurfaceView
        .getHolder()
        .addCallback(
            new SurfaceHolder.Callback() {
              @Override
              public void surfaceCreated(SurfaceHolder holder) {
                try {
                  cameraSource.start(scannerSurfaceView.getHolder());
                } catch (IOException e) {
                  Log.e(TAG, "Can't start camera", e);
                }
              }

              @Override
              public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

              @Override
              public void surfaceDestroyed(SurfaceHolder holder) {
                cameraSource.stop();
              }
            });
    scannerSurfaceView.setVisibility(View.VISIBLE);

    barcodeDetector.setProcessor(
        new Detector.Processor<Barcode>() {
          @Override
          public void release() {}

          @Override
          public void receiveDetections(Detector.Detections<Barcode> detections) {
            final SparseArray<Barcode> barcodes = detections.getDetectedItems();

            double lowestDist = Double.MAX_VALUE;

            for (int i = 0; i < barcodes.size(); i++) {
              int key = barcodes.keyAt(i);
              Barcode barcode = barcodes.get(key);
              if (barcode != null) {
                String rawValue = barcode.rawValue;
                TemporaryExposureKey temporaryExposureKey;
                try {
                  temporaryExposureKey = TemporaryExposureKeyEncodingHelper.decodeSingle(rawValue);
                } catch (DecodeException e) {
                  Log.e(TAG, "Not a valid QR", e);
                  continue;
                }

                Rect boundingBox = barcode.getBoundingBox();
                int x = (boundingBox.left + boundingBox.right) / 2;
                int y = (boundingBox.top + boundingBox.bottom) / 2;
                int cx = cameraSource.getPreviewSize().getWidth() / 2;
                int cy = cameraSource.getPreviewSize().getHeight() / 2;
                double dist = Math.sqrt(Math.pow(x - cx, 2.) + Math.pow(y - cy, 2.));

                if (dist < lowestDist) {
                  lowestDist = dist;
                  returnButton.setText(BASE16.encode(temporaryExposureKey.getKeyData()));
                  returnButton.setOnClickListener(
                      v -> {
                        Intent intent = new Intent();
                        intent.putExtra(RESULT_KEY, rawValue);
                        setResult(RESULT_OK, intent);
                        finish();
                      });
                }
              }
            }
          }
        });
  }
}
