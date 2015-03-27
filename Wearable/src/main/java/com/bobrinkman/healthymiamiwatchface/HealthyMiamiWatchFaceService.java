/*
 * Copyright (C) 2015 Bo Brinkman
 *
 * Portions are Copyright (C) 2014 The Android Open Source Project, and
 *  used under the Apache License 2.0
 *  (see: https://github.com/googlesamples/android-WatchFace/blob/master/Wearable/src/main/java/com/example/android/wearable/watchface/DigitalWatchFaceService.java)
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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
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
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import java.util.TimeZone;

/**
 * Miami University themed digital watch face with step counter.
 */
public class HealthyMiamiWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "MiamiWatchFaceSrv";

    private Typeface BOLD_TYPEFACE = null;
    private Typeface NORMAL_TYPEFACE = null;

    //TODO: These are all in px. Would be better to switch to dp for layouts
    private static final float WATCH_WIDTH = 320.0f;
    private static final float CIRCLE_WIDTH = WATCH_WIDTH/2.0f;
    private static final float WATCH_RADIUS = CIRCLE_WIDTH;
    private static final float CIRCLE_RADIUS = CIRCLE_WIDTH/2.0f;
    private static final float CIRCLE_OFFSET = (float) Math.sqrt(CIRCLE_RADIUS*CIRCLE_RADIUS/2.0f);
    private static final float FONT_SIZE_LARGE = 90.0f;
    private static final int   PADDING = 12;

    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode.
     * 20 FPS seems to be sufficiently smooth looking
     */
    private static final long NORMAL_UPDATE_RATE_MS = 1000/20;

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

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(HealthyMiamiWatchFaceService.this)
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

        Time mTime;

        int mInteractiveBackgroundColor = Color.argb(255, 196, 18, 48);
        int mInteractiveBackgroundColor2 = Color.argb(128, 196, 18, 48);
        int mInteractiveDigitsColor = Color.argb(255,255,255,255);
        int mInteractiveCircleColor = Color.argb(96,0,0,0);
        int mInteractiveCircleBorderColor = Color.argb(196,255,255,255);
        int mInteractiveMiamiMColor = Color.argb(255,255,255,255);

        SensorManager mSensorManager = null;
        int mMidnightStepCount = 0;
        int mLastStepCount = 0;
        int mCurDay = -1;

        Bitmap mFootsteps;
        Bitmap mStipple;
        Shader mStippleShader;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        SharedPreferences mSettings;

        @SuppressLint("CommitPrefEdits")
        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            if(BOLD_TYPEFACE == null) {
                BOLD_TYPEFACE = Typeface.createFromAsset(getAssets(), "Open Sans 600.ttf");
            }
            if(NORMAL_TYPEFACE == null) {
                NORMAL_TYPEFACE = Typeface.createFromAsset(getAssets(), "Open Sans 300.ttf");
            }

            setWatchFaceStyle(new WatchFaceStyle.Builder(HealthyMiamiWatchFaceService.this)
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
            mCircleBorderPaint.setStrokeWidth(3.0f);

            mAmbientDashEffect = new DashPathEffect(new float[]{(2.0f),(4.0f)},0);

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
            mMPathPaint.setStrokeWidth(1.0f);

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

            mSettings = getSharedPreferences("HealthyMiamiWatchFace", MODE_PRIVATE);
            mMidnightStepCount = mSettings.getInt("MidnightStepCount",0);
            mCurDay = mSettings.getInt("CurDay",0);
            mLastStepCount = mSettings.getInt("LastStepCount",0);
            if (mLastStepCount < mMidnightStepCount) {
                //Something has gone badly wrong. Reset to a weird number
                // to make it clear that something went wrong
                SharedPreferences.Editor editorMidnight = mSettings.edit();
                mMidnightStepCount = mLastStepCount - 1234;
                editorMidnight.putInt("MidnightStepCount",mMidnightStepCount);
                editorMidnight.commit();
            }

            // Load resources that have alternate values for round watches.
            Resources resources = HealthyMiamiWatchFaceService.this.getResources();
            mFootsteps = BitmapFactory.decodeResource(resources,R.drawable.footprints);
            mStipple = BitmapFactory.decodeResource(resources,R.drawable.stipple);
            mStippleShader = new BitmapShader(mStipple, Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT);
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
            HealthyMiamiWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            HealthyMiamiWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            float textSize = FONT_SIZE_LARGE;

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
                mCircleBorderPaint.setStrokeWidth(2.0f);
                mMFillPaint.setShader(mStippleShader);
            } else {
                mCircleBorderPaint.setPathEffect(null);
                mCircleBorderPaint.setStrokeWidth(3.0f);
                mMFillPaint.setShader(null);
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
            canvas.drawPath(mMPath,mMFillPaint);
            if(isInAmbientMode()) {
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

            String hourString = String.valueOf(convertTo12Hour(mTime.hour));
            String minuteString = formatTwoDigitNumber(mTime.minute);

            Rect bgbounds = new Rect();
            mHourPaint.getTextBounds(hourString,0,hourString.length(),bgbounds);
            float hourHeight = bgbounds.height();
            mMinutePaint.getTextBounds(minuteString,0,minuteString.length(),bgbounds);
            float minuteHeight = bgbounds.height();
            float totalHeight = hourHeight + PADDING + minuteHeight;

            canvas.drawText(hourString, centerX, centerY + (hourHeight-(totalHeight/2)), mHourPaint);
            canvas.drawText(minuteString, centerX,
                    centerY+(totalHeight/2), mMinutePaint);

            //Draw the steps
            String stepString = String.valueOf(mLastStepCount - mMidnightStepCount);
            //Draw the background rectangle
            mStepPaint.getTextBounds(stepString,0,stepString.length(),bgbounds);

            int textWidth = bgbounds.width();
            int textHeight = bgbounds.height();
            int centerY2 = centerY + (int)(0.75*CIRCLE_WIDTH);
            int contentWidth = textWidth + mFootsteps.getWidth();
            int roomForRounded = textHeight+2*PADDING;
            int fullWidth = contentWidth + roomForRounded;
            int fullHeight = textHeight + 2*PADDING;
            int radius = roomForRounded/2;

            canvas.drawRoundRect(
                    centerX - fullWidth/2,
                    centerY2 - fullHeight/2,
                    centerX + fullWidth/2,
                    centerY2 + fullHeight/2,
                    radius, radius,
                    mCirclePaint);

            if(isInAmbientMode()) {
                canvas.drawRoundRect(
                        centerX - fullWidth/2,
                        centerY2 - fullHeight/2,
                        centerX + fullWidth/2,
                        centerY2 + fullHeight/2,
                        radius, radius,
                        mCircleBorderPaint);
            }
            canvas.drawText(stepString, centerX+mFootsteps.getWidth()/2,
                    centerY2+textHeight/2,mStepPaint);
            canvas.drawBitmap(mFootsteps, centerX - contentWidth / 2 - mFootsteps.getWidth() / 2,
                    centerY2 - mFootsteps.getHeight() / 2, null);

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

        @SuppressLint("CommitPrefEdits")
        @Override
        public void onSensorChanged(SensorEvent event) {
            //curStepCount is steps since system reboot
            int curStepCount = (int)event.values[0];
            int todayIs = mTime.year*10000 + mTime.month*100 + mTime.monthDay;

            if(curStepCount < mLastStepCount && todayIs == mCurDay){
                //Either a time warp ... or likely a reboot.
                //If this was a reboot, then we want to save the amount of steps
                // we had, by setting the mMidnightStepCount to an appropriate
                // negative number
                SharedPreferences.Editor editorMidnight = mSettings.edit();
                mMidnightStepCount = -(mLastStepCount - mMidnightStepCount);
                editorMidnight.putInt("MidnightStepCount",mMidnightStepCount);
                mLastStepCount = 0;
                editorMidnight.putInt("LastStepCount",mLastStepCount);
                editorMidnight.commit();
            } else if (curStepCount < mMidnightStepCount) {
                //Something has gone badly wrong. Reset to a weird number
                // to make it clear that something went wrong
                SharedPreferences.Editor editorMidnight = mSettings.edit();
                mMidnightStepCount = curStepCount - 1337;
                editorMidnight.putInt("MidnightStepCount",mMidnightStepCount);
                editorMidnight.commit();
            }

            SharedPreferences.Editor editor = mSettings.edit();
            mLastStepCount = curStepCount;
            editor.putInt("LastStepCount",mLastStepCount);

            if(todayIs != mCurDay){
                mCurDay = todayIs;
                mMidnightStepCount = mLastStepCount;
                editor.putInt("MidnightStepCount",mMidnightStepCount);
                editor.putInt("CurDay",mCurDay);
            }

            editor.commit();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Do nothing
        }
    }
}
