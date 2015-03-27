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
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

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
     * Update rate in milliseconds for normal (not ambient) mode.
     * 20 FPS seems to be sufficiently smooth looking
     */
    private static final long NORMAL_UPDATE_RATE_MS = 1000/20;

    /**
     * Points to make the beveled M. Note that at the point we
     * transition from the outer path to the inner path we
     * have a visual artifact. Need to keep it off screen.
     */
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

    private class Engine extends CanvasWatchFaceService.Engine implements
            SensorEventListener {

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

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBlackPaint;

        Paint mInteractiveBackgroundPaint;

        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mStepPaint;

        Paint mTopLayerBackgroundPaint;
        Paint mTopLayerBorderPaint;
        DashPathEffect mTopLayerBorderDashEffect;

        Path  mMPath;
        Paint mMPathPaint;
        Paint mMFillPaint;

        Time mTime;

        //The background is a radial gradient, color
        int mInteractiveBackgroundColorInner = Color.argb(255, 196, 18, 48);
        int mInteractiveBackgroundColorOuter = Color.argb(128, 196, 18, 48);

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

            mBlackPaint = new Paint();
            mBlackPaint.setColor(Color.argb(255,0,0,0));

            mInteractiveBackgroundPaint = new Paint();
            mInteractiveBackgroundPaint.setShader(new RadialGradient(WATCH_RADIUS,WATCH_RADIUS,
                    WATCH_RADIUS,
                    mInteractiveBackgroundColorInner,mInteractiveBackgroundColorOuter,
                    Shader.TileMode.CLAMP));

            mTopLayerBackgroundPaint = new Paint();
            mTopLayerBackgroundPaint.setColor(mInteractiveCircleColor);
            mTopLayerBackgroundPaint.setStyle(Paint.Style.FILL);
            mTopLayerBackgroundPaint.setAntiAlias(true);

            mTopLayerBorderPaint = new Paint();
            mTopLayerBorderPaint.setColor(mInteractiveCircleBorderColor);
            mTopLayerBorderPaint.setStyle(Paint.Style.STROKE);
            mTopLayerBorderPaint.setAntiAlias(true);
            mTopLayerBorderPaint.setStrokeWidth(3.0f);

            mTopLayerBorderDashEffect = new DashPathEffect(new float[]{(2.0f),(4.0f)},0);

            mHourPaint = createTextPaint(mInteractiveDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveDigitsColor);
            mStepPaint  = createTextPaint(mInteractiveDigitsColor);
            mHourPaint.setTextSize(FONT_SIZE_LARGE);
            mMinutePaint.setTextSize(FONT_SIZE_LARGE/2);
            mStepPaint.setTextSize(FONT_SIZE_LARGE/4);

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
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
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

            adjustPaintColorToCurrentMode(mTopLayerBackgroundPaint, mInteractiveCircleColor,
                    Color.argb(255,0,0,0));
            adjustPaintColorToCurrentMode(mTopLayerBorderPaint, mInteractiveCircleBorderColor,
                    Color.argb(255,255,255,255));

            if(isInAmbientMode()){
                mTopLayerBorderPaint.setPathEffect(mTopLayerBorderDashEffect);
                mTopLayerBorderPaint.setStrokeWidth(2.0f);
                mMFillPaint.setShader(mStippleShader);
            } else {
                mTopLayerBorderPaint.setPathEffect(null);
                mTopLayerBorderPaint.setStrokeWidth(3.0f);
                mMFillPaint.setShader(null);
            }

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mStepPaint.setAntiAlias(antiAlias);
                mTopLayerBackgroundPaint.setAntiAlias(antiAlias);
                mTopLayerBorderPaint.setAntiAlias(antiAlias);
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

            //Clear the screen to black
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBlackPaint);

            //Draw the gradient background, if in interactive mode
            if(!isInAmbientMode()){
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mInteractiveBackgroundPaint);
            }

            //The time is shown in a circle whose circumference touches
            // both the center of the view and (in a circular watch), the
            // edge of the view. It is at a 45 degree angle up and right
            // of the center of the view
            int timeCenterX = (int)(bounds.width()/2 + CIRCLE_OFFSET);
            int timeCenterY = (int)(bounds.height()/2 - CIRCLE_OFFSET);

            int circleLeft = (int)(timeCenterX - (CIRCLE_RADIUS));
            int circleRight = (int)(circleLeft + (2 * CIRCLE_RADIUS));
            int circleTop = (int)(timeCenterY - (CIRCLE_RADIUS));
            int circleBot = (int)(circleTop + (2 * CIRCLE_RADIUS));

            //Want upper-right corner of path to line up with centerX, centerY
            mMPath.offset(-217.0f+timeCenterX,timeCenterY);
            canvas.drawPath(mMPath,mMFillPaint);
            if(isInAmbientMode()) {
                canvas.drawPath(mMPath,mMPathPaint);
            }
            mMPath.offset(217.0f-timeCenterX,-timeCenterY);

            // Draw the circle that goes under the time
            canvas.drawCircle(timeCenterX, timeCenterY,
                    (CIRCLE_RADIUS),mTopLayerBackgroundPaint);

            if(!isInAmbientMode()) {
                float pctAround = (mTime.second + millis/1000.0f)/60.0f;

                if (mTime.minute % 2 == 0) {
                    canvas.drawArc(circleLeft, circleTop, circleRight, circleBot, 270,
                            360*pctAround, false, mTopLayerBorderPaint);
                } else {
                    canvas.drawArc(circleLeft, circleTop, circleRight, circleBot, (270+360*pctAround),
                            360*(1.0f-pctAround), false, mTopLayerBorderPaint);
                }
            } else {
                canvas.drawArc(circleLeft, circleTop, circleRight, circleBot, 0,
                        360, false, mTopLayerBorderPaint);
            }

            String hourString = String.valueOf(convertTo12Hour(mTime.hour));
            String minuteString = formatTwoDigitNumber(mTime.minute);

            Rect textBounds = new Rect();
            mHourPaint.getTextBounds(hourString,0,hourString.length(),textBounds);
            float hourHeight = textBounds.height();
            mMinutePaint.getTextBounds(minuteString,0,minuteString.length(),textBounds);
            float minuteHeight = textBounds.height();
            float totalHeight = hourHeight + PADDING + minuteHeight;

            canvas.drawText(hourString, timeCenterX, timeCenterY + (hourHeight-(totalHeight/2)), mHourPaint);
            canvas.drawText(minuteString, timeCenterX,
                    timeCenterY+(totalHeight/2), mMinutePaint);

            String stepString = String.valueOf(mLastStepCount - mMidnightStepCount);
            mStepPaint.getTextBounds(stepString,0,stepString.length(),textBounds);

            int textWidth = textBounds.width();
            int textHeight = textBounds.height();
            int stepCenterY = timeCenterY + (int)(0.75*CIRCLE_WIDTH);
            int contentWidth = textWidth + mFootsteps.getWidth();
            int roomForRounded = textHeight+2*PADDING;
            int fullWidth = contentWidth + roomForRounded;
            int fullHeight = textHeight + 2*PADDING;
            int radius = roomForRounded/2;

            canvas.drawRoundRect(
                    timeCenterX - fullWidth/2,
                    stepCenterY - fullHeight/2,
                    timeCenterX + fullWidth/2,
                    stepCenterY + fullHeight/2,
                    radius, radius,
                    mTopLayerBackgroundPaint);

            if(isInAmbientMode()) {
                canvas.drawRoundRect(
                        timeCenterX - fullWidth/2,
                        stepCenterY - fullHeight/2,
                        timeCenterX + fullWidth/2,
                        stepCenterY + fullHeight/2,
                        radius, radius,
                        mTopLayerBorderPaint);
            }
            canvas.drawText(stepString, timeCenterX+mFootsteps.getWidth()/2,
                    stepCenterY+textHeight/2,mStepPaint);
            canvas.drawBitmap(mFootsteps, timeCenterX - contentWidth / 2 - mFootsteps.getWidth() / 2,
                    stepCenterY - mFootsteps.getHeight() / 2, null);

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
