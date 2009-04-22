/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.view;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.LocalPowerManager;

/**
 * This interface supplies all UI-specific behavior of the window manager.  An
 * instance of it is created by the window manager when it starts up, and allows
 * customization of window layering, special window types, key dispatching, and
 * layout.
 * 
 * <p>Because this provides deep interaction with the system window manager,
 * specific methods on this interface can be called from a variety of contexts
 * with various restrictions on what they can do.  These are encoded through
 * a suffixes at the end of a method encoding the thread the method is called
 * from and any locks that are held when it is being called; if no suffix
 * is attached to a method, then it is not called with any locks and may be
 * called from the main window manager thread or another thread calling into
 * the window manager.
 * 
 * <p>The current suffixes are:
 * 
 * <dl>
 * <dt> Ti <dd> Called from the input thread.  This is the thread that
 * collects pending input events and dispatches them to the appropriate window.
 * It may block waiting for events to be processed, so that the input stream is
 * properly serialized.
 * <dt> Tq <dd> Called from the low-level input queue thread.  This is the
 * thread that reads events out of the raw input devices and places them
 * into the global input queue that is read by the <var>Ti</var> thread.
 * This thread should not block for a long period of time on anything but the
 * key driver.
 * <dt> Lw <dd> Called with the main window manager lock held.  Because the
 * window manager is a very low-level system service, there are few other
 * system services you can call with this lock held.  It is explicitly okay to
 * make calls into the package manager and power manager; it is explicitly not
 * okay to make calls into the activity manager.  Note that
 * {@link android.content.Context#checkPermission(String, int, int)} and
 * variations require calling into the activity manager.
 * <dt> Li <dd> Called with the input thread lock held.  This lock can be
 * acquired by the window manager while it holds the window lock, so this is
 * even more restrictive than <var>Lw</var>.
 * </dl>
 * 
 * @hide
 */
public interface WindowManagerPolicy {
    public final static int FLAG_WAKE = 0x00000001;
    public final static int FLAG_WAKE_DROPPED = 0x00000002;
    public final static int FLAG_SHIFT = 0x00000004;
    public final static int FLAG_CAPS_LOCK = 0x00000008;
    public final static int FLAG_ALT = 0x00000010;
    public final static int FLAG_ALT_GR = 0x00000020;
    public final static int FLAG_MENU = 0x00000040;
    public final static int FLAG_LAUNCHER = 0x00000080;

    public final static int FLAG_WOKE_HERE = 0x10000000;
    public final static int FLAG_BRIGHT_HERE = 0x20000000;

    public final static boolean WATCH_POINTER = false;

    // flags for interceptKeyTq
    /**
     * Pass this event to the user / app.  To be returned from {@link #interceptKeyTq}.
     */
    public final static int ACTION_PASS_TO_USER = 0x00000001;

    /**
     * This key event should extend the user activity timeout and turn the lights on.
     * To be returned from {@link #interceptKeyTq}. Do not return this and
     * {@link #ACTION_GO_TO_SLEEP} or {@link #ACTION_PASS_TO_USER}.
     */
    public final static int ACTION_POKE_USER_ACTIVITY = 0x00000002;

    /**
     * This key event should put the device to sleep (and engage keyguard if necessary)
     * To be returned from {@link #interceptKeyTq}.  Do not return this and
     * {@link #ACTION_POKE_USER_ACTIVITY} or {@link #ACTION_PASS_TO_USER}.
     */
    public final static int ACTION_GO_TO_SLEEP = 0x00000004;

    /**
     * Interface to the Window Manager state associated with a particular
     * window.  You can hold on to an instance of this interface from the call
     * to prepareAddWindow() until removeWindow().
     */
    public interface WindowState {
        /**
         * Perform standard frame computation.  The result can be obtained with
         * getFrame() if so desired.  Must be called with the window manager
         * lock held.
         * 
         * @param parentLeft The left edge of the parent container this window
         * is in, used for computing its position.
         * @param parentTop The top edge of the parent container this window
         * is in, used for computing its position.
         * @param parentRight The right edge of the parent container this window
         * is in, used for computing its position.
         * @param parentBottom The bottom edge of the parent container this window
         * is in, used for computing its position.
         * @param displayLeft The left edge of the available display, used
         * for constraining the overall dimensions of the window.
         * @param displayTop The left edge of the available display, used
         * for constraining the overall dimensions of the window.
         * @param displayRight The right edge of the available display, used
         * for constraining the overall dimensions of the window.
         * @param displayBottom The bottom edge of the available display, used
         * for constraining the overall dimensions of the window.
         */
        public void computeFrameLw(int parentLeft, int parentRight, int parentBottom,
                int parentHeight, int displayLeft, int displayTop,
                int displayRight, int displayBottom);

        /**
         * Set the window's frame to an exact value.  Must be called with the
         * window manager lock held.
         * 
         * @param left Left edge of the window.
         * @param top Top edge of the window.
         * @param right Right edge (exclusive) of the window.
         * @param bottom Bottom edge (exclusive) of the window.
         */
        public void setFrameLw(int left, int top, int right, int bottom);

        /**
         * Retrieve the current frame of the window.  Must be called with the
         * window manager lock held.
         * 
         * @return Rect The rectangle holding the window frame.
         */
        public Rect getFrameLw();

        /**
         * Retrieve the current LayoutParams of the window.
         * 
         * @return WindowManager.LayoutParams The window's internal LayoutParams
         *         instance.
         */
        public WindowManager.LayoutParams getAttrs();

        /**
         * Return the token for the application (actually activity) that owns 
         * this window.  May return null for system windows. 
         * 
         * @return An IApplicationToken identifying the owning activity.
         */
        public IApplicationToken getAppToken();

        /**
         * Return true if, at any point, the application token associated with 
         * this window has actually displayed any windows.  This is most useful 
         * with the "starting up" window to determine if any windows were 
         * displayed when it is closed. 
         * 
         * @return Returns true if one or more windows have been displayed, 
         *         else false.
         */
        public boolean hasAppShownWindows();

        /**
         * Return true if the application token has been asked to display an 
         * app starting icon as the application is starting up. 
         * 
         * @return Returns true if setAppStartingIcon() was called for this 
         *         window's token.
         */
        public boolean hasAppStartingIcon();

        /**
         * Return the Window that is being displayed as this window's 
         * application token is being started. 
         * 
         * @return Returns the currently displayed starting window, or null if 
         *         it was not requested, has not yet been displayed, or has
         *         been removed.
         */
        public WindowState getAppStartingWindow();

        /**
         * Is this window currently visible to the user on-screen?  It is 
         * displayed either if it is visible or it is currently running an 
         * animation before no longer being visible.  Must be called with the
         * window manager lock held.
         */
        boolean isDisplayedLw();

        /**
         * Returns true if the window is both full screen and opaque.  Must be
         * called with the window manager lock held.
         * 
         * @param width The width of the screen
         * @param height The height of the screen 
         * @param shownFrame If true, this is based on the actual shown frame of 
         *                   the window (taking into account animations); if
         *                   false, this is based on the currently requested
         *                   frame, which any current animation will be moving
         *                   towards.
         * @return Returns true if the window is both full screen and opaque
         */
        public boolean fillsScreenLw(int width, int height, boolean shownFrame);

        /**
         * Returns true if this window has been shown on screen at some time in 
         * the past.  Must be called with the window manager lock held.
         * 
         * @return boolean
         */
        public boolean hasDrawnLw();

        /**
         * Can be called by the policy to force a window to be hidden,
         * regardless of whether the client or window manager would like
         * it shown.  Must be called with the window manager lock held.
         */
        public void hideLw();
        
        /**
         * Can be called to undo the effect of {@link #hideLw}, allowing a
         * window to be shown as long as the window manager and client would
         * also like it to be shown.  Must be called with the window manager
         * lock held.
         */
        public void showLw();

        /**
         * Sets insets on the window that represent the area within the window that is covered
         * by system windows (e.g. status bar).  Must be called with the window
         * manager lock held.
         * 
         * @param l
         * @param t
         * @param r
         * @param b
         */
        public void setCoveredInsetsLw(int l, int t, int r, int b);
    }

    /** No transition happening. */
    public final int TRANSIT_NONE = 0;
    /** Window has been added to the screen. */
    public final int TRANSIT_ENTER = 1;
    /** Window has been removed from the screen. */
    public final int TRANSIT_EXIT = 2;
    /** Window has been made visible. */
    public final int TRANSIT_SHOW = 3;
    /** Window has been made invisible. */
    public final int TRANSIT_HIDE = 4;
    /** The "application starting" preview window is no longer needed, and will
     * animate away to show the real window. */
    public final int TRANSIT_PREVIEW_DONE = 5;
    /** A window in a new activity is being opened on top of an existing one
     * in the same task. */
    public final int TRANSIT_ACTIVITY_OPEN = 6;
    /** The window in the top-most activity is being closed to reveal the
     * previous activity in the same task. */
    public final int TRANSIT_ACTIVITY_CLOSE = 7;
    /** A window in a new task is being opened on top of an existing one
     * in another activity's task. */
    public final int TRANSIT_TASK_OPEN = 8;
    /** A window in the top-most activity is being closed to reveal the
     * previous activity in a different task. */
    public final int TRANSIT_TASK_CLOSE = 9;
    /** A window in an existing task is being displayed on top of an existing one
     * in another activity's task. */
    public final int TRANSIT_TASK_TO_FRONT = 10;
    /** A window in an existing task is being put below all other tasks. */
    public final int TRANSIT_TASK_TO_BACK = 11;
    
    /** Screen turned off because of power button */
    public final int OFF_BECAUSE_OF_USER = 1;
    /** Screen turned off because of timeout */
    public final int OFF_BECAUSE_OF_TIMEOUT = 2;

    /**
     * Magic constant to {@link IWindowManager#setRotation} to not actually
     * modify the rotation.
     */
    public final int USE_LAST_ROTATION = -1000;
    
    /**
     * Perform initialization of the policy.
     * 
     * @param context The system context we are running in.
     * @param powerManager 
     */
    public void init(Context context, IWindowManager windowManager,
            LocalPowerManager powerManager);

    /**
     * Check permissions when adding a window.
     * 
     * @param attrs The window's LayoutParams. 
     *  
     * @return {@link WindowManagerImpl#ADD_OKAY} if the add can proceed;
     *      else an error code, usually
     *      {@link WindowManagerImpl#ADD_PERMISSION_DENIED}, to abort the add.
     */
    public int checkAddPermission(WindowManager.LayoutParams attrs);

    /**
     * Sanitize the layout parameters coming from a client.  Allows the policy
     * to do things like ensure that windows of a specific type can't take
     * input focus.
     * 
     * @param attrs The window layout parameters to be modified.  These values
     * are modified in-place.
     */
    public void adjustWindowParamsLw(WindowManager.LayoutParams attrs);
    
    /**
     * After the window manager has computed the current configuration based
     * on its knowledge of the display and input devices, it gives the policy
     * a chance to adjust the information contained in it.  If you want to
     * leave it as-is, simply do nothing.
     * 
     * <p>This method may be called by any thread in the window manager, but
     * no internal locks in the window manager will be held.
     * 
     * @param config The Configuration being computed, for you to change as
     * desired.
     */
    public void adjustConfigurationLw(Configuration config);
    
    /**
     * Assign a window type to a layer.  Allows you to control how different
     * kinds of windows are ordered on-screen.
     * 
     * @param type The type of window being assigned.
     * 
     * @return int An arbitrary integer used to order windows, with lower
     *         numbers below higher ones.
     */
    public int windowTypeToLayerLw(int type);

    /**
     * Return how to Z-order sub-windows in relation to the window they are
     * attached to.  Return positive to have them ordered in front, negative for
     * behind.
     * 
     * @param type The sub-window type code.
     * 
     * @return int Layer in relation to the attached window, where positive is
     *         above and negative is below.
     */
    public int subWindowTypeToLayerLw(int type);

    /**
     * Called when the system would like to show a UI to indicate that an
     * application is starting.  You can use this to add a
     * APPLICATION_STARTING_TYPE window with the given appToken to the window
     * manager (using the normal window manager APIs) that will be shown until
     * the application displays its own window.  This is called without the
     * window manager locked so that you can call back into it.
     * 
     * @param appToken Token of the application being started.
     * @param packageName The name of the application package being started. 
     * @param theme Resource defining the application's overall visual theme.
     * @param nonLocalizedLabel The default title label of the application if
     *        no data is found in the resource.
     * @param labelRes The resource ID the application would like to use as its name.
     * @param icon The resource ID the application would like to use as its icon.
     * 
     * @return Optionally you can return the View that was used to create the
     *         window, for easy removal in removeStartingWindow.
     * 
     * @see #removeStartingWindow
     */
    public View addStartingWindow(IBinder appToken, String packageName,
            int theme, CharSequence nonLocalizedLabel,
            int labelRes, int icon);

    /**
     * Called when the first window of an application has been displayed, while
     * {@link #addStartingWindow} has created a temporary initial window for
     * that application.  You should at this point remove the window from the
     * window manager.  This is called without the window manager locked so
     * that you can call back into it.
     * 
     * <p>Note: due to the nature of these functions not being called with the
     * window manager locked, you must be prepared for this function to be
     * called multiple times and/or an initial time with a null View window
     * even if you previously returned one.
     * 
     * @param appToken Token of the application that has started.
     * @param window Window View that was returned by createStartingWindow.
     * 
     * @see #addStartingWindow
     */
    public void removeStartingWindow(IBinder appToken, View window);

    /**
     * Prepare for a window being added to the window manager.  You can throw an
     * exception here to prevent the window being added, or do whatever setup
     * you need to keep track of the window.
     * 
     * @param win The window being added.
     * @param attrs The window's LayoutParams. 
     *  
     * @return {@link WindowManagerImpl#ADD_OKAY} if the add can proceed, else an 
     *         error code to abort the add.
     */
    public int prepareAddWindowLw(WindowState win,
            WindowManager.LayoutParams attrs);

    /**
     * Called when a window is being removed from a window manager.  Must not
     * throw an exception -- clean up as much as possible.
     * 
     * @param win The window being removed.
     */
    public void removeWindowLw(WindowState win);

    /**
     * Control the animation to run when a window's state changes.  Return a
     * non-0 number to force the animation to a specific resource ID, or 0
     * to use the default animation.
     * 
     * @param win The window that is changing.
     * @param transit What is happening to the window: {@link #TRANSIT_ENTER},
     *                {@link #TRANSIT_EXIT}, {@link #TRANSIT_SHOW}, or
     *                {@link #TRANSIT_HIDE}.
     * 
     * @return Resource ID of the actual animation to use, or 0 for none.
     */
    public int selectAnimationLw(WindowState win, int transit);

    /**
     * Called from the key queue thread before a key is dispatched to the
     * input thread.
     *
     * <p>There are some actions that need to be handled here because they
     * affect the power state of the device, for example, the power keys.
     * Generally, it's best to keep as little as possible in the queue thread
     * because it's the most fragile.
     *
     * @param event the raw input event as read from the driver
     * @param screenIsOn true if the screen is already on
     * @return The bitwise or of the {@link #ACTION_PASS_TO_USER},
     *          {@link #ACTION_POKE_USER_ACTIVITY} and {@link #ACTION_GO_TO_SLEEP} flags.
     */
    public int interceptKeyTq(RawInputEvent event, boolean screenIsOn);
    
    /**
     * Called from the input thread before a key is dispatched to a window.
     *
     * <p>Allows you to define
     * behavior for keys that can not be overridden by applications or redirect
     * key events to a different window.  This method is called from the
     * input thread, with no locks held.
     * 
     * <p>Note that if you change the window a key is dispatched to, the new
     * target window will receive the key event without having input focus.
     * 
     * @param win The window that currently has focus.  This is where the key
     *            event will normally go.
     * @param code Key code.
     * @param metaKeys TODO
     * @param down Is this a key press (true) or release (false)?
     * @param repeatCount Number of times a key down has repeated.
     * @return Returns true if the policy consumed the event and it should
     * not be further dispatched.
     */
    public boolean interceptKeyTi(WindowState win, int code,
                               int metaKeys, boolean down, int repeatCount);

    /**
     * Called when layout of the windows is about to start.
     * 
     * @param displayWidth The current full width of the screen.
     * @param displayHeight The current full height of the screen.
     */
    public void beginLayoutLw(int displayWidth, int displayHeight);

    /**
     * Called for each window attached to the window manager as layout is
     * proceeding.  The implementation of this function must take care of
     * setting the window's frame, either here or in finishLayout().
     * 
     * @param win The window being positioned.
     * @param attrs The LayoutParams of the window.
     * @param attached For sub-windows, the window it is attached to; this
     *                 window will already have had layoutWindow() called on it
     *                 so you can use its Rect.  Otherwise null.
     */
    public void layoutWindowLw(WindowState win,
            WindowManager.LayoutParams attrs, WindowState attached);

    
    /**
     * Return the insets for the areas covered by system windows. These values are computed on the
     * mose recent layout, so they are not guaranteed to be correct.
     * 
     * @param attrs The LayoutParams of the window.
     * @param coveredInset The areas covered by system windows, expressed as positive insets
     * 
     */
    public void getCoveredInsetHintLw(WindowManager.LayoutParams attrs, Rect coveredInset);
    
    /**
     * Called when layout of the windows is finished.  After this function has
     * returned, all windows given to layoutWindow() <em>must</em> have had a
     * frame assigned.
     */
    public void finishLayoutLw();

    /**
     * Called when animation of the windows is about to start.
     * 
     * @param displayWidth The current full width of the screen.
     * @param displayHeight The current full height of the screen.
     */
    public void beginAnimationLw(int displayWidth, int displayHeight);

    /**
     * Called each time a window is animating.
     * 
     * @param win The window being positioned.
     * @param attrs The LayoutParams of the window. 
     */
    public void animatingWindowLw(WindowState win,
            WindowManager.LayoutParams attrs);

    /**
     * Called when animation of the windows is finished.  If in this function you do 
     * something that may have modified the animation state of another window, 
     * be sure to return true in order to perform another animation frame. 
     *  
     * @return Return true if animation state may have changed (so that another 
     *         frame of animation will be run).
     */
    public boolean finishAnimationLw();

    /**
     * Called after the screen turns off.
     *
     * @param why {@link #OFF_BECAUSE_OF_USER} or
     * {@link #OFF_BECAUSE_OF_TIMEOUT}.
     */
    public void screenTurnedOff(int why);

    /**
     * Called after the screen turns on.
     */
    public void screenTurnedOn();

    /**
     * Perform any initial processing of a low-level input event before the
     * window manager handles special keys and generates a high-level event
     * that is dispatched to the application.
     * 
     * @param event The input event that has occurred.
     * 
     * @return Return true if you have consumed the event and do not want
     * further processing to occur; return false for normal processing.
     */
    public boolean preprocessInputEventTq(RawInputEvent event);
    
    /**
     * Determine whether a given key code is used to cause an app switch
     * to occur (most often the HOME key, also often ENDCALL).  If you return
     * true, then the system will go into a special key processing state
     * where it drops any pending events that it cans and adjusts timeouts to
     * try to get to this key as quickly as possible.
     * 
     * <p>Note that this function is called from the low-level input queue
     * thread, with either/or the window or input lock held; be very careful
     * about what you do here.  You absolutely should never acquire a lock
     * that you would ever hold elsewhere while calling out into the window
     * manager or view hierarchy.
     * 
     * @param keycode The key that should be checked for performing an
     * app switch before delivering to the application.
     * 
     * @return Return true if this is an app switch key and special processing
     * should happen; return false for normal processing.
     */
    public boolean isAppSwitchKeyTqTiLwLi(int keycode);
    
    /**
     * Determine whether a given key code is used for movement within a UI,
     * and does not generally cause actions to be performed (normally the DPAD
     * movement keys, NOT the DPAD center press key).  This is called
     * when {@link #isAppSwitchKeyTiLi} returns true to remove any pending events
     * in the key queue that are not needed to switch applications.
     * 
     * <p>Note that this function is called from the low-level input queue
     * thread; be very careful about what you do here.
     * 
     * @param keycode The key that is waiting to be delivered to the
     * application.
     * 
     * @return Return true if this is a purely navigation key and can be
     * dropped without negative consequences; return false to keep it.
     */
    public boolean isMovementKeyTi(int keycode);
    
    /**
     * Given the current state of the world, should this relative movement
     * wake up the device?
     * 
     * @param device The device the movement came from.
     * @param classes The input classes associated with the device.
     * @param event The input event that occurred.
     * @return
     */
    public boolean isWakeRelMovementTq(int device, int classes,
            RawInputEvent event);
    
    /**
     * Given the current state of the world, should this absolute movement
     * wake up the device?
     * 
     * @param device The device the movement came from.
     * @param classes The input classes associated with the device.
     * @param event The input event that occurred.
     * @return
     */
    public boolean isWakeAbsMovementTq(int device, int classes,
            RawInputEvent event);
    
    /**
     * Tell the policy if anyone is requesting that keyguard not come on.
     *
     * @param enabled Whether keyguard can be on or not.  does not actually
     * turn it on, unless it was previously disabled with this function.
     *
     * @see android.app.KeyguardManager.KeyguardLock#disableKeyguard()
     * @see android.app.KeyguardManager.KeyguardLock#reenableKeyguard()
     */
    public void enableKeyguard(boolean enabled);

    /**
     * Callback used by {@link WindowManagerPolicy#exitKeyguardSecurely}
     */
    interface OnKeyguardExitResult {
        void onKeyguardExitResult(boolean success);
    }

    /**
     * Tell the policy if anyone is requesting the keyguard to exit securely
     * (this would be called after the keyguard was disabled)
     * @param callback Callback to send the result back.
     * @see android.app.KeyguardManager#exitKeyguardSecurely(android.app.KeyguardManager.OnKeyguardExitResult)
     */
    void exitKeyguardSecurely(OnKeyguardExitResult callback);

    /**
     * Return if keyguard is currently showing.
     */
    public boolean keyguardIsShowingTq();

    /**
     * inKeyguardRestrictedKeyInputMode
     *
     * if keyguard screen is showing or in restricted key input mode (i.e. in
     * keyguard password emergency screen). When in such mode, certain keys,
     * such as the Home key and the right soft keys, don't work.
     *
     * @return true if in keyguard restricted input mode.
     */
    public boolean inKeyguardRestrictedKeyInputMode();

    /**
     * Given an orientation constant
     * ({@link android.content.pm.ActivityInfo#SCREEN_ORIENTATION_LANDSCAPE
     * ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE} or
     * {@link android.content.pm.ActivityInfo#SCREEN_ORIENTATION_PORTRAIT
     * ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}), return a surface
     * rotation.
     */
    public int rotationForOrientation(int orientation);
    
    /**
     * Called when the system is mostly done booting
     */
    public void systemReady();

    /**
     * Called when we have finished booting and can now display the home
     * screen to the user.  This wilWl happen after systemReady(), and at
     * this point the display is active.
     */
    public void enableScreenAfterBoot();
    
    /**
     * Returns true if the user's cheek has been pressed against the phone. This is 
     * determined by comparing the event's size attribute with a threshold value.
     * For example for a motion event like down or up or move, if the size exceeds
     * the threshold, it is considered as cheek press.
     * @param ev the motion event generated when the cheek is pressed 
     * against the phone
     * @return Returns true if the user's cheek has been pressed against the phone
     * screen resulting in an invalid motion event
     */
    public boolean isCheekPressedAgainstScreen(MotionEvent ev);
    
    public void setCurrentOrientation(int newOrientation);
}
