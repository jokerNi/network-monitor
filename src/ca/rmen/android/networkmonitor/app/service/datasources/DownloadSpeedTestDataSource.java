/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2014 Carmen Alvarez (c@rmen.ca)
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
package ca.rmen.android.networkmonitor.app.service.datasources;

import android.content.ContentValues;
import android.content.Context;

import ca.rmen.android.networkmonitor.Constants;
import ca.rmen.android.networkmonitor.R;
import ca.rmen.android.networkmonitor.app.speedtest.SpeedTestDownload;
import ca.rmen.android.networkmonitor.app.speedtest.SpeedTestDownloadConfig;
import ca.rmen.android.networkmonitor.app.speedtest.SpeedTestPreferences;
import ca.rmen.android.networkmonitor.app.speedtest.SpeedTestResult;
import ca.rmen.android.networkmonitor.app.speedtest.SpeedTestResult.SpeedTestStatus;
import ca.rmen.android.networkmonitor.provider.NetMonColumns;
import ca.rmen.android.networkmonitor.util.Log;

/**
 * Tests download speed by downloading a file.
 */
class DownloadSpeedTestDataSource implements NetMonDataSource {
    private static final String TAG = Constants.TAG + DownloadSpeedTestDataSource.class.getSimpleName();

    private SpeedTestPreferences mPreferences;
    private String mDisabledValue;

    @Override
    public void onCreate(Context context) {
        Log.v(TAG, "onCreate");
        mPreferences = SpeedTestPreferences.getInstance(context);
        mDisabledValue = context.getString(R.string.speed_test_disabled);
    }

    @Override
    public void onDestroy() {}

    @Override
    public ContentValues getContentValues() {
        Log.v(TAG, "getContentValues");
        ContentValues values = new ContentValues();

        if (mPreferences.isEnabled()) {
            SpeedTestDownloadConfig downloadConfig = mPreferences.getDownloadConfig();
            if (!downloadConfig.isValid()) return values;
            SpeedTestResult result = SpeedTestDownload.download(downloadConfig);
            mPreferences.setLastDownloadResult(result);
            if (result.status == SpeedTestStatus.SUCCESS) values.put(NetMonColumns.DOWNLOAD_SPEED, String.format("%.3f", result.getSpeedMbps()));
        } else {
            values.put(NetMonColumns.DOWNLOAD_SPEED, mDisabledValue);
        }
        return values;
    }
}
