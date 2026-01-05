package com.autoglm.server;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/**
 * Reflection Helper for Hidden APIs
 * Handles API differences across Android 11 - Android 14+
 */
public class ReflectionHelper {
    
    private static final String TAG = "ReflectionHelper";
    
    // Cached reflection objects
    private static Object inputManager;
    private static Method injectInputEventMethod;
    
    /**
     * Initialize InputManager via reflection
     */
    public static boolean initInputManager() {
        try {
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Method getInstanceMethod = inputManagerClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);
            inputManager = getInstanceMethod.invoke(null);
            
            if (inputManager == null) {
                Log.e(TAG, "Failed to get InputManager instance");
                return false;
            }
            
            // Try to find injectInputEvent method
            // Android 11-13: injectInputEvent(InputEvent, int)
            // Android 14+:  injectInputEvent(InputEvent, int, int)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (SDK 34)
                try {
                    injectInputEventMethod = inputManagerClass.getDeclaredMethod(
                        "injectInputEvent",
                        InputEvent.class,
                        int.class,  // mode (INJECT_INPUT_EVENT_MODE_ASYNC)
                        int.class   // targetUid (optional, use -1 for current)
                    );
                    Log.d(TAG, "Using Android 14+ injectInputEvent signature");
                } catch (NoSuchMethodException e) {
                    Log.w(TAG, "Android 14+ method not found, falling back");
                }
            }
            
            if (injectInputEventMethod == null) {
                // Android 11-13 fallback
                injectInputEventMethod = inputManagerClass.getDeclaredMethod(
                    "injectInputEvent",
                    InputEvent.class,
                    int.class  // mode
                );
                Log.d(TAG, "Using Android 11-13 injectInputEvent signature");
            }
            
            injectInputEventMethod.setAccessible(true);
            Log.d(TAG, "✅ InputManager initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize InputManager", e);
            return false;
        }
    }
    
    /**
     * Inject touch event
     * 
     * @param displayId Target display
     * @param action MotionEvent action
     * @param x X coordinate
     * @param y Y coordinate
     * @return true if successful
     */
    public static boolean injectTouch(int displayId, int action, float x, float y) {
        if (inputManager == null || injectInputEventMethod == null) {
            if (!initInputManager()) {
                return false;
            }
        }
        
        try {
            long now = android.os.SystemClock.uptimeMillis();
            
            MotionEvent event = MotionEvent.obtain(
                now,      // downTime
                now,      // eventTime
                action,   // action
                x,        // x
                y,        // y
                0         // metaState
            );
            
            // Set display ID via reflection
            try {
                Method setDisplayIdMethod = MotionEvent.class.getDeclaredMethod("setDisplayId", int.class);
                setDisplayIdMethod.setAccessible(true);
                setDisplayIdMethod.invoke(event, displayId);
            } catch (Exception e) {
                Log.w(TAG, "Failed to set display ID, using default", e);
            }
            
            boolean result;
            
            // MODE_ASYNC = 0
            final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: injectInputEvent(event, mode, targetUid)
                result = (boolean) injectInputEventMethod.invoke(
                    inputManager,
                    event,
                    INJECT_INPUT_EVENT_MODE_ASYNC,
                    -1  // targetUid (-1 means current process)
                );
            } else {
                // Android 11-13: injectInputEvent(event, mode)
                result = (boolean) injectInputEventMethod.invoke(
                    inputManager,
                    event,
                    INJECT_INPUT_EVENT_MODE_ASYNC
                );
            }
            
            event.recycle();
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject touch event", e);
            return false;
        }
    }
    
    /**
     * Inject key event (Back/Home)
     * 
     * @param keyCode KeyEvent code
     * @return true if successful
     */
    public static boolean injectKey(int keyCode) {
        if (inputManager == null || injectInputEventMethod == null) {
            if (!initInputManager()) {
                return false;
            }
        }
        
        try {
            long now = android.os.SystemClock.uptimeMillis();
            
            // Send DOWN event
            KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
            
            // Send UP event
            KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
            
            final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
            
            boolean downResult, upResult;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                downResult = (boolean) injectInputEventMethod.invoke(
                    inputManager, downEvent, INJECT_INPUT_EVENT_MODE_ASYNC, -1
                );
                upResult = (boolean) injectInputEventMethod.invoke(
                    inputManager, upEvent, INJECT_INPUT_EVENT_MODE_ASYNC, -1
                );
            } else {
                downResult = (boolean) injectInputEventMethod.invoke(
                    inputManager, downEvent, INJECT_INPUT_EVENT_MODE_ASYNC
                );
                upResult = (boolean) injectInputEventMethod.invoke(
                    inputManager, upEvent, INJECT_INPUT_EVENT_MODE_ASYNC
                );
            }
            
            return downResult && upResult;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject key event", e);
            return false;
        }
    }
    
    /**
     * Capture screenshot using SurfaceControl (Hidden API)
     * FIX: Save to file instead of returning byte[] to avoid TransactionTooLargeException
     * 
     * @param displayId Target display
     * @return Screenshot file path or null
     */
    public static String captureScreenToFile(int displayId) {
        try {
            // TODO: Full implementation requires:
            // 1. Get Display via DisplayManager
            // 2. Use SurfaceControl.screenshot() to get Bitmap
            // 3. Save Bitmap to /data/local/tmp/autoglm_screenshot_<timestamp>.png
            // 4. Return file path
            
            String filePath = "/data/local/tmp/autoglm_screenshot_" + 
                System.currentTimeMillis() + ".png";
            
            Log.w(TAG, "captureScreenToFile not fully implemented yet");
            Log.d(TAG, "Would save to: " + filePath);
            
            return null; // Placeholder
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screen", e);
            return null;
        }
    }
}
