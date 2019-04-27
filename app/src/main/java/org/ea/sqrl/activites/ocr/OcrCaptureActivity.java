package org.ea.sqrl.activites.ocr;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.text.TextRecognizer;

import org.ea.sqrl.R;

import java.io.IOException;
import java.util.Date;

public final class OcrCaptureActivity extends AppCompatActivity {
    private static final String TAG = "OcrCaptureActivity";

    public static final int OCR_REQUEST = 42;
    public static final String QR_CODE_DOMAIN_PASSTHROUGH = "QR_CODE_DOMAIN_PASSTHROUGH";
    public static final String OCR_DOMAIN_RESULT = "OCR_DOMAIN_RESULT";

    // Intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // Permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // Constants used to pass extra data in the intent
    public static final String AutoFocus = "AutoFocus";
    public static final String UseFlash = "UseFlash";

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<OcrGraphic> mGraphicOverlay;

    private String mFlavor;
    private String mQrDomain = "";
    private OcrDetectorProcessor mDetectorProcessor;
    private Long mTimeStarted = -1L;

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_ocr_capture);
        mFlavor = getIntent().getStringExtra("flavor");
        mQrDomain = getIntent().getStringExtra(QR_CODE_DOMAIN_PASSTHROUGH);

        mPreview = findViewById(R.id.preview);
        mGraphicOverlay = findViewById(R.id.graphicOverlay);

        createCameraSource(true, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("m", mFlavor);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mFlavor = savedInstanceState.getString("m");
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the ocr detector to detect small text samples
     * at long distances.
     *
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
        Context context = getApplicationContext();

        // Create the TextRecognizer
        TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();

        // Set the TextRecognizer's Processor.
        Integer repeatedReadCount = 5; // TODO: Figure out how many we need to have enough confidence.
        mDetectorProcessor = new OcrDetectorProcessor(mGraphicOverlay, mFlavor, this, repeatedReadCount, context);
        textRecognizer.setProcessor(mDetectorProcessor);

        // Check if the TextRecognizer is operational.
        if (!textRecognizer.isOperational()) {
            Log.v(TAG, "Detector dependencies are not yet available.");

            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;
            if (hasLowStorage) {
                //Toast.makeText(this, R.string.low_internal_storage_view_title, Toast.LENGTH_LONG).show();
                //Log.w(TAG, getString(R.string.low_internal_storage_view_title));
            }
        }

        // Create the mCameraSource using the TextRecognizer.
        mCameraSource =
                new CameraSource.Builder(getApplicationContext(), textRecognizer)
                        .setFacing(CameraSource.CAMERA_FACING_BACK)
                        .setRequestedPreviewSize(1280, 1024)
                        .setRequestedFps(15.0f)
                        .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                        .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null)
                        .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        Log.v(TAG, "The process took " + ((new Date().getTime() - mTimeStarted)/1000) + " seconds.");
        if (mDetectorProcessor == null ) {
            setResult(RESULT_CANCELED, null);
            finish();
            super.onBackPressed();
        }
        String ocr_domain_result = mDetectorProcessor.getResult();
        Log.v(TAG, "about to finish OCR.  Setting extras.");
        Intent data = new Intent();
        data.putExtra(OCR_DOMAIN_RESULT, ocr_domain_result);
        data.putExtra(QR_CODE_DOMAIN_PASSTHROUGH, mQrDomain);
        setResult(RESULT_OK, data);
        finish();
        super.onBackPressed();
    }

    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =  GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
                mTimeStarted = new Date().getTime();
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

}
