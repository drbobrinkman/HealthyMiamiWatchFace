/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.bobrinkman.healthymiamiwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Sample digital watch face with blinking colons and seconds. In ambient mode, the seconds are
 * replaced with an AM/PM indicator and the colons don't blink. On devices with low-bit ambient
 * mode, the text is drawn without anti-aliasing in ambient mode. On devices which require burn-in
 * protection, the hours are drawn in normal rather than bold. The time is drawn with less contrast
 * and without seconds in mute mode.
 */
public class DigitalWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "MiamiWatchFaceSrv";

    private Typeface BOLD_TYPEFACE = null;
    private Typeface NORMAL_TYPEFACE = null;

    private static final float WATCH_WIDTH = 320.0f;
    private static final float CIRCLE_WIDTH = WATCH_WIDTH/2.0f;
    private static final float WATCH_RADIUS = CIRCLE_WIDTH;
    private static final float CIRCLE_RADIUS = CIRCLE_WIDTH/2.0f;
    private static final float CIRCLE_OFFSET = (float) Math.sqrt(CIRCLE_RADIUS*CIRCLE_RADIUS/2.0f);
    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode.
     * 20 FPS seems to be sufficiently smooth looking
     */
    private static final long NORMAL_UPDATE_RATE_MS = 1000/20;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    private static final float[][] M_POINTS =
            {
                    //Outer perimeter
                    {0.0f,163.8f},
                    {24.6f,122.8f},
                    {29.2f,122.8f},
                    {29.2f,41.0f},
                    {24.8f,41.0f},
                    {0.0f,0.0f},
                    {72.8f,0.0f},
                    {108.5f,61.2f},
                    {144.1f,0.0f},
                    {217.0f,0.0f},
                    {191.8f,41.0f},
                    {187.5f,41.0f},
                    {187.5f,122.8f},
                    {192.0f,122.8f},
                    {217.0f,163.8f},
                    {110.0f,163.8f},
                    {134.7f,122.8f},
                    {139.1f,122.8f},
                    {139.1f,97.0f},
                    {108.5f,149.1f},
                    {77.4f,96.5f},
                    {77.4f,122.8f},
                    {81.9f,122.8f},
                    {106.5f,163.8f},

                    //Repeat first point of outer perimenter
                    {0.0f,163.8f},

                    //Inner perimeter
                    {20.3f, 155.2f},
                    {31.4f, 133.9f},
                    {40.5f, 133.9f},
                    {40.5f, 29.4f},
                    {31.4f, 29.4f},
                    {20.5f, 11.1f},
                    {65.9f, 11.1f},
                    {108.5f, 83.7f},
                    {151.3f, 11.1f},
                    {196.5f, 11.1f},
                    {185.6f, 29.4f},
                    {176.5f, 29.4f},
                    {176.5f, 133.9f},
                    {185.7f, 133.9f},
                    {196.5f, 155.2f},
                    {130.4f, 155.2f},
                    {141.5f, 133.9f},
                    {150.8f, 133.9f},
                    {150.8f, 54.3f},
                    {108.5f, 126.3f},
                    {66.4f, 54.3f},
                    {66.4f, 133.9f},
                    {75.4f, 133.9f},
                    {86.6f, 155.2f},
                    {20.3f, 155.2f}
            } ;
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements /*DataApi.DataListener,*/
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, SensorEventListener {

        /** Alpha value for drawing time when in mute mode. */
        static final int MUTE_ALPHA = 100;

        /** Alpha value for drawing time when not in mute mode. */
        static final int NORMAL_ALPHA = 255;

        static final int MSG_UPDATE_TIME = 0;

        /** How often {@link #mUpdateTimeHandler} ticks in milliseconds. */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        /** Handler to update the time periodically in interactive mode. */
    //TODO: Figure out how this ought to be handled
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(DigitalWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mAmbientBackgroundPaint;
        Paint mInteractiveBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mStepPaint;
        Paint mCirclePaint;
        Paint mCircleBorderPaint;
        DashPathEffect mAmbientDashEffect;

        Path mMPath;
        Paint mMPathPaint;
        Paint mMFillPaint;

        boolean mMute;
        Time mTime;

        int mInteractiveBackgroundColor = Color.argb(255, 196, 18, 48);
        int mInteractiveBackgroundColor2 = Color.argb(128, 196, 18, 48);
        int mInteractiveDigitsColor = Color.argb(255,255,255,255);
        int mInteractiveCircleColor = Color.argb(64,0,0,0);
        int mInteractiveCircleBorderColor = Color.argb(196,255,255,255);
        int mInteractiveMiamiMColor = Color.argb(255,255,255,255);

        SensorManager mSensorManager = null;
        int mStepCount = 0;
        int mMidnightStepCount = 0;
        int mCurDay = -1;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            if(BOLD_TYPEFACE == null) {
                BOLD_TYPEFACE = Typeface.createFromAsset(getAssets(), "Helvetica.ttf");
            }
            if(NORMAL_TYPEFACE == null) {
                NORMAL_TYPEFACE = Typeface.createFromAsset(getAssets(), "HelveticaLight.ttf");
            }

            setWatchFaceStyle(new WatchFaceStyle.Builder(DigitalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mAmbientBackgroundPaint = new Paint();
            mAmbientBackgroundPaint.setColor(Color.argb(255,0,0,0));
            mInteractiveBackgroundPaint = new Paint();
            mInteractiveBackgroundPaint.setShader(new RadialGradient(WATCH_RADIUS,WATCH_RADIUS,
                    WATCH_RADIUS,
                    mInteractiveBackgroundColor,mInteractiveBackgroundColor2,
                    Shader.TileMode.CLAMP));

            mCirclePaint = new Paint();
            mCirclePaint.setColor(mInteractiveCircleColor);
            mCirclePaint.setStyle(Paint.Style.FILL);
            mCirclePaint.setAntiAlias(true);

            mCircleBorderPaint = new Paint();
            mCircleBorderPaint.setColor(mInteractiveCircleBorderColor);
            mCircleBorderPaint.setStyle(Paint.Style.STROKE);
            mCircleBorderPaint.setAntiAlias(true);
            mCircleBorderPaint.setStrokeWidth(2.0f);

            mAmbientDashEffect = new DashPathEffect(new float[]{(2.0f),(7.0f)},0);

            mHourPaint = createTextPaint(mInteractiveDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveDigitsColor);
            mStepPaint  = createTextPaint(mInteractiveDigitsColor);

            mMPath = new Path();
            mMPath.moveTo((M_POINTS[0][0]),(M_POINTS[0][1]));
            for(int i=1;i<M_POINTS.length;i++){
                mMPath.lineTo((M_POINTS[i][0]),(M_POINTS[i][1]));
            }
            mMPath.close();
            mMPath.setFillType(Path.FillType.EVEN_ODD);

            //Used in ambient mode only
            mMPathPaint = new Paint();
            mMPathPaint.setColor(mInteractiveMiamiMColor);
            mMPathPaint.setStyle(Paint.Style.STROKE);
            mMPathPaint.setAntiAlias(false);
            mMPathPaint.setStrokeWidth(2.0f);

            //Used in interactive mode only
            mMFillPaint = new Paint();
            mMFillPaint.setColor(mInteractiveMiamiMColor);
            mMFillPaint.setStyle(Paint.Style.FILL);
            mMFillPaint.setAntiAlias(true);

            mTime = new Time();

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if(mSensorManager != null) {
                Sensor countSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (countSensor != null) {
                    mSensorManager.registerListener(this,countSensor,SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible);
            }
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    //Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            DigitalWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            DigitalWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = DigitalWatchFaceService.this.getResources();

            float textSize = resources.getDimension(R.dimen.digital_text_size_round);

            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize/2);
            mStepPaint.setTextSize(textSize/4);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                        + ", low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }

            adjustPaintColorToCurrentMode(mCirclePaint, mInteractiveCircleColor,
                    Color.argb(255,0,0,0));
            adjustPaintColorToCurrentMode(mCircleBorderPaint, mInteractiveCircleBorderColor,
                    Color.argb(255,255,255,255));

            if(isInAmbientMode()){
                mCircleBorderPaint.setPathEffect(mAmbientDashEffect);
            } else {
                mCircleBorderPaint.setPathEffect(null);
            }

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mStepPaint.setAntiAlias(antiAlias);
                mCirclePaint.setAntiAlias(antiAlias);
                mCircleBorderPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor,
                int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            }
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mHourPaint.setAlpha(alpha);
                mMinutePaint.setAlpha(alpha);
                mStepPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private int convertTo12Hour(int hour) {
            int result = hour % 12;
            return (result == 0) ? 12 : result;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            long millis = System.currentTimeMillis() % 1000;
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mAmbientBackgroundPaint);
            if(!isInAmbientMode()){
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mInteractiveBackgroundPaint);
            }

            int centerX = (int)(bounds.width()/2 + CIRCLE_OFFSET);
            int centerY = (int)(bounds.height()/2 - CIRCLE_OFFSET);

            int left = (int)(centerX - (CIRCLE_RADIUS));
            int right = (int)(left + (2 * CIRCLE_RADIUS));
            int top = (int)(centerY - (CIRCLE_RADIUS));
            int bot = (int)(top + (2 * CIRCLE_RADIUS));

            //Want upper-right corner of path to line up with centerX, centerY
            mMPath.offset(-217.0f+centerX,centerY);
            if(!isInAmbientMode()) {
                canvas.drawPath(mMPath,mMFillPaint);
            } else {
                canvas.drawPath(mMPath,mMPathPaint);
            }
            mMPath.offset(217.0f-centerX,-centerY);

            // Draw the circle that goes under the time
            canvas.drawCircle(centerX, centerY,
                    (CIRCLE_RADIUS),mCirclePaint);

            if(!isInAmbientMode()) {
                float pctAround = (mTime.second + millis/1000.0f)/60.0f;

                if (mTime.minute % 2 == 0) {
                    canvas.drawArc(left, top, right, bot, 270,
                            360*pctAround, false, mCircleBorderPaint);
                } else {
                    canvas.drawArc(left, top, right, bot, (270+360*pctAround),
                            360*(1.0f-pctAround), false, mCircleBorderPaint);
                }
            } else {
                canvas.drawArc(left, top, right, bot, 0,
                        360, false, mCircleBorderPaint);
            }

            float hourHeight = -mHourPaint.getFontMetrics().ascent
                    + mHourPaint.getFontMetrics().leading/2;
            float minuteHeight =  -mMinutePaint.getFontMetrics().ascent
                    + mMinutePaint.getFontMetrics().leading/2;
            float totalHeight = hourHeight + minuteHeight;
            // Draw the hours.
            String hourString = String.valueOf(convertTo12Hour(mTime.hour));
            canvas.drawText(hourString, centerX, centerY + (hourHeight-(totalHeight/2)), mHourPaint);

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mTime.minute);
            canvas.drawText(minuteString, centerX,
                    centerY+(totalHeight/2), mMinutePaint);

            //Draw the steps
            String stepString = String.valueOf(mStepCount - mMidnightStepCount);
            //Draw the background rectangle
            Rect bgbounds = new Rect();
            mStepPaint.getTextBounds(stepString,0,stepString.length(),bgbounds);
            int origWidth = bgbounds.width();
            bgbounds.inset(-4-bgbounds.height()/2,-4);
            bgbounds.offset(centerX-origWidth/2-1,centerY+(int)(0.75*CIRCLE_WIDTH));

            canvas.drawRoundRect(new RectF(bgbounds),
                    bgbounds.height()/2,bgbounds.height()/2,
                    mCirclePaint);
            if(isInAmbientMode()) {
                canvas.drawRoundRect(new RectF(bgbounds),
                        bgbounds.height() / 2, bgbounds.height() / 2,
                        mCircleBorderPaint);
            }
            canvas.drawText(stepString, centerX, centerY+(int)(0.75*CIRCLE_WIDTH),mStepPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
/*
        private void updateConfigDataItemAndUiOnStartup() {
            DigitalWatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                    new DigitalWatchFaceUtil.FetchConfigDataMapCallback() {
                        @Override
                        public void onConfigDataMapFetched(DataMap startupConfig) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            setDefaultValuesForMissingConfigKeys(startupConfig);
                            DigitalWatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                            updateUiForConfigDataMap(startupConfig);
                        }
                    }
            );
        }

        private void setDefaultValuesForMissingConfigKeys(DataMap config) {
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_BACKGROUND_COLOR,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_HOURS_COLOR,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_MINUTES_COLOR,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_SECONDS_COLOR,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {
            if (!config.containsKey(key)) {
                config.putInt(key, color);
            }
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            try {
                for (DataEvent dataEvent : dataEvents) {
                    if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                        continue;
                    }

                    DataItem dataItem = dataEvent.getDataItem();
                    if (!dataItem.getUri().getPath().equals(
                            DigitalWatchFaceUtil.PATH_WITH_FEATURE)) {
                        continue;
                    }

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                    DataMap config = dataMapItem.getDataMap();
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Config DataItem updated:" + config);
                    }
                    updateUiForConfigDataMap(config);
                }
            } finally {
                dataEvents.close();
            }
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }
                int color = config.getInt(configKey);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                            + Integer.toHexString(color));
                }
                if (updateUiForKey(configKey, color)) {
                    uiUpdated = true;
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }
*/
        /**
         * Updates the color of a UI item according to the given {@code configKey}. Does nothing if
         * {@code configKey} isn't recognized.
         *
         * @return whether UI has been updated
         */
  /*      private boolean updateUiForKey(String configKey, int color) {
            if (configKey.equals(DigitalWatchFaceUtil.KEY_BACKGROUND_COLOR)) {
                setInteractiveBackgroundColor(color);
            } else if (configKey.equals(DigitalWatchFaceUtil.KEY_HOURS_COLOR)) {
                setInteractiveHourDigitsColor(color);
            } else if (configKey.equals(DigitalWatchFaceUtil.KEY_MINUTES_COLOR)) {
                setInteractiveMinuteDigitsColor(color);
            } else if (configKey.equals(DigitalWatchFaceUtil.KEY_SECONDS_COLOR)) {
                setInteractiveSecondDigitsColor(color);
            } else {
                Log.w(TAG, "Ignoring unknown config key: " + configKey);
                return false;
            }
            return true;
        }*/

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            //Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            //updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            mStepCount = (int)event.values[0];
            if(mTime.monthDay != mCurDay){
                mCurDay = mTime.monthDay;
                mMidnightStepCount = mStepCount;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Do nothing
        }
    }
}
