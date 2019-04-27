/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ea.sqrl.activites.ocr;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.view.ViewParent;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;

import java.util.HashMap;
import java.util.List;

public class OcrDetectorProcessor implements Detector.Processor<TextBlock>{
    private static final String TAG = OcrDetectorProcessor.class.getSimpleName();

    private static final String[] URL_MATCHES = {"https://", "http://", "https//", "http//"};

    private final Context mContext;
    private final String mFlavor;
    private final Integer mRepeatsRequired;
    private final Activity mActivity;
    private boolean mProcessResults = true;
    private String mFoundUrl;

    private HashMap<String, Integer> mFoundResults = new HashMap<String, Integer>();

    private GraphicOverlay<OcrGraphic> mGraphicOverlay;

    OcrDetectorProcessor(GraphicOverlay<OcrGraphic> ocrGraphicOverlay, String flavor, OcrCaptureActivity activity, Integer repeatsRequired, Context context) {
        mGraphicOverlay = ocrGraphicOverlay;
        mContext = context;
        mFlavor = flavor;
        mRepeatsRequired = repeatsRequired;
        mFoundUrl = null;
        mActivity = activity;
    }

    @Override
    public void release() {
        mGraphicOverlay.clear();
        ViewParent aView = mGraphicOverlay.getParent();
        Log.v(TAG, "parent view:" + aView.getClass().getName());
    }

    public String getResult() {
        return mFoundUrl;
    }

    @Override
    public void receiveDetections(Detector.Detections<TextBlock> detections) {
        mGraphicOverlay.clear();
        if (mProcessResults) {
            SparseArray<TextBlock> items = detections.getDetectedItems();
            for (int i = 0; i < items.size(); ++i){
                TextBlock item = items.valueAt(i);
                if (item != null && item.getValue() != null) {
                    if ("firefox".equals(mFlavor)) {
                        scanForAddressBar(item);
                    }
                }
                OcrGraphic graphic = new OcrGraphic(mGraphicOverlay, item);
                mGraphicOverlay.add(graphic);
            }
        }
    }

    private void scanForAddressBar(TextBlock item) {
        List<? extends Text> lines = item.getComponents();
        for (int j = 0; j < lines.size(); j++) {
            boolean willContinue = false;
            Text linea = lines.get(j);
            String foundText = linea.getValue();
            int startLoc = -1;
            for (String match: URL_MATCHES) {
                startLoc = foundText.indexOf(match);
                if (startLoc > -1) {
                    startLoc = startLoc + match.length();
                    break;
                }
            }
            if (foundText.length() < startLoc + 10) continue;
            int firstSlash = foundText.indexOf("/",startLoc + 10);

            if ((startLoc > -1) && firstSlash > -1 ) {
                String legitFormattedUrl = foundText.substring(startLoc, firstSlash);
                Log.v(TAG, "*******[" + legitFormattedUrl + "]************   from [" + foundText + "]");
                Integer foundCount = mFoundResults.get(legitFormattedUrl);
                if (foundCount == null) mFoundResults.put(legitFormattedUrl, 1);
                else mFoundResults.put(legitFormattedUrl, foundCount + 1);

                for (String url : mFoundResults.keySet()) {
                    Integer count = mFoundResults.get(url);
                    if ((count != null) && (count >= mRepeatsRequired)) {
                        // END THIS PROCESSOR AND RETURN THE URL
                        Log.v(TAG, "<<<<<<<<<<<<<<<< FOUND " + mRepeatsRequired + " SAME >>>>>>>>>>>>>>>>>>>>>>>>");
                        mFoundUrl = url;

                        mActivity.runOnUiThread(mActivity::onBackPressed);
                        mProcessResults = false;
                        break;
                    }
                }
            }
        }
    }
}
