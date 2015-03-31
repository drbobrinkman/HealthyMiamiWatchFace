/*
 * Copyright (C) 2015 Bo Brinkman
 *
 * Portions are derived from: https://github.com/googlesamples/android-WatchFace/blob/master/Wearable/src/main/java/com/example/android/wearable/watchface/DigitalWatchFaceService.java)
 *  and used under the Apache Lincense, Version 2.0
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

import java.lang.ref.WeakReference;
import java.util.TimeZone;

/**
 * Provides a Miami University themed digital watch face with step counter.
 */
public class HealthyMiamiWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "MiamiWatchFaceSrv";

    //TODO: These are all in px. Would be better to switch to dp for layouts
    //Watch measurement constants
    private static final float WATCH_WIDTH = 320.0f;
    private static final float WATCH_RADIUS =  WATCH_WIDTH/2.0f;

    //Measurement constants for the main time display
    private static final float CIRCLE_WIDTH = WATCH_RADIUS;
    private static final float CIRCLE_RADIUS = CIRCLE_WIDTH/2.0f;
    private static final float CIRCLE_OFFSET = (float) Math.sqrt(CIRCLE_RADIUS*CIRCLE_RADIUS/2.0f);

    //Measurement constants for fonts and font spacing
    private static final float FONT_SIZE_LARGE = 90.0f;
    //Padding around step counter as well as spacing between hours and minutes
    private static final int PADDING = 12;

    //Dimensions of the Beveled M path
    private static final float M_PATH_WIDTH = 217.0f;
    private static final float M_PATH_HEIGHT = 164.0f;
    /**
     * Update rate in milliseconds for normal (not ambient) mode.
     * 20 FPS seems to be sufficiently smooth looking
     */
    private static final long NORMAL_UPDATE_RATE_MS = 1000/20;

    private static final DashPathEffect mTopLayerBorderDashEffect
            = new DashPathEffect(new float[]{(2.0f),(4.0f)},0);

    //The background is a radial gradient, color
    private static final int INTERACTIVE_BACKGROUND_COLOR_INNER = Color.argb(255, 196, 18, 48);
    private static final int INTERACTIVE_BACKGROUND_COLOR_OUTER = Color.argb(128, 196, 18, 48);
    private static final int AMBIENT_BACKGROUND_COLOR_INNER = Color.argb(255, 128, 128, 128);
    private static final int AMBIENT_BACKGROUND_COLOR_OUTER = Color.argb(128, 128, 128, 128);

    private static final int INTERACTIVE_DIGITS_COLOR = Color.argb(255,255,255,255);
    private static final int INTERACTIVE_CIRCLE_COLOR = Color.argb(96,0,0,0);
    private static final int LOWBIT_CIRCLE_COLOR = Color.argb(255,0,0,0);
    private static final int INTERACTIVE_CIRCLE_BORDER_COLOR = Color.argb(255,255,255,255);
    private static final int LOWBIT_CIRCLE_BORDER_COLOR = Color.argb(255,255,255,255);
    private static final int INTERACTIVE_MIAMI_M_COLOR = Color.argb(255,255,255,255);

    private static final float SHOE_PATH_WIDTH = 11.373f;
    private static final float SHOE_PATH_HEIGHT = 22.0f;
    /**
     * Points to make the beveled M. Note that at the point we
     * transition from the outer path to the inner path we
     * have a visual artifact. Need to keep it off screen.
     */
    private static final float[][] M_POINTS = {
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

    private static final double[][] SHOE_POINTS = {
            {0.00674493,11.299466},
            {0.04524493,9.913266},
            {0.27628493,8.3729658},
            {0.73836493,7.1022658},
            {1.1234349,6.2936658},
            {1.7780549,5.4849658},
            {2.6252049,5.0613658},
            {3.6263849,4.9843658},
            {4.3195049,5.2924658},
            {5.0126249,6.1010658},
            {5.6672449,7.5258658},
            {5.8982849,8.0649658},
            {5.6287349,6.6016658},
            {5.2051649,5.4849658},
            {5.0126249,4.3682658},
            {5.0511249,3.0590658},
            {5.4361949,1.9808658},
            {5.8982749,1.018166},
            {6.3988649,0.36356603},
            {7.1690049,0.05556603},
            {8.3627149,0.05556603},
            {9.1713549,0.47906603},
            {9.8259749,0.97966599},
            {10.326565,1.6728658},
            {10.673125,2.4429658},
            {11.058195,3.5981658},
            {11.327735,4.5608658},
            {11.366235,5.2154658},
            {11.366235,6.1781658},
            {11.173705,7.1792658},
            {10.942665,8.4500658},
            {10.904165,9.3741658},
            {10.904165,10.452366},
            {10.904165,11.607566},
            {10.904165,13.186366},
            {10.904165,14.187566},
            {10.634615,15.150166},
            {10.211045,15.727766},
            {9.5179149,16.266866},
            {8.7862849,16.343866},
            {7.9776449,16.304866},
            {7.1690049,15.996866},
            {6.6684149,15.534766},
            {6.2063249,14.649166},
            {6.0523049,13.763466},
            {6.1678249,12.492766},
            {6.3603549,11.029466},
            {6.2833549,10.336366},
            {6.0908149,9.7202658},
            {6.0523149,11.067966},
            {5.7057549,11.915166},
            {5.5517249,12.723766},
            {5.3591849,13.686466},
            {5.3591849,14.379566},
            {5.5132149,15.188266},
            {5.8597749,16.189366},
            {6.0908149,17.113566},
            {6.3218549,17.999166},
            {6.3988549,18.653866},
            {6.4758549,19.462466},
            {6.2833249,20.617666},
            {5.7827349,21.349266},
            {4.9355849,21.888366},
            {4.2039549,22.042466},
            {3.3568049,22.042466},
            {2.6251749,21.618866},
            {1.8550349,20.579166},
            {1.6239949,19.616466},
            {1.4314649,17.768166},
            {1.3159449,16.073866},
            {1.0848949,14.995666},
            {0.73833493,13.840466},
            {0.31476493,12.800766},
            {0.04521493,11.876666}
    };

    /**
     * These are visual elements that aren't changed after they are initialized,
     * they can be safely shared between different Engine instances
     */
    private static Typeface mNormalTypeface = null;
    private static Typeface mThinTypeface = null;

    private static Shader mStippleShader;

    private static Paint mBlackPaint; //For clearing the screen
    private static Paint mInteractiveBackgroundPaint; //red gradient for watch face
    private static Paint mAmbientBackgroundPaint; //gray gradient for watch face
    private static Paint mTopLayerBackgroundPaint;
    private static Paint mTopLayerBorderPaint;
    private static Paint mTopLayerBackgroundPaintLowBit;
    private static Paint mMFillPaint;
    private static Paint mMNoBurnFillPaint;
    private static Paint mMLowBitFillPaint;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    public static void initializeStaticPaints(){
        if(mBlackPaint == null) {
            mBlackPaint = new Paint();
            mBlackPaint.setColor(Color.argb(255, 0, 0, 0));
        }

        if(mInteractiveBackgroundPaint == null) {
            mInteractiveBackgroundPaint = new Paint();
            mInteractiveBackgroundPaint.setShader(new RadialGradient(WATCH_RADIUS, WATCH_RADIUS,
                    WATCH_RADIUS,
                    INTERACTIVE_BACKGROUND_COLOR_INNER, INTERACTIVE_BACKGROUND_COLOR_OUTER,
                    Shader.TileMode.CLAMP));
        }
        if(mAmbientBackgroundPaint == null) {
            mAmbientBackgroundPaint = new Paint();
            mAmbientBackgroundPaint.setShader(new RadialGradient(WATCH_RADIUS, WATCH_RADIUS,
                    WATCH_RADIUS,
                    AMBIENT_BACKGROUND_COLOR_INNER, AMBIENT_BACKGROUND_COLOR_OUTER,
                    Shader.TileMode.CLAMP));
        }

        if(mTopLayerBackgroundPaint == null) {
            mTopLayerBackgroundPaint = new Paint();
            mTopLayerBackgroundPaint.setColor(INTERACTIVE_CIRCLE_COLOR);
            mTopLayerBackgroundPaint.setStyle(Paint.Style.FILL);
            mTopLayerBackgroundPaint.setAntiAlias(true);
        }

        if(mTopLayerBackgroundPaintLowBit == null) {
            mTopLayerBackgroundPaintLowBit = new Paint();
            mTopLayerBackgroundPaintLowBit.setColor(LOWBIT_CIRCLE_COLOR);
            mTopLayerBackgroundPaintLowBit.setStyle(Paint.Style.FILL);
            mTopLayerBackgroundPaintLowBit.setAntiAlias(false);
        }

        if(mTopLayerBorderPaint == null) {
            mTopLayerBorderPaint = new Paint();
            mTopLayerBorderPaint.setColor(INTERACTIVE_CIRCLE_BORDER_COLOR);
            mTopLayerBorderPaint.setStyle(Paint.Style.STROKE);
            mTopLayerBorderPaint.setAntiAlias(true);
            mTopLayerBorderPaint.setStrokeWidth(2.0f);
        }

        if(mMFillPaint == null) {
            mMFillPaint = new Paint();
            mMFillPaint.setColor(INTERACTIVE_MIAMI_M_COLOR);
            mMFillPaint.setStyle(Paint.Style.FILL);
            mMFillPaint.setAntiAlias(true);
        }

        if(mMLowBitFillPaint == null) {
            mMLowBitFillPaint = new Paint();
            mMLowBitFillPaint.setColor(INTERACTIVE_MIAMI_M_COLOR);
            mMLowBitFillPaint.setStyle(Paint.Style.FILL);
            mMLowBitFillPaint.setAntiAlias(false);
        }
    }

    //Need a constant in for each message we might send. In this case
    // there is only one type of message to send, "invalidate the screen,
    // so the watch face gets updated"
    static final int MSG_UPDATE_WATCHFACE = 0;

    /** Handler to update the time periodically in interactive mode. */
    //Handler code from the sample is outdated, trips Lint HandlerLeak warning. My version is
    // based on http://stackoverflow.com/questions/11278875/handlers-and-memory-leaks-in-android
    static class WatchUpdateHandler extends Handler{
        WeakReference<HealthyMiamiWatchFaceService.Engine> mEngineRef;

        WatchUpdateHandler(HealthyMiamiWatchFaceService.Engine aEngine){
            mEngineRef = new WeakReference<>(aEngine);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_UPDATE_WATCHFACE:
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "updating time");
                    }

                    HealthyMiamiWatchFaceService.Engine theEngine = mEngineRef.get();

                    theEngine.invalidate();
                    if (theEngine.shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs =
                                NORMAL_UPDATE_RATE_MS - (timeMs % NORMAL_UPDATE_RATE_MS);
                        theEngine.mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_WATCHFACE, delayMs);
                    }
                    break;
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            SensorEventListener {

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        boolean mBurnInProtection;

        /** Detects when low-bit mode or burn-in-protection mode is enabled/disabled. */
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + mBurnInProtection);
            }
        }

        final Handler mUpdateTimeHandler = new WatchUpdateHandler(this);

        /** Handler to cope with time zone changes */
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        //Paints for text elements and step icon. These cannot be static because
        // anti-aliasing is turned on and off during run time
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mStepPaint;
        Paint mTMPaint;

        //Cannot be static because anti-aliasing is turned on and off during run time
        Paint mTopLayerBorderPaintNoBurn;

        //Arguably, these paths would make sense to be static except that onDraw() actually
        // changes the offset based on changes to timeCenterX and timeCenterY. If
        // onDraw was refactored to not depend on bound, this could be changed
        Path mMPath;
        Path mShoePath;

        //Cannot be static because anti-aliasing is turned on and off during run time
        Paint mMPathPaint;

        //These are really instance variables, cannot be static
        Time mTime;
        SensorManager mSensorManager = null;
        SharedPreferences mSettings;

        @SuppressLint("CommitPrefEdits")
        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            //getAssets() cannot be accessed from static, so can't go in initStaticPaints
            if(mNormalTypeface == null) {
                mNormalTypeface = Typeface.createFromAsset(getAssets(), "Open Sans 600.ttf");
            }
            if(mThinTypeface == null) {
                mThinTypeface = Typeface.createFromAsset(getAssets(), "Open Sans 300.ttf");
            }

            setWatchFaceStyle(new WatchFaceStyle.Builder(HealthyMiamiWatchFaceService.this)
                    .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setViewProtection(WatchFaceStyle.PROTECT_HOTWORD_INDICATOR | WatchFaceStyle.PROTECT_STATUS_BAR)
                    .setShowSystemUiTime(false)
                    .build());

            initializeStaticPaints();

            mTopLayerBorderPaintNoBurn = new Paint();
            mTopLayerBorderPaintNoBurn.setColor(LOWBIT_CIRCLE_BORDER_COLOR);
            mTopLayerBorderPaintNoBurn.setStyle(Paint.Style.STROKE);
            mTopLayerBorderPaintNoBurn.setAntiAlias(true);
            mTopLayerBorderPaintNoBurn.setStrokeWidth(2.0f);
            mTopLayerBorderPaintNoBurn.setPathEffect(mTopLayerBorderDashEffect);

            mHourPaint = createTextPaint(INTERACTIVE_DIGITS_COLOR, mNormalTypeface);
            mMinutePaint = createTextPaint(INTERACTIVE_DIGITS_COLOR);
            mStepPaint  = createTextPaint(INTERACTIVE_DIGITS_COLOR);
            mTMPaint  = createTextPaint(INTERACTIVE_DIGITS_COLOR, mNormalTypeface);
            mHourPaint.setTextSize(FONT_SIZE_LARGE);
            mMinutePaint.setTextSize(FONT_SIZE_LARGE/2);
            mStepPaint.setTextSize(FONT_SIZE_LARGE/4);
            mTMPaint.setTextSize(FONT_SIZE_LARGE/8);

            mMPath = new Path();
            mMPath.moveTo((M_POINTS[0][0]),(M_POINTS[0][1]));
            for(int i=1;i<M_POINTS.length;i++){
                mMPath.lineTo((M_POINTS[i][0]),(M_POINTS[i][1]));
            }
            mMPath.close();
            mMPath.setFillType(Path.FillType.EVEN_ODD);
            mShoePath = new Path();
            mShoePath.moveTo((float)(SHOE_POINTS[0][0]),(float)(SHOE_POINTS[0][1]));
            for(int i=1;i<SHOE_POINTS.length;i++){
                mShoePath.lineTo((float)(SHOE_POINTS[i][0]),(float)(SHOE_POINTS[i][1]));
            }
            mShoePath.close();
            mShoePath.setFillType(Path.FillType.EVEN_ODD);

            mMPathPaint = new Paint();
            mMPathPaint.setColor(INTERACTIVE_MIAMI_M_COLOR);
            mMPathPaint.setStyle(Paint.Style.STROKE);
            mMPathPaint.setAntiAlias(true);
            mMPathPaint.setStrokeWidth(1.0f);

            //getResources() cannot be accessed from static, so can't go in initStaticPaints
            Resources resources = HealthyMiamiWatchFaceService.this.getResources();
            if(mStippleShader == null) {
                Bitmap stipple = BitmapFactory.decodeResource(resources, R.drawable.stipple);
                mStippleShader = new BitmapShader(stipple, Shader.TileMode.REPEAT,
                        Shader.TileMode.REPEAT);
            }

            //Depends on mStippleShader, so can't go in initStaticPaints
            if(mMNoBurnFillPaint == null) {
                mMNoBurnFillPaint = new Paint();
                mMNoBurnFillPaint.setShader(mStippleShader);
                mMNoBurnFillPaint.setStyle(Paint.Style.FILL);
                mMNoBurnFillPaint.setAntiAlias(false);
            }

            mTime = new Time();

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if(mSensorManager != null) {
                Sensor countSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (countSensor != null) {
                    mSensorManager.registerListener(this,countSensor,SensorManager.SENSOR_DELAY_NORMAL);
                }
            }

            mSettings = getSharedPreferences("HealthyMiamiWatchFace", MODE_PRIVATE);
            updateStepData(CALLED_FROM_ON_CREATE);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_WATCHFACE);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, mThinTypeface);
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

            /**
             * These paints are normally anti-aliased in ambient mode, but shouldn't be
             * if we have a low bit ambient
             */
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mStepPaint.setAntiAlias(antiAlias);
                mTMPaint.setAntiAlias(antiAlias);
                mMPathPaint.setAntiAlias(antiAlias);
                mTopLayerBorderPaintNoBurn.setAntiAlias(antiAlias);
            }
            //Thinner fonts for less burn in
            if(mBurnInProtection){
                mHourPaint.setTypeface(inAmbientMode ? mThinTypeface : mNormalTypeface);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
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
            updateStepData(CALLED_FROM_TIME_UPDATE);

            //Clear the screen to black
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBlackPaint);

            //Draw the gradient background, if in interactive mode
            if(!isInAmbientMode()){
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mInteractiveBackgroundPaint);
            } else if(!mLowBitAmbient) {
                //Okay to use this even in burn-in-protection mode?
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mAmbientBackgroundPaint);
            } /* else {
                //Do nothing, black background
            }*/

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
            mMPath.offset(-M_PATH_WIDTH+timeCenterX,timeCenterY);
            //Always fill. Use stipple only in ambient mode, and only when burninprotection
            // is enabled
            Paint whichFill = mMFillPaint;
            if(isInAmbientMode()){
                if(mBurnInProtection){
                    //Use this version in both lowBit and non-lowBit, when doing burn-in protect
                    whichFill = mMNoBurnFillPaint;
                } else if (mLowBitAmbient) {
                    //Just disable anti-alias
                    whichFill = mMLowBitFillPaint;
                }
            }
            canvas.drawPath(mMPath,whichFill);
            //Add TM symbol
            canvas.drawText("TM",timeCenterX+PADDING,timeCenterY+M_PATH_HEIGHT,mTMPaint);

            //Draw outline only when stipple is used
            if(isInAmbientMode() && mBurnInProtection) {
                canvas.drawPath(mMPath,mMPathPaint);
            }
            mMPath.offset(M_PATH_WIDTH-timeCenterX,-timeCenterY);

            // Draw the circle that goes under the time
            canvas.drawCircle(timeCenterX, timeCenterY,
                    (CIRCLE_RADIUS),
                    ((isInAmbientMode() && mLowBitAmbient) ?
                            mTopLayerBackgroundPaintLowBit : mTopLayerBackgroundPaint));

            if(!isInAmbientMode()) {
                float pctAround = (mTime.second + millis/1000.0f)/60.0f;

                if (mTime.minute % 2 == 0) {
                    canvas.drawArc(circleLeft-1, circleTop-1, circleRight+1, circleBot+1, 270,
                            360*pctAround, false, mTopLayerBorderPaint);
                } else {
                    canvas.drawArc(circleLeft-1, circleTop-1, circleRight+1, circleBot+1,
                            (270+360*pctAround),
                            360*(1.0f-pctAround), false, mTopLayerBorderPaint);
                }

                //Inner circle counts each second
                pctAround = millis/1000.0f;
                if (mTime.second % 2 == 0) {
                    canvas.drawArc(circleLeft+2, circleTop+2, circleRight-2, circleBot-2, 270,
                            360*pctAround, false, mTopLayerBorderPaint);
                } else {
                    canvas.drawArc(circleLeft+2, circleTop+2, circleRight-2, circleBot-2,
                            (270+360*pctAround),
                            360*(1.0f-pctAround), false, mTopLayerBorderPaint);
                }

            } else {
                Paint whichBorderPaint = mTopLayerBorderPaint;
                if(mBurnInProtection || mLowBitAmbient){
                    //Use dotted, whether in low bit or not
                    whichBorderPaint = mTopLayerBorderPaintNoBurn;
                }
                canvas.drawArc(circleLeft-1, circleTop-1, circleRight+1, circleBot+1, 0,
                        360, false,
                        whichBorderPaint);
            }

            String hourString = String.valueOf(convertTo12Hour(mTime.hour));
            String minuteString = formatTwoDigitNumber(mTime.minute);

            Rect textBounds = new Rect();
            mHourPaint.getTextBounds(hourString,0,hourString.length(),textBounds);
            float hourHeight = textBounds.height();
            mMinutePaint.getTextBounds(minuteString,0,minuteString.length(),textBounds);
            float minuteHeight = textBounds.height();
            float totalHeight = hourHeight + PADDING + minuteHeight;

            canvas.drawText(hourString, timeCenterX, timeCenterY + (hourHeight-(totalHeight/2)),
                    mHourPaint);
            canvas.drawText(minuteString, timeCenterX,
                    timeCenterY+(totalHeight/2), mMinutePaint);

            String stepString = String.valueOf(mSettings.getInt(PREF_LAST_STEPS,0)
                    - mSettings.getInt(PREF_MIDNIGHT_STEPS,0));
            mStepPaint.getTextBounds(stepString,0,stepString.length(),textBounds);

            int textWidth = textBounds.width();
            int textHeight = textBounds.height();
            int stepCenterY = timeCenterY + (int)(0.75*CIRCLE_WIDTH);
            int contentWidth = textWidth + (int)SHOE_PATH_WIDTH;
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
                    ((isInAmbientMode() && mLowBitAmbient) ?
                            mTopLayerBackgroundPaintLowBit : mTopLayerBackgroundPaint));

            if(isInAmbientMode() && mLowBitAmbient) {
                //Only draw border on step area in low bit ambient mode
                canvas.drawRoundRect(
                        timeCenterX - fullWidth/2,
                        stepCenterY - fullHeight/2,
                        timeCenterX + fullWidth/2,
                        stepCenterY + fullHeight/2,
                        radius, radius,
                        mTopLayerBorderPaintNoBurn);
            }
            canvas.drawText(stepString, timeCenterX+SHOE_PATH_WIDTH/2,
                    stepCenterY+textHeight/2,mStepPaint);

            Paint whichPaint = mMFillPaint;
            if(isInAmbientMode()){
                if(mBurnInProtection){
                    //Use this version in both lowBit and non-lowBit, when doing burn-in protect
                    whichPaint = mMPathPaint;
                } else if (mLowBitAmbient) {
                    //Just disable anti-alias
                    whichPaint = mMLowBitFillPaint;
                }
            }
            float shoeOffsetX = timeCenterX - contentWidth / 2 - SHOE_PATH_WIDTH / 2;
            float shoeOffsetY = stepCenterY - SHOE_PATH_HEIGHT/ 2;
            mShoePath.offset(shoeOffsetX,shoeOffsetY);
            canvas.drawPath(mShoePath, whichPaint);
            mShoePath.offset(-shoeOffsetX,-shoeOffsetY);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_WATCHFACE);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_WATCHFACE);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private final int CALLED_FROM_TIME_UPDATE = -1;
        private final int CALLED_FROM_ON_CREATE = -2;

        private final String PREF_CUR_DAY = "CurDay";
        private final String PREF_LAST_STEPS = "LastStepCount";
        private final String PREF_MIDNIGHT_STEPS = "MidnightStepCount";
        /**
         * Updates step count info in storage. This is a single function so that
         * we don't have to think too hard about race conditions on the preferences.
         * This should be the only place that mSettings gets edited.
         *
         * Callers:
         *  - Time update function (really, onDraw)
         *  - Step counter callback
         *  - onCreate
         */
        //TODO: Check whether it is really safe to apply() instead of commit() here
        @SuppressLint("CommitPrefEdits")
        private synchronized void updateStepData(int curStepCount){
            if(curStepCount == CALLED_FROM_TIME_UPDATE){
                //being called from the time update function, check for day rollover.
                //When day changes, store the current step count as the midnight
                // step count and update the current day
                int todayIs = mTime.year * 10000 + mTime.month * 100 + mTime.monthDay;

                //Note: If pref doesn't exist, this will set the current day and
                // step count, which is probably the right thing to do
                if(todayIs != mSettings.getInt(PREF_CUR_DAY,0)){
                    int lastSteps = mSettings.getInt(PREF_LAST_STEPS, 0);
                    SharedPreferences.Editor editor = mSettings.edit();
                    editor.putInt(PREF_CUR_DAY,todayIs);
                    editor.putInt(PREF_MIDNIGHT_STEPS,lastSteps);
                    editor.commit();
                }
            } else if (curStepCount == CALLED_FROM_ON_CREATE) {
                //In onCreate we may discover invalid preference state,
                // which is when LAST_STEPS < MIDNIGHT_STEPS
                //This should only result from debugging, so we should log it as an error
                if(mSettings.getInt(PREF_LAST_STEPS,0) < mSettings.getInt(PREF_MIDNIGHT_STEPS,0)) {
                    Log.e(TAG, "updateStepData: LAST_STEPS < MIDNIGHT_STEPS");
                    SharedPreferences.Editor editor = mSettings.edit();
                    //Since this is an invalid state, we will just clear all the prefs
                    editor.putInt(PREF_CUR_DAY, 0);
                    editor.putInt(PREF_MIDNIGHT_STEPS, 0);
                    editor.putInt(PREF_LAST_STEPS, 0);
                    editor.commit();
                }
            } else {
                //Called by the sensor callback. Two possibilities:
                // - Just a normal update of step count
                // - curStepCount < LAST_STEPS ... this indicates a reboot.
                if (curStepCount < mSettings.getInt(PREF_LAST_STEPS,0)) {
                    //If this was a reboot, then we want to save the amount of steps
                    // we had, by setting the MIDNIGHT_STEPS to an appropriate
                    // negative number
                    int lastSteps = mSettings.getInt(PREF_LAST_STEPS,0);
                    int midnightSteps = mSettings.getInt(PREF_MIDNIGHT_STEPS,0);

                    SharedPreferences.Editor editorMidnight = mSettings.edit();
                    midnightSteps = -(lastSteps - midnightSteps);
                    editorMidnight.putInt(PREF_MIDNIGHT_STEPS, midnightSteps);
                    editorMidnight.putInt(PREF_LAST_STEPS, curStepCount);
                    editorMidnight.commit();
                } else {
                    //Just got a new step count, nothing wacky happened
                    SharedPreferences.Editor editor = mSettings.edit();
                    editor.putInt(PREF_LAST_STEPS, curStepCount);
                    editor.commit();
                }
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            //curStepCount is steps since system reboot
            int curStepCount = (int)event.values[0];
            updateStepData(curStepCount);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Do nothing
        }
    }
}
