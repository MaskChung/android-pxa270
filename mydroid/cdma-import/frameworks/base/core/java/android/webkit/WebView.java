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

package android.webkit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.net.http.SslCertificate;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.text.IClipboard;
import android.text.Selection;
import android.text.Spannable;
import android.util.AttributeSet;
import android.util.Config;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.webkit.WebViewCore.EventHub;
import android.widget.AbsoluteLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.Toast;
import android.widget.ZoomControls;
import android.widget.AdapterView.OnItemClickListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * <p>A View that displays web pages. This class is the basis upon which you 
 * can roll your own web browser or simply display some online content within your Activity.
 * It uses the WebKit rendering engine to display
 * web pages and includes methods to navigate forward and backward
 * through a history, zoom in and out, perform text searches and more.</p>
 * <p>Note that, in order for your Activity to access the Internet and load web pages
 * in a WebView, you must add the <var>INTERNET</var> permissions to your 
 * Android Manifest file:</p>
 * <pre>&lt;uses-permission android:name="android.permission.INTERNET" /></pre>
 * <p>This must be a child of the <code>&lt;manifest></code> element.</p>
 */
public class WebView extends AbsoluteLayout 
        implements ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewGroup.OnHierarchyChangeListener {

    // keep debugging parameters near the top of the file
    static final String LOGTAG = "webview";
    static final boolean DEBUG = false;
    static final boolean LOGV_ENABLED = DEBUG ? Config.LOGD : Config.LOGV;

    private class ExtendedZoomControls extends RelativeLayout {
        public ExtendedZoomControls(Context context, AttributeSet attrs) {
            super(context, attrs);
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(com.android.internal.R.layout.zoom_magnify, this 
                    , true);
            mZoomControls = (ZoomControls) findViewById
                    (com.android.internal.R.id.zoomControls);
            mZoomMagnify = (ImageView) findViewById
                    (com.android.internal.R.id.zoomMagnify);
        }
        
        public void show(boolean canZoomOut) {
            mZoomMagnify.setVisibility(canZoomOut ? View.VISIBLE : View.GONE);
            fade(View.VISIBLE, 0.0f, 1.0f);
        }
        
        public void hide() {
            fade(View.GONE, 1.0f, 0.0f);
        }
        
        private void fade(int visibility, float startAlpha, float endAlpha) {
            AlphaAnimation anim = new AlphaAnimation(startAlpha, endAlpha);
            anim.setDuration(500);
            startAnimation(anim);
            setVisibility(visibility);
        }
        
        public void setIsZoomMagnifyEnabled(boolean isEnabled) {
            mZoomMagnify.setEnabled(isEnabled);
        }
        
        public boolean hasFocus() {
            return mZoomControls.hasFocus() || mZoomMagnify.hasFocus();
        }
        
        public void setOnZoomInClickListener(OnClickListener listener) {
            mZoomControls.setOnZoomInClickListener(listener);
        }
            
        public void setOnZoomOutClickListener(OnClickListener listener) {
            mZoomControls.setOnZoomOutClickListener(listener);
        }
            
        public void setOnZoomMagnifyClickListener(OnClickListener listener) {
            mZoomMagnify.setOnClickListener(listener);
        }

        ZoomControls mZoomControls;
        ImageView mZoomMagnify;
    }
    
    /**
     *  Transportation object for returning WebView across thread boundaries.
     */
    public class WebViewTransport {
        private WebView mWebview;

        /**
         * Set the WebView to the transportation object.
         * @param webview The WebView to transport.
         */
        public synchronized void setWebView(WebView webview) {
            mWebview = webview;
        }

        /**
         * Return the WebView object.
         * @return WebView The transported WebView object.
         */
        public synchronized WebView getWebView() {
            return mWebview;
        }
    }

    // A final CallbackProxy shared by WebViewCore and BrowserFrame.
    private final CallbackProxy mCallbackProxy;

    private final WebViewDatabase mDatabase;

    // SSL certificate for the main top-level page (if secure)
    private SslCertificate mCertificate;

    // Native WebView pointer that is 0 until the native object has been
    // created.
    private int mNativeClass;
    // This would be final but it needs to be set to null when the WebView is
    // destroyed.
    private WebViewCore mWebViewCore;
    // Handler for dispatching UI messages.
    /* package */ final Handler mPrivateHandler = new PrivateHandler();
    private TextDialog mTextEntry;
    // Used to ignore changes to webkit text that arrives to the UI side after
    // more key events.
    private int mTextGeneration;

    // The list of loaded plugins.
    private static PluginList sPluginList;

    /**
     * Position of the last touch event.
     */
    private float mLastTouchX;
    private float mLastTouchY;

    /**
     * Time of the last touch event.
     */
    private long mLastTouchTime;

    /**
     * Helper class to get velocity for fling
     */
    VelocityTracker mVelocityTracker;

    /**
     * Touch mode
     */
    private int mTouchMode = TOUCH_DONE_MODE;
    private static final int TOUCH_INIT_MODE = 1;
    private static final int TOUCH_DRAG_START_MODE = 2;
    private static final int TOUCH_DRAG_MODE = 3;
    private static final int TOUCH_SHORTPRESS_START_MODE = 4;
    private static final int TOUCH_SHORTPRESS_MODE = 5;
    private static final int TOUCH_DOUBLECLICK_MODE = 6;
    private static final int TOUCH_DONE_MODE = 7;
    // touch mode values specific to scale+scroll
    private static final int FIRST_SCROLL_ZOOM = 9;
    private static final int SCROLL_ZOOM_ANIMATION_IN = 9;
    private static final int SCROLL_ZOOM_ANIMATION_OUT = 10;
    private static final int SCROLL_ZOOM_OUT = 11;
    private static final int LAST_SCROLL_ZOOM = 11;
    // end of touch mode values specific to scale+scroll

    // If updateTextEntry gets called while we are out of focus, use this 
    // variable to remember to do it next time we gain focus.
    private boolean mNeedsUpdateTextEntry = false;
    
    // Whether or not to draw the focus ring.
    private boolean mDrawFocusRing = true;

    /**
     * Customizable constant
     */
    // pre-computed square of ViewConfiguration.getTouchSlop()
    private static final int TOUCH_SLOP_SQUARE =
            ViewConfiguration.getTouchSlop() * ViewConfiguration.getTouchSlop();
    // This should be ViewConfiguration.getTapTimeout()
    // But system time out is 100ms, which is too short for the browser.
    // In the browser, if it switches out of tap too soon, jump tap won't work.
    private static final int TAP_TIMEOUT = 200;
    // The duration in milliseconds we will wait to see if it is a double tap.
    // With a limited survey, the time between the first tap up and the second
    // tap down in the double tap case is around 70ms - 120ms.
    private static final int DOUBLE_TAP_TIMEOUT = 200;
    // This should be ViewConfiguration.getLongPressTimeout()
    // But system time out is 500ms, which is too short for the browser.
    // With a short timeout, it's difficult to treat trigger a short press.
    private static final int LONG_PRESS_TIMEOUT = 1000;
    // needed to avoid flinging after a pause of no movement
    private static final int MIN_FLING_TIME = 250;
    // The time that the Zoom Controls are visible before fading away
    private static final long ZOOM_CONTROLS_TIMEOUT = 
            ViewConfiguration.getZoomControlsTimeout();
    // Wait a short time before sending kit focus message, in case 
    // the user is still moving around, to avoid rebuilding the display list
    // prematurely 
    private static final long SET_KIT_FOCUS_DELAY = 250;
    // The amount of content to overlap between two screens when going through
    // pages with the space bar, in pixels.
    private static final int PAGE_SCROLL_OVERLAP = 24;

    /**
     * These prevent calling requestLayout if either dimension is fixed. This
     * depends on the layout parameters and the measure specs.
     */
    boolean mWidthCanMeasure;
    boolean mHeightCanMeasure;

    // Remember the last dimensions we sent to the native side so we can avoid
    // sending the same dimensions more than once.
    int mLastWidthSent;
    int mLastHeightSent;

    private int mContentWidth;   // cache of value from WebViewCore
    private int mContentHeight;  // cache of value from WebViewCore

    // Need to have the separate control for horizontal and vertical scrollbar 
    // style than the View's single scrollbar style
    private boolean mOverlayHorizontalScrollbar = true;
    private boolean mOverlayVerticalScrollbar = false;

    // our standard speed. this way small distances will be traversed in less
    // time than large distances, but we cap the duration, so that very large
    // distances won't take too long to get there.
    private static final int STD_SPEED = 480;  // pixels per second
    // time for the longest scroll animation
    private static final int MAX_DURATION = 750;   // milliseconds
    private Scroller mScroller;

    private boolean mWrapContent;

    // The View containing the zoom controls
    private ExtendedZoomControls mZoomControls;
    private Runnable mZoomControlRunnable;

    // true if we should call webcore to draw the content, false means we have
    // requested something but it isn't ready to draw yet.
    private WebViewCore.FocusData mFocusData;
    /**
     * Private message ids
     */
    private static final int REMEMBER_PASSWORD = 1;
    private static final int NEVER_REMEMBER_PASSWORD = 2;
    private static final int SWITCH_TO_SHORTPRESS = 3;
    private static final int SWITCH_TO_LONGPRESS = 4;
    private static final int RELEASE_SINGLE_TAP = 5;
    private static final int UPDATE_TEXT_ENTRY_ADAPTER = 6;
    private static final int SWITCH_TO_ENTER = 7;
    private static final int RESUME_WEBCORE_UPDATE = 8;

    //! arg1=x, arg2=y
    static final int SCROLL_TO_MSG_ID               = 10;
    static final int SCROLL_BY_MSG_ID               = 11;
    //! arg1=x, arg2=y
    static final int SPAWN_SCROLL_TO_MSG_ID         = 12;
    //! arg1=x, arg2=y
    static final int SYNC_SCROLL_TO_MSG_ID          = 13;
    static final int NEW_PICTURE_MSG_ID             = 14;
    static final int UPDATE_TEXT_ENTRY_MSG_ID       = 15;
    static final int WEBCORE_INITIALIZED_MSG_ID     = 16;
    static final int UPDATE_TEXTFIELD_TEXT_MSG_ID   = 17;
    static final int DID_FIRST_LAYOUT_MSG_ID        = 18;
    static final int RECOMPUTE_FOCUS_MSG_ID         = 19;
    static final int NOTIFY_FOCUS_SET_MSG_ID        = 20;
    static final int MARK_NODE_INVALID_ID           = 21;
    static final int UPDATE_CLIPBOARD               = 22;
    static final int LONG_PRESS_TRACKBALL           = 24;

    // width which view is considered to be fully zoomed out
    static final int ZOOM_OUT_WIDTH = 1024;

    private static final float DEFAULT_MAX_ZOOM_SCALE = 4;
    private static final float DEFAULT_MIN_ZOOM_SCALE = 0.25f;
    // scale limit, which can be set through viewport meta tag in the web page
    private float mMaxZoomScale = DEFAULT_MAX_ZOOM_SCALE;
    private float mMinZoomScale = DEFAULT_MIN_ZOOM_SCALE;

    // initial scale in percent. 0 means using default.
    private int mInitialScale = 0;

    // computed scale and inverse, from mZoomWidth.
    private float mActualScale = 1;
    private float mInvActualScale = 1;
    // if this is non-zero, it is used on drawing rather than mActualScale
    private float mZoomScale;
    private float mInvInitialZoomScale;
    private float mInvFinalZoomScale;
    private long mZoomStart;
    private static final int ZOOM_ANIMATION_LENGTH = 500;

    private boolean mUserScroll = false;

    private int mSnapScrollMode = SNAP_NONE;
    private static final int SNAP_NONE = 1;
    private static final int SNAP_X = 2;
    private static final int SNAP_Y = 3;
    private static final int SNAP_X_LOCK = 4;
    private static final int SNAP_Y_LOCK = 5;
    private boolean mSnapPositive;
    
    // Used to match key downs and key ups
    private boolean mGotKeyDown;

    /**
     * URI scheme for telephone number
     */
    public static final String SCHEME_TEL = "tel:";
    /**
     * URI scheme for email address
     */
    public static final String SCHEME_MAILTO = "mailto:";
    /**
     * URI scheme for map address
     */
    public static final String SCHEME_GEO = "geo:0,0?q=";
    
    private int mBackgroundColor = Color.WHITE;

    // Used to notify listeners of a new picture.
    private PictureListener mPictureListener;
    /**
     * Interface to listen for new pictures as they change.
     */
    public interface PictureListener {
        /**
         * Notify the listener that the picture has changed.
         * @param view The WebView that owns the picture.
         * @param picture The new picture.
         */
        public void onNewPicture(WebView view, Picture picture);
    }

    public class HitTestResult {
        /**
         * Default HitTestResult, where the target is unknown
         */
        public static final int UNKNOWN_TYPE = 0;
        /**
         * HitTestResult for hitting a HTML::a tag
         */
        public static final int ANCHOR_TYPE = 1;
        /**
         * HitTestResult for hitting a phone number
         */
        public static final int PHONE_TYPE = 2;
        /**
         * HitTestResult for hitting a map address
         */
        public static final int GEO_TYPE = 3;
        /**
         * HitTestResult for hitting an email address
         */
        public static final int EMAIL_TYPE = 4;
        /**
         * HitTestResult for hitting an HTML::img tag
         */
        public static final int IMAGE_TYPE = 5;
        /**
         * HitTestResult for hitting a HTML::a tag which contains HTML::img
         */
        public static final int IMAGE_ANCHOR_TYPE = 6;
        /**
         * HitTestResult for hitting a HTML::a tag with src=http
         */
        public static final int SRC_ANCHOR_TYPE = 7;
        /**
         * HitTestResult for hitting a HTML::a tag with src=http + HTML::img
         */
        public static final int SRC_IMAGE_ANCHOR_TYPE = 8;
        /**
         * HitTestResult for hitting an edit text area
         */
        public static final int EDIT_TEXT_TYPE = 9;

        private int mType;
        private String mExtra;

        HitTestResult() {
            mType = UNKNOWN_TYPE;
        }

        private void setType(int type) {
            mType = type;
        }

        private void setExtra(String extra) {
            mExtra = extra;
        }

        public int getType() {
            return mType;
        }

        public String getExtra() {
            return mExtra;
        }
    }

    /**
     * Construct a new WebView with a Context object.
     * @param context A Context object used to access application assets.
     */
    public WebView(Context context) {
        this(context, null);
    }

    /**
     * Construct a new WebView with layout parameters.
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public WebView(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.webViewStyle);
    }

    /**
     * Construct a new WebView with layout parameters and a default style.
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public WebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();

        TypedArray a = context.obtainStyledAttributes(
                com.android.internal.R.styleable.View);
        initializeScrollbars(a);
        a.recycle();

        mCallbackProxy = new CallbackProxy(context, this);
        mWebViewCore = new WebViewCore(context, this, mCallbackProxy);
        mDatabase = WebViewDatabase.getInstance(context);
        mFocusData = new WebViewCore.FocusData();
        mFocusData.mFrame = 0;
        mFocusData.mNode = 0;
        mFocusData.mX = 0;
        mFocusData.mY = 0;
        mScroller = new Scroller(context);
    }

    private void init() {
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setLongClickable(true);

        // should be conditional on if we're in the browser activity? 
        setHorizontalScrollBarEnabled(true);
        setVerticalScrollBarEnabled(true);
    }

    /* package */ boolean onSavePassword(String host, String username,
            String password, final Message resumeMsg) {
       boolean rVal = false;
       if (resumeMsg == null) {
           // null resumeMsg implies saving password silently
           mDatabase.setUsernamePassword(host, username, password);
       } else {
            final Message remember = mPrivateHandler.obtainMessage(
                    REMEMBER_PASSWORD);
            remember.getData().putString("host", host);
            remember.getData().putString("username", username);
            remember.getData().putString("password", password);
            remember.obj = resumeMsg;

            final Message neverRemember = mPrivateHandler.obtainMessage(
                    NEVER_REMEMBER_PASSWORD);
            neverRemember.getData().putString("host", host);
            neverRemember.getData().putString("username", username);
            neverRemember.getData().putString("password", password);
            neverRemember.obj = resumeMsg;

            new AlertDialog.Builder(getContext())
                    .setTitle(com.android.internal.R.string.save_password_label)
                    .setMessage(com.android.internal.R.string.save_password_message)
                    .setPositiveButton(com.android.internal.R.string.save_password_notnow,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            resumeMsg.sendToTarget();
                        }
                    })
                    .setNeutralButton(com.android.internal.R.string.save_password_remember,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            remember.sendToTarget();
                        }
                    })
                    .setNegativeButton(com.android.internal.R.string.save_password_never,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            neverRemember.sendToTarget();
                        }
                    })
                    .setOnCancelListener(new OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            resumeMsg.sendToTarget();
                        }
                    }).show();
            // Return true so that WebViewCore will pause while the dialog is
            // up.
            rVal = true;
        }
       return rVal;
    }

    @Override
    public void setScrollBarStyle(int style) {
        if (style == View.SCROLLBARS_INSIDE_INSET
                || style == View.SCROLLBARS_OUTSIDE_INSET) {
            mOverlayHorizontalScrollbar = mOverlayVerticalScrollbar = false;
        } else {
            mOverlayHorizontalScrollbar = mOverlayVerticalScrollbar = true;
        }
        super.setScrollBarStyle(style);
    }

    /**
     * Specify whether the horizontal scrollbar has overlay style.
     * @param overlay TRUE if horizontal scrollbar should have overlay style.
     */
    public void setHorizontalScrollbarOverlay(boolean overlay) {
        mOverlayHorizontalScrollbar = overlay;
    }

    /**
     * Specify whether the vertical scrollbar has overlay style.
     * @param overlay TRUE if vertical scrollbar should have overlay style.
     */
    public void setVerticalScrollbarOverlay(boolean overlay) {
        mOverlayVerticalScrollbar = overlay;
    }

    /**
     * Return whether horizontal scrollbar has overlay style
     * @return TRUE if horizontal scrollbar has overlay style.
     */
    public boolean overlayHorizontalScrollbar() {
        return mOverlayHorizontalScrollbar;
    }

    /**
     * Return whether vertical scrollbar has overlay style
     * @return TRUE if vertical scrollbar has overlay style.
     */
    public boolean overlayVerticalScrollbar() {
        return mOverlayVerticalScrollbar;
    }

    /*
     * Return the width of the view where the content of WebView should render
     * to.
     */
    private int getViewWidth() {
        if (mOverlayVerticalScrollbar) {
            return getWidth();
        } else {
            return getWidth() - getVerticalScrollbarWidth();
        }
    }

    /*
     * Return the height of the view where the content of WebView should render
     * to.
     */
    private int getViewHeight() {
        if (mOverlayHorizontalScrollbar) {
            return getHeight();
        } else {
            return getHeight() - getHorizontalScrollbarHeight();
        }
    }

    /**
     * @return The SSL certificate for the main top-level page or null if
     * there is no certificate (the site is not secure).
     */
    public SslCertificate getCertificate() {
        return mCertificate;
    }

    /**
     * Sets the SSL certificate for the main top-level page.
     */
    public void setCertificate(SslCertificate certificate) {
        // here, the certificate can be null (if the site is not secure)
        mCertificate = certificate;
    }

    //-------------------------------------------------------------------------
    // Methods called by activity
    //-------------------------------------------------------------------------

    /**
     * Save the username and password for a particular host in the WebView's
     * internal database.
     * @param host The host that required the credentials.
     * @param username The username for the given host.
     * @param password The password for the given host.
     */
    public void savePassword(String host, String username, String password) {
        mDatabase.setUsernamePassword(host, username, password);
    }

    /**
     * Set the HTTP authentication credentials for a given host and realm.
     *
     * @param host The host for the credentials.
     * @param realm The realm for the credentials.
     * @param username The username for the password. If it is null, it means
     *                 password can't be saved.
     * @param password The password
     */
    public void setHttpAuthUsernamePassword(String host, String realm,
            String username, String password) {
        mDatabase.setHttpAuthUsernamePassword(host, realm, username, password);
    }

    /**
     * Retrieve the HTTP authentication username and password for a given
     * host & realm pair
     *
     * @param host The host for which the credentials apply.
     * @param realm The realm for which the credentials apply.
     * @return String[] if found, String[0] is username, which can be null and
     *         String[1] is password. Return null if it can't find anything.
     */
    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        return mDatabase.getHttpAuthUsernamePassword(host, realm);
    }

    /**
     * Destroy the internal state of the WebView. This method should be called
     * after the WebView has been removed from the view system. No other
     * methods may be called on a WebView after destroy.
     */
    public void destroy() {
        clearTextEntry();
        getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
        if (mWebViewCore != null) {
            // Set the handlers to null before destroying WebViewCore so no
            // more messages will be posted. 
            mCallbackProxy.setWebViewClient(null);
            mCallbackProxy.setWebChromeClient(null);
            // Tell WebViewCore to destroy itself
            WebViewCore webViewCore = mWebViewCore;
            mWebViewCore = null; // prevent using partial webViewCore
            webViewCore.destroy();
            // Remove any pending messages that might not be serviced yet.
            mPrivateHandler.removeCallbacksAndMessages(null);
            mCallbackProxy.removeCallbacksAndMessages(null);
            // Wake up the WebCore thread just in case it is waiting for a
            // javascript dialog.
            synchronized (mCallbackProxy) {
                mCallbackProxy.notify();
            }
        }
        if (mNativeClass != 0) {
            nativeDestroy();
            mNativeClass = 0;
        }
    }

    /**
     * Enables platform notifications of data state and proxy changes.
     */
    public static void enablePlatformNotifications() {
        Network.enablePlatformNotifications();
    }

    /**
     * If platform notifications are enabled, this should be called
     * from onPause() or onStop().
     */
    public static void disablePlatformNotifications() {
        Network.disablePlatformNotifications();
    }

    /**
     * Save the state of this WebView used in Activity.onSaveInstanceState.
     * @param outState The Bundle to store the WebView state.
     * @return The same copy of the back/forward list used to save the state. If
     *         saveState fails, the returned list will be null.
     */
    public WebBackForwardList saveState(Bundle outState) {
        // We grab a copy of the back/forward list because a client of WebView
        // may have invalidated the history list by calling clearHistory.
        WebBackForwardList list = copyBackForwardList();
        final int currentIndex = list.getCurrentIndex();
        final int size = list.getSize();
        // We should fail saving the state if the list is empty or the index is
        // not in a valid range.
        if (currentIndex < 0 || currentIndex >= size || size == 0) {
            return null;
        }
        outState.putInt("index", currentIndex);
        // FIXME: This should just be a byte[][] instead of ArrayList but
        // Parcel.java does not have the code to handle multi-dimensional
        // arrays.
        ArrayList<byte[]> history = new ArrayList<byte[]>(size);
        for (int i = 0; i < size; i++) {
            WebHistoryItem item = list.getItemAtIndex(i);
            byte[] data = item.getFlattenedData();
            if (data == null) {
                // It would be very odd to not have any data for a given history
                // item. And we will fail to rebuild the history list without
                // flattened data.
                return null;
            }
            history.add(data);
            if (i == currentIndex) {
                Picture p = capturePicture();
                String path = mContext.getDir("thumbnails", 0).getPath()
                        + File.separatorChar + hashCode() + "_pic.save";
                File f = new File(path);
                try {
                    final FileOutputStream out = new FileOutputStream(f);
                    p.writeToStream(out);
                    out.close();
                } catch (FileNotFoundException e){
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                if (f.length() > 0) {
                    outState.putString("picture", path);
                    outState.putInt("scrollX", mScrollX);
                    outState.putInt("scrollY", mScrollY);
                    outState.putFloat("scale", mActualScale);
                }
            }
        }
        outState.putSerializable("history", history);
        if (mCertificate != null) {
            outState.putBundle("certificate",
                               SslCertificate.saveState(mCertificate));
        }
        return list;
    }

    /**
     * Restore the state of this WebView from the given map used in
     * Activity.onThaw. This method should be called to restore the state of
     * the WebView before using the object. If it is called after the WebView
     * has had a chance to build state (load pages, create a back/forward list,
     * etc.) there may be undesirable side-effects.
     * @param inState The incoming Bundle of state.
     * @return The restored back/forward list or null if restoreState failed.
     */
    public WebBackForwardList restoreState(Bundle inState) {
        WebBackForwardList returnList = null;
        if (inState.containsKey("index") && inState.containsKey("history")) {
            mCertificate = SslCertificate.restoreState(
                inState.getBundle("certificate"));

            final WebBackForwardList list = mCallbackProxy.getBackForwardList();
            final int index = inState.getInt("index");
            // We can't use a clone of the list because we need to modify the
            // shared copy, so synchronize instead to prevent concurrent
            // modifications.
            synchronized (list) {
                final List<byte[]> history =
                        (List<byte[]>) inState.getSerializable("history");
                final int size = history.size();
                // Check the index bounds so we don't crash in native code while
                // restoring the history index.
                if (index < 0 || index >= size) {
                    return null;
                }
                for (int i = 0; i < size; i++) {
                    byte[] data = history.remove(0);
                    if (data == null) {
                        // If we somehow have null data, we cannot reconstruct
                        // the item and thus our history list cannot be rebuilt.
                        return null;
                    }
                    WebHistoryItem item = new WebHistoryItem(data);
                    list.addHistoryItem(item);
                }
                if (inState.containsKey("picture")) {
                    String path = inState.getString("picture");
                    File f = new File(path);
                    if (f.exists()) {
                        Picture p = null;
                        try {
                            final FileInputStream in = new FileInputStream(f);
                            p = Picture.createFromStream(in);
                            in.close();
                            f.delete();
                        } catch (FileNotFoundException e){
                            e.printStackTrace();
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (p != null) {
                            int sx = inState.getInt("scrollX", 0);
                            int sy = inState.getInt("scrollY", 0);
                            float scale = inState.getFloat("scale", 1.0f);
                            mDrawHistory = true;
                            mHistoryPicture = p;
                            mScrollX = sx;
                            mScrollY = sy;
                            mHistoryWidth = Math.round(p.getWidth() * scale);
                            mHistoryHeight = Math.round(p.getHeight() * scale);
                            // as getWidth() / getHeight() of the view are not
                            // available yet, set up mActualScale, so that when
                            // onSizeChanged() is called, the rest will be set
                            // correctly
                            mActualScale = scale;
                            invalidate();
                        }
                    }
                }
                // Grab the most recent copy to return to the caller.
                returnList = copyBackForwardList();
                // Update the copy to have the correct index.
                returnList.setCurrentIndex(index);
            }
            // Remove all pending messages because we are restoring previous
            // state.
            mWebViewCore.removeMessages();
            // Send a restore state message.
            mWebViewCore.sendMessage(EventHub.RESTORE_STATE, index);
        }
        return returnList;
    }

    /**
     * Load the given url.
     * @param url The url of the resource to load.
     */
    public void loadUrl(String url) {
        switchOutDrawHistory();
        mWebViewCore.sendMessage(EventHub.LOAD_URL, url);
        clearTextEntry();
    }

    /**
     * Load the given data into the WebView. This will load the data into
     * WebView using the data: scheme. Content loaded through this mechanism
     * does not have the ability to load content from the network.
     * @param data A String of data in the given encoding.
     * @param mimeType The MIMEType of the data. i.e. text/html, image/jpeg
     * @param encoding The encoding of the data. i.e. utf-8, base64
     */
    public void loadData(String data, String mimeType, String encoding) {
        loadUrl("data:" + mimeType + ";" + encoding + "," + data);
    }

    /**
     * Load the given data into the WebView, use the provided URL as the base
     * URL for the content. The base URL is the URL that represents the page
     * that is loaded through this interface. As such, it is used for the
     * history entry and to resolve any relative URLs.
     * The failUrl is used if browser fails to load the data provided. If it 
     * is empty or null, and the load fails, then no history entry is created.
     * @param baseUrl Url to resolve relative paths with, if null defaults to
     *        "about:blank"
     * @param data A String of data in the given encoding.
     * @param mimeType The MIMEType of the data. i.e. text/html. If null,
     *        defaults to "text/html"
     * @param encoding The encoding of the data. i.e. utf-8, us-ascii
     * @param failUrl URL to use if the content fails to load or null.
     */
    public void loadDataWithBaseURL(String baseUrl, String data,
            String mimeType, String encoding, String failUrl) {
        
        if (baseUrl != null && baseUrl.toLowerCase().startsWith("data:")) {
            loadData(data, mimeType, encoding);
            return;
        }
        switchOutDrawHistory();
        HashMap arg = new HashMap();
        arg.put("baseUrl", baseUrl);
        arg.put("data", data);
        arg.put("mimeType", mimeType);
        arg.put("encoding", encoding);
        arg.put("failUrl", failUrl);
        mWebViewCore.sendMessage(EventHub.LOAD_DATA, arg);
        clearTextEntry();
    }

    /**
     * Stop the current load.
     */
    public void stopLoading() {
        // TODO: should we clear all the messages in the queue before sending
        // STOP_LOADING?
        switchOutDrawHistory();
        mWebViewCore.sendMessage(EventHub.STOP_LOADING);
    }

    /**
     * Reload the current url.
     */
    public void reload() {
        switchOutDrawHistory();
        mWebViewCore.sendMessage(EventHub.RELOAD);
    }

    /**
     * Return true if this WebView has a back history item.
     * @return True iff this WebView has a back history item.
     */
    public boolean canGoBack() {
        WebBackForwardList l = mCallbackProxy.getBackForwardList();
        synchronized (l) {
            if (l.getClearPending()) {
                return false;
            } else {
                return l.getCurrentIndex() > 0;
            }
        }
    }

    /**
     * Go back in the history of this WebView.
     */
    public void goBack() {
        goBackOrForward(-1);
    }

    /**
     * Return true if this WebView has a forward history item.
     * @return True iff this Webview has a forward history item.
     */
    public boolean canGoForward() {
        WebBackForwardList l = mCallbackProxy.getBackForwardList();
        synchronized (l) {
            if (l.getClearPending()) {
                return false;
            } else {
                return l.getCurrentIndex() < l.getSize() - 1;
            }
        }
    }

    /**
     * Go forward in the history of this WebView.
     */
    public void goForward() {
        goBackOrForward(1);
    }

    /**
     * Return true if the page can go back or forward the given
     * number of steps.
     * @param steps The negative or positive number of steps to move the
     *              history.
     */
    public boolean canGoBackOrForward(int steps) {
        WebBackForwardList l = mCallbackProxy.getBackForwardList();
        synchronized (l) {
            if (l.getClearPending()) {
                return false;
            } else {
                int newIndex = l.getCurrentIndex() + steps;
                return newIndex >= 0 && newIndex < l.getSize();
            }
        }
    }

    /**
     * Go to the history item that is the number of steps away from
     * the current item. Steps is negative if backward and positive
     * if forward.
     * @param steps The number of steps to take back or forward in the back
     *              forward list.
     */
    public void goBackOrForward(int steps) {
        goBackOrForward(steps, false);
    }

    private void goBackOrForward(int steps, boolean ignoreSnapshot) {
        // every time we go back or forward, we want to reset the
        // WebView certificate:
        // if the new site is secure, we will reload it and get a
        // new certificate set;
        // if the new site is not secure, the certificate must be
        // null, and that will be the case
        mCertificate = null;
        if (steps != 0) {
            clearTextEntry();
            mWebViewCore.sendMessage(EventHub.GO_BACK_FORWARD, steps,
                    ignoreSnapshot ? 1 : 0);
        }
    }
    
    private boolean extendScroll(int y) {
        int finalY = mScroller.getFinalY();
        int newY = pinLocY(finalY + y);
        if (newY == finalY) return false;
        mScroller.setFinalY(newY);
        mScroller.extendDuration(computeDuration(0, y));
        return true;
    }
    
    /**
     * Scroll the contents of the view up by half the view size
     * @param top true to jump to the top of the page
     * @return true if the page was scrolled
     */
    public boolean pageUp(boolean top) {
        if (mNativeClass == 0) {
            return false;
        }
        nativeClearFocus(-1, -1);
        if (top) {
            // go to the top of the document
            return pinScrollTo(mScrollX, 0, true);
        }
        // Page up
        int h = getHeight();
        int y;
        if (h > 2 * PAGE_SCROLL_OVERLAP) {
            y = -h + PAGE_SCROLL_OVERLAP;
        } else {
            y = -h / 2;
        }
        mUserScroll = true;
        return mScroller.isFinished() ? pinScrollBy(0, y, true) 
                : extendScroll(y);
    }
    
    /**
     * Scroll the contents of the view down by half the page size
     * @param bottom true to jump to bottom of page
     * @return true if the page was scrolled
     */
    public boolean pageDown(boolean bottom) {
        if (mNativeClass == 0) {
            return false;
        }
        nativeClearFocus(-1, -1);
        if (bottom) {
            return pinScrollTo(mScrollX, mContentHeight, true);
        }
        // Page down.
        int h = getHeight();
        int y;
        if (h > 2 * PAGE_SCROLL_OVERLAP) {
            y = h - PAGE_SCROLL_OVERLAP;
        } else {
            y = h / 2;
        }
        mUserScroll = true;
        return mScroller.isFinished() ? pinScrollBy(0, y, true) 
                : extendScroll(y);
    }

    /**
     * Clear the view so that onDraw() will draw nothing but white background,
     * and onMeasure() will return 0 if MeasureSpec is not MeasureSpec.EXACTLY
     */
    public void clearView() {
        mContentWidth = 0;
        mContentHeight = 0;
        mWebViewCore.clearContentPicture();
    }
    
    /**
     * Return a new picture that captures the current display of the webview.
     * This is a copy of the display, and will be unaffected if the webview
     * later loads a different URL.
     *
     * @return a picture containing the current contents of the view. Note this
     *         picture is of the entire document, and is not restricted to the
     *         bounds of the view.
     */
    public Picture capturePicture() {
        if (null == mWebViewCore) return null; // check for out of memory tab 
        return mWebViewCore.copyContentPicture();
    }

    /**
     *  Return true if the browser is displaying a TextView for text input.
     */
    private boolean inEditingMode() {
        return mTextEntry != null && mTextEntry.getParent() != null
                && mTextEntry.hasFocus();
    }

    private void clearTextEntry() {
        if (inEditingMode()) {
            mTextEntry.remove();
        }
    }

    /** 
     * Return the current scale of the WebView
     * @return The current scale.
     */
    public float getScale() {
        return mActualScale;
    }

    /**
     * Set the initial scale for the WebView. 0 means default. If
     * {@link WebSettings#getUseWideViewPort()} is true, it zooms out all the
     * way. Otherwise it starts with 100%. If initial scale is greater than 0,
     * WebView starts will this value as initial scale.
     *
     * @param scaleInPercent The initial scale in percent.
     */
    public void setInitialScale(int scaleInPercent) {
        mInitialScale = scaleInPercent;
    }

    /**
     * Invoke the graphical zoom picker widget for this WebView. This will
     * result in the zoom widget appearing on the screen to control the zoom
     * level of this WebView.
     */
    public void invokeZoomPicker() {
        if (!getSettings().supportZoom()) {
            Log.w(LOGTAG, "This WebView doesn't support zoom.");
            return;
        }
        clearTextEntry();
        ExtendedZoomControls zoomControls = (ExtendedZoomControls) 
                getZoomControls();
        zoomControls.show(canZoomScrollOut());
        zoomControls.requestFocus();
        mPrivateHandler.removeCallbacks(mZoomControlRunnable);
        mPrivateHandler.postDelayed(mZoomControlRunnable,
                ZOOM_CONTROLS_TIMEOUT);
    }

    /**
     * Return a HitTestResult based on the current focus node. If a HTML::a tag
     * is found, the HitTestResult type is set to ANCHOR_TYPE and the url has to
     * be retrieved through {@link #requestFocusNodeHref} asynchronously. If a
     * HTML::img tag is found, the HitTestResult type is set to IMAGE_TYPE and
     * the url has to be retrieved through {@link #requestFocusNodeHref}
     * asynchronously. If a phone number is found, the HitTestResult type is set
     * to PHONE_TYPE and the phone number is set in the "extra" field of
     * HitTestResult. If a map address is found, the HitTestResult type is set
     * to GEO_TYPE and the address is set in the "extra" field of HitTestResult.
     * If an email address is found, the HitTestResult type is set to EMAIL_TYPE
     * and the email is set in the "extra" field of HitTestResult. Otherwise,
     * HitTestResult type is set to UNKNOWN_TYPE.
     */
    public HitTestResult getHitTestResult() {
        if (mNativeClass == 0) {
            return null;
        }

        HitTestResult result = new HitTestResult();

        if (nativeUpdateFocusNode()) {
            FocusNode node = mFocusNode;
            if (node.mIsAnchor && node.mText == null) {
                result.setType(HitTestResult.ANCHOR_TYPE);
            } else if (node.mIsTextField || node.mIsTextArea) {
                result.setType(HitTestResult.EDIT_TEXT_TYPE);
            } else {
                String text = node.mText;
                if (text != null) {
                    if (text.startsWith(SCHEME_TEL)) {
                        result.setType(HitTestResult.PHONE_TYPE);
                        result.setExtra(text.substring(SCHEME_TEL.length()));
                    } else if (text.startsWith(SCHEME_MAILTO)) {
                        result.setType(HitTestResult.EMAIL_TYPE);
                        result.setExtra(text.substring(SCHEME_MAILTO.length()));
                    } else if (text.startsWith(SCHEME_GEO)) {
                        result.setType(HitTestResult.GEO_TYPE);
                        result.setExtra(URLDecoder.decode(text
                                .substring(SCHEME_GEO.length())));
                    }
                }
            }
        }
        int type = result.getType();
        if (type == HitTestResult.UNKNOWN_TYPE
                || type == HitTestResult.ANCHOR_TYPE) {
            // Now check to see if it is an image.
            int contentX = viewToContent((int) mLastTouchX + mScrollX);
            int contentY = viewToContent((int) mLastTouchY + mScrollY);
            if (nativeIsImage(contentX, contentY)) {
                result.setType(type == HitTestResult.UNKNOWN_TYPE ? 
                        HitTestResult.IMAGE_TYPE : 
                        HitTestResult.IMAGE_ANCHOR_TYPE);
            }
            if (nativeHasSrcUrl()) {
                result.setType(result.getType() == HitTestResult.ANCHOR_TYPE ? 
                        HitTestResult.SRC_ANCHOR_TYPE : 
                        HitTestResult.SRC_IMAGE_ANCHOR_TYPE);
            }
        }
        return result;
    }

    /**
     * Request the href of an anchor element due to getFocusNodePath returning
     * "href." If hrefMsg is null, this method returns immediately and does not
     * dispatch hrefMsg to its target.
     * 
     * @param hrefMsg This message will be dispatched with the result of the
     *            request as the data member with "url" as key. The result can
     *            be null.
     */
    public void requestFocusNodeHref(Message hrefMsg) {
        if (hrefMsg == null || mNativeClass == 0) {
            return;
        }
        if (nativeUpdateFocusNode()) {
            FocusNode node = mFocusNode;
            if (node.mIsAnchor) {
                mWebViewCore.sendMessage(EventHub.REQUEST_FOCUS_HREF,
                        node.mFramePointer, node.mNodePointer, hrefMsg);
            }
        }
    }

    /**
     * Request the url of the image last touched by the user. msg will be sent
     * to its target with a String representing the url as its object.
     * 
     * @param msg This message will be dispatched with the result of the request
     *            as the data member with "url" as key. The result can be null.
     */
    public void requestImageRef(Message msg) {
        if (msg == null || mNativeClass == 0) {
            return;
        }
        int contentX = viewToContent((int) mLastTouchX + mScrollX);
        int contentY = viewToContent((int) mLastTouchY + mScrollY);
        mWebViewCore.sendMessage(EventHub.REQUEST_IMAGE_HREF, contentX,
                contentY, msg);
    }

    private static int pinLoc(int x, int viewMax, int docMax) {
//        Log.d(LOGTAG, "-- pinLoc " + x + " " + viewMax + " " + docMax);
        if (docMax < viewMax) {   // the doc has room on the sides for "blank"
            x = -(viewMax - docMax) >> 1;
//            Log.d(LOGTAG, "--- center " + x);
        } else if (x < 0) {
            x = 0;
//            Log.d(LOGTAG, "--- zero");
        } else if (x + viewMax > docMax) {
            x = docMax - viewMax;
//            Log.d(LOGTAG, "--- pin " + x);
        }
        return x;
    }

    // Expects x in view coordinates
    private int pinLocX(int x) {
        return pinLoc(x, getViewWidth(), computeHorizontalScrollRange());
    }

    // Expects y in view coordinates
    private int pinLocY(int y) {
        return pinLoc(y, getViewHeight(), computeVerticalScrollRange());
    }

    /*package*/ int viewToContent(int x) {
        return Math.round(x * mInvActualScale);
    }

    private int contentToView(int x) {
        return Math.round(x * mActualScale);
    }

    /* call from webcoreview.draw(), so we're still executing in the UI thread
    */
    private void recordNewContentSize(int w, int h, boolean updateLayout) {

        // premature data from webkit, ignore
        if ((w | h) == 0) {
            return;
        }
        
        // don't abort a scroll animation if we didn't change anything
        if (mContentWidth != w || mContentHeight != h) {
            // record new dimensions
            mContentWidth = w;
            mContentHeight = h;
            // If history Picture is drawn, don't update scroll. They will be
            // updated when we get out of that mode.
            if (!mDrawHistory) {
                // repin our scroll, taking into account the new content size
                int oldX = mScrollX;
                int oldY = mScrollY;
                mScrollX = pinLocX(mScrollX);
                mScrollY = pinLocY(mScrollY);
                // android.util.Log.d("skia", "recordNewContentSize -
                // abortAnimation");
                mScroller.abortAnimation(); // just in case
                if (oldX != mScrollX || oldY != mScrollY) {
                    sendOurVisibleRect();
                }
            }
        }
        contentSizeChanged(updateLayout);
    }

    private void setNewZoomScale(float scale, boolean force) {
        if (scale < mMinZoomScale) {
            scale = mMinZoomScale;
        } else if (scale > mMaxZoomScale) {
            scale = mMaxZoomScale;
        }
        if (scale != mActualScale || force) {
            if (mDrawHistory) {
                // If history Picture is drawn, don't update scroll. They will
                // be updated when we get out of that mode.
                if (scale != mActualScale) {
                    mCallbackProxy.onScaleChanged(mActualScale, scale);
                }
                mActualScale = scale;
                mInvActualScale = 1 / scale;
                sendViewSizeZoom();
            } else {
                // update our scroll so we don't appear to jump
                // i.e. keep the center of the doc in the center of the view

                int oldX = mScrollX;
                int oldY = mScrollY;
                float ratio = scale * mInvActualScale;   // old inverse
                float sx = ratio * oldX + (ratio - 1) * getViewWidth() * 0.5f;
                float sy = ratio * oldY + (ratio - 1) * getViewHeight() * 0.5f;

                // now update our new scale and inverse
                if (scale != mActualScale) {
                    mCallbackProxy.onScaleChanged(mActualScale, scale);
                }
                mActualScale = scale;
                mInvActualScale = 1 / scale;

                // as we don't have animation for scaling, don't do animation 
                // for scrolling, as it causes weird intermediate state
                //        pinScrollTo(Math.round(sx), Math.round(sy));
                mScrollX = pinLocX(Math.round(sx));
                mScrollY = pinLocY(Math.round(sy));

                sendViewSizeZoom();
                sendOurVisibleRect();
            }
        }
    }

    // Used to avoid sending many visible rect messages.
    private Rect mLastVisibleRectSent;

    private Rect sendOurVisibleRect() {
        Rect rect = new Rect();
        calcOurContentVisibleRect(rect);
        // Rect.equals() checks for null input.
        if (!rect.equals(mLastVisibleRectSent)) {
            mWebViewCore.sendMessage(EventHub.SET_VISIBLE_RECT, rect);
            mLastVisibleRectSent = rect;
        }
        return rect;
    }

    // Sets r to be the visible rectangle of our webview in view coordinates
    private void calcOurVisibleRect(Rect r) {
        Point p = new Point();
        getGlobalVisibleRect(r, p);
        r.offset(-p.x, -p.y);
    }

    // Sets r to be our visible rectangle in content coordinates
    private void calcOurContentVisibleRect(Rect r) {
        calcOurVisibleRect(r);
        r.left = viewToContent(r.left);
        r.top = viewToContent(r.top);
        r.right = viewToContent(r.right);
        r.bottom = viewToContent(r.bottom);
    }

    /**
     * Compute unzoomed width and height, and if they differ from the last
     * values we sent, send them to webkit (to be used has new viewport)
     *
     * @return true if new values were sent
     */
    private boolean sendViewSizeZoom() {
        int newWidth = Math.round(getViewWidth() * mInvActualScale);
        int newHeight = Math.round(getViewHeight() * mInvActualScale);
        /*
         * Because the native side may have already done a layout before the
         * View system was able to measure us, we have to send a height of 0 to
         * remove excess whitespace when we grow our width. This will trigger a
         * layout and a change in content size. This content size change will
         * mean that contentSizeChanged will either call this method directly or
         * indirectly from onSizeChanged.
         */
        if (newWidth > mLastWidthSent && mWrapContent) {
            newHeight = 0;
        }
        // Avoid sending another message if the dimensions have not changed.
        if (newWidth != mLastWidthSent || newHeight != mLastHeightSent) {
            mWebViewCore.sendMessage(EventHub.VIEW_SIZE_CHANGED,
                    newWidth, newHeight, new Float(mActualScale));
            mLastWidthSent = newWidth;
            mLastHeightSent = newHeight;
            return true;
        }
        return false;
    }

    @Override
    protected int computeHorizontalScrollRange() {
        if (mDrawHistory) {
            return mHistoryWidth;
        } else {
            return contentToView(mContentWidth);
        }
    }

    @Override
    protected int computeVerticalScrollRange() {
        if (mDrawHistory) {
            return mHistoryHeight;
        } else {
            return contentToView(mContentHeight);
        }
    }

    /**
     * Get the url for the current page. This is not always the same as the url
     * passed to WebViewClient.onPageStarted because although the load for
     * that url has begun, the current page may not have changed.
     * @return The url for the current page.
     */
    public String getUrl() {
        WebHistoryItem h = mCallbackProxy.getBackForwardList().getCurrentItem();
        return h != null ? h.getUrl() : null;
    }

    /**
     * Get the title for the current page. This is the title of the current page
     * until WebViewClient.onReceivedTitle is called.
     * @return The title for the current page.
     */
    public String getTitle() {
        WebHistoryItem h = mCallbackProxy.getBackForwardList().getCurrentItem();
        return h != null ? h.getTitle() : null;
    }

    /**
     * Get the favicon for the current page. This is the favicon of the current
     * page until WebViewClient.onReceivedIcon is called.
     * @return The favicon for the current page.
     */
    public Bitmap getFavicon() {
        WebHistoryItem h = mCallbackProxy.getBackForwardList().getCurrentItem();
        return h != null ? h.getFavicon() : null;
    }

    /**
     * Get the progress for the current page.
     * @return The progress for the current page between 0 and 100.
     */
    public int getProgress() {
        return mCallbackProxy.getProgress();
    }
    
    /**
     * @return the height of the HTML content.
     */
    public int getContentHeight() {
        return mContentHeight;
    }

    /**
     * Pause all layout, parsing, and javascript timers. This can be useful if
     * the WebView is not visible or the application has been paused.
     */
    public void pauseTimers() {
        mWebViewCore.sendMessage(EventHub.PAUSE_TIMERS);
    }

    /**
     * Resume all layout, parsing, and javascript timers. This will resume
     * dispatching all timers.
     */
    public void resumeTimers() {
        mWebViewCore.sendMessage(EventHub.RESUME_TIMERS);
    }

    /**
     * Clear the resource cache. This will cause resources to be re-downloaded
     * if accessed again.
     * <p>
     * Note: this really needs to be a static method as it clears cache for all
     * WebView. But we need mWebViewCore to send message to WebCore thread, so
     * we can't make this static.
     */
    public void clearCache(boolean includeDiskFiles) {
        mWebViewCore.sendMessage(EventHub.CLEAR_CACHE,
                includeDiskFiles ? 1 : 0, 0);
    }

    /**
     * Make sure that clearing the form data removes the adapter from the
     * currently focused textfield if there is one.
     */
    public void clearFormData() {
        if (inEditingMode()) {
            ArrayAdapter<String> adapter = null;
            mTextEntry.setAdapter(adapter);
        }
    }

    /**
     * Tell the WebView to clear its internal back/forward list.
     */
    public void clearHistory() {
        mCallbackProxy.getBackForwardList().setClearPending();
        mWebViewCore.sendMessage(EventHub.CLEAR_HISTORY);
    }

    /**
     * Clear the SSL preferences table stored in response to proceeding with SSL
     * certificate errors.
     */
    public void clearSslPreferences() {
        mWebViewCore.sendMessage(EventHub.CLEAR_SSL_PREF_TABLE);
    }

    /**
     * Return the WebBackForwardList for this WebView. This contains the
     * back/forward list for use in querying each item in the history stack.
     * This is a copy of the private WebBackForwardList so it contains only a
     * snapshot of the current state. Multiple calls to this method may return
     * different objects. The object returned from this method will not be
     * updated to reflect any new state.
     */
    public WebBackForwardList copyBackForwardList() {
        return mCallbackProxy.getBackForwardList().clone();
    }

    /*
     * Making the find methods private since we are disabling for 1.0
     *
     * Find and highlight the next occurance of the String find, beginning with
     * the current selection. Wraps the page infinitely, and scrolls. When
     * WebCore determines whether it has found it, the response message is sent
     * to its target with true(1) or false(0) as arg1 depending on whether the
     * text was found.
     * @param find String to find.
     * @param response A Message object that will be dispatched with the result
     *                 as the arg1 member. A result of 1 means the search
     *                 succeeded.
     */
    private void findNext(String find, Message response) {
        if (response == null) {
            return;
        }
        Message m = Message.obtain(null, EventHub.FIND, 1, 0, response);
        m.getData().putString("find", find);
        mWebViewCore.sendMessage(m);
    }

    /*
     * Making the find methods private since we are disabling for 1.0
     *
     * Find and highlight the previous occurance of the String find, beginning
     * with the current selection.
     * @param find String to find.
     * @param response A Message object that will be dispatched with the result
     *                 as the arg1 member. A result of 1 means the search
     *                 succeeded.
     */
    private void findPrevious(String find, Message response) {
        if (response == null) {
            return;
        }
        Message m = Message.obtain(null, EventHub.FIND, -1, 0, response);
        m.getData().putString("find", find);
        mWebViewCore.sendMessage(m);
    }

    /*
     * Making the find methods private since we are disabling for 1.0
     *
     * Find and highlight the first occurance of find, beginning with the start
     * of the page.
     * @param find String to find.
     * @param response A Message object that will be dispatched with the result
     *                 as the arg1 member. A result of 1 means the search
     *                 succeeded.
     */
    private void findFirst(String find, Message response) {
        if (response == null) {
            return;
        }
        Message m = Message.obtain(null, EventHub.FIND, 0, 0, response);
        m.getData().putString("find", find);
        mWebViewCore.sendMessage(m);
    }

    /*
     * Making the find methods private since we are disabling for 1.0
     *
     * Find all instances of find on the page and highlight them.
     * @param find  String to find.
     * @param response A Message object that will be dispatched with the result
     *                 as the arg1 member.  The result will be the number of
     *                 matches to the String find.
     */
    private void findAll(String find, Message response) {
        if (response == null) {
            return;
        }
        Message m = Message.obtain(null, EventHub.FIND_ALL, 0, 0, response);
        m.getData().putString("find", find);
        mWebViewCore.sendMessage(m);
    }
    
    /**
     * Return the first substring consisting of the address of a physical 
     * location. Currently, only addresses in the United States are detected,
     * and consist of:
     * - a house number
     * - a street name
     * - a street type (Road, Circle, etc), either spelled out or abbreviated
     * - a city name
     * - a state or territory, either spelled out or two-letter abbr.
     * - an optional 5 digit or 9 digit zip code.
     *
     * All names must be correctly capitalized, and the zip code, if present,
     * must be valid for the state. The street type must be a standard USPS
     * spelling or abbreviation. The state or territory must also be spelled
     * or abbreviated using USPS standards. The house number may not exceed 
     * five digits.
     * @param addr The string to search for addresses.
     *
     * @return the address, or if no address is found, return null.
     */
    public static String findAddress(String addr) {
        return WebViewCore.nativeFindAddress(addr);
    }

    /*
     * Making the find methods private since we are disabling for 1.0
     *
     * Clear the highlighting surrounding text matches created by findAll.
     */
    private void clearMatches() {
        mWebViewCore.sendMessage(EventHub.CLEAR_MATCHES);
    }

    /**
     * Query the document to see if it contains any image references. The
     * message object will be dispatched with arg1 being set to 1 if images
     * were found and 0 if the document does not reference any images.
     * @param response The message that will be dispatched with the result.
     */
    public void documentHasImages(Message response) {
        if (response == null) {
            return;
        }
        mWebViewCore.sendMessage(EventHub.DOC_HAS_IMAGES, response);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int oldX = mScrollX;
            int oldY = mScrollY;
            mScrollX = mScroller.getCurrX();
            mScrollY = mScroller.getCurrY();
            postInvalidate();  // So we draw again
            if (oldX != mScrollX || oldY != mScrollY) {
                // as onScrollChanged() is not called, sendOurVisibleRect()
                // needs to be call explicitly
                sendOurVisibleRect();
            }
        } else {
            super.computeScroll();
        }
    }

    private static int computeDuration(int dx, int dy) {
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        int duration = distance * 1000 / STD_SPEED;
        return Math.min(duration, MAX_DURATION);
    }

    // helper to pin the scrollBy parameters (already in view coordinates)
    // returns true if the scroll was changed
    private boolean pinScrollBy(int dx, int dy, boolean animate) {
        return pinScrollTo(mScrollX + dx, mScrollY + dy, animate);
    }

    // helper to pin the scrollTo parameters (already in view coordinates)
    // returns true if the scroll was changed
    private boolean pinScrollTo(int x, int y, boolean animate) {
        x = pinLocX(x);
        y = pinLocY(y);
        int dx = x - mScrollX;
        int dy = y - mScrollY;

        if ((dx | dy) == 0) {
            return false;
        }

        if (true && animate) {
            //        Log.d(LOGTAG, "startScroll: " + dx + " " + dy);

            mScroller.startScroll(mScrollX, mScrollY, dx, dy,
                                  computeDuration(dx, dy));
            invalidate();
        } else {
            mScroller.abortAnimation(); // just in case
            scrollTo(x, y);
        }
        return true;
    }

    // Scale from content to view coordinates, and pin.
    // Also called by jni webview.cpp
    private void setContentScrollBy(int cx, int cy) {
        if (mDrawHistory) {
            // disallow WebView to change the scroll position as History Picture
            // is used in the view system.
            // TODO: as we switchOutDrawHistory when trackball or navigation
            // keys are hit, this should be safe. Right?
            return;
        }
        cx = contentToView(cx);
        cy = contentToView(cy);
        if (mHeightCanMeasure) {
            // move our visible rect according to scroll request
            if (cy != 0) {
                Rect tempRect = new Rect();
                calcOurVisibleRect(tempRect);
                tempRect.offset(cx, cy);
                requestRectangleOnScreen(tempRect);
            }
            // FIXME: We scroll horizontally no matter what because currently
            // ScrollView and ListView will not scroll horizontally.
            // FIXME: Why do we only scroll horizontally if there is no
            // vertical scroll?
//                Log.d(LOGTAG, "setContentScrollBy cy=" + cy);
            if (cy == 0 && cx != 0) {
                pinScrollBy(cx, 0, true);
            }
        } else {
            pinScrollBy(cx, cy, true);
        }
    }

    // scale from content to view coordinates, and pin
    // return true if pin caused the final x/y different than the request cx/cy;
    // return false if the view scroll to the exact position as it is requested.
    private boolean setContentScrollTo(int cx, int cy) {
        if (mDrawHistory) {
            // disallow WebView to change the scroll position as History Picture
            // is used in the view system.
            // One known case where this is called is that WebCore tries to
            // restore the scroll position. As history Picture already uses the
            // saved scroll position, it is ok to skip this.
            return false;
        }
        int vx = contentToView(cx);
        int vy = contentToView(cy);
//        Log.d(LOGTAG, "content scrollTo [" + cx + " " + cy + "] view=[" +
//                      vx + " " + vy + "]");
        pinScrollTo(vx, vy, false);
        if (mScrollX != vx || mScrollY != vy) {
            return true;
        } else {
            return false;
        }
    }

    // scale from content to view coordinates, and pin
    private void spawnContentScrollTo(int cx, int cy) {
        if (mDrawHistory) {
            // disallow WebView to change the scroll position as History Picture
            // is used in the view system.
            return;
        }
        int vx = contentToView(cx);
        int vy = contentToView(cy);
        pinScrollTo(vx, vy, true);
    }

    /**
     * These are from webkit, and are in content coordinate system (unzoomed)
     */
    private void contentSizeChanged(boolean updateLayout) {
        // suppress 0,0 since we usually see real dimensions soon after
        // this avoids drawing the prev content in a funny place. If we find a
        // way to consolidate these notifications, this check may become
        // obsolete
        if ((mContentWidth | mContentHeight) == 0) {
            return;
        }

        if (mHeightCanMeasure) {
            if (getMeasuredHeight() != contentToView(mContentHeight)
                    && updateLayout) {
                requestLayout();
            }
        } else if (mWidthCanMeasure) {
            if (getMeasuredWidth() != contentToView(mContentWidth)
                    && updateLayout) {
                requestLayout();
            }
        } else {
            // If we don't request a layout, try to send our view size to the
            // native side to ensure that WebCore has the correct dimensions.
            sendViewSizeZoom();
        }
    }

    /**
     * Set the WebViewClient that will receive various notifications and
     * requests. This will replace the current handler.
     * @param client An implementation of WebViewClient.
     */
    public void setWebViewClient(WebViewClient client) {
        mCallbackProxy.setWebViewClient(client);
    }

    /**
     * Register the interface to be used when content can not be handled by
     * the rendering engine, and should be downloaded instead. This will replace
     * the current handler.
     * @param listener An implementation of DownloadListener.
     */
    public void setDownloadListener(DownloadListener listener) {
        mCallbackProxy.setDownloadListener(listener);
    }

    /**
     * Set the chrome handler. This is an implementation of WebChromeClient for
     * use in handling Javascript dialogs, favicons, titles, and the progress.
     * This will replace the current handler.
     * @param client An implementation of WebChromeClient.
     */
    public void setWebChromeClient(WebChromeClient client) {
        mCallbackProxy.setWebChromeClient(client);
    }

    /**
     * Set the Picture listener. This is an interface used to receive
     * notifications of a new Picture.
     * @param listener An implementation of WebView.PictureListener.
     */
    public void setPictureListener(PictureListener listener) {
        mPictureListener = listener;
    }

    /**
     * {@hide}
     */
    /* FIXME: Debug only! Remove for SDK! */
    public void externalRepresentation(Message callback) {
        mWebViewCore.sendMessage(EventHub.REQUEST_EXT_REPRESENTATION, callback);
    }

    /**
     * {@hide}
     */
    /* FIXME: Debug only! Remove for SDK! */
    public void documentAsText(Message callback) {
        mWebViewCore.sendMessage(EventHub.REQUEST_DOC_AS_TEXT, callback);
    }

    /**
     * Use this function to bind an object to Javascript so that the
     * methods can be accessed from Javascript.
     * IMPORTANT, the object that is bound runs in another thread and
     * not in the thread that it was constructed in.
     * @param obj The class instance to bind to Javascript
     * @param interfaceName The name to used to expose the class in Javascript
     */
    public void addJavascriptInterface(Object obj, String interfaceName) {
        // Use Hashmap rather than Bundle as Bundles can't cope with Objects
        HashMap arg = new HashMap();
        arg.put("object", obj);
        arg.put("interfaceName", interfaceName);
        mWebViewCore.sendMessage(EventHub.ADD_JS_INTERFACE, arg);
    }

    /**
     * Return the WebSettings object used to control the settings for this
     * WebView.
     * @return A WebSettings object that can be used to control this WebView's
     *         settings.
     */
    public WebSettings getSettings() {
        return mWebViewCore.getSettings();
    }

   /**
    * Return the list of currently loaded plugins.
    * @return The list of currently loaded plugins.
    */
    public static synchronized PluginList getPluginList() {
        if (sPluginList == null) {
            sPluginList = new PluginList();
        }
        return sPluginList;
    }

   /**
    * Signal the WebCore thread to refresh its list of plugins. Use
    * this if the directory contents of one of the plugin directories
    * has been modified and needs its changes reflecting. May cause
    * plugin load and/or unload.
    * @param reloadOpenPages Set to true to reload all open pages.
    */
    public void refreshPlugins(boolean reloadOpenPages) {
        if (mWebViewCore != null) {
            mWebViewCore.sendMessage(EventHub.REFRESH_PLUGINS, reloadOpenPages);
        }
    }

    //-------------------------------------------------------------------------
    // Override View methods
    //-------------------------------------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        destroy();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // if mNativeClass is 0, the WebView has been destroyed. Do nothing.
        if (mNativeClass == 0) {
            return;
        }
        if (mWebViewCore.mEndScaleZoom) {
            mWebViewCore.mEndScaleZoom = false;
            if (mTouchMode >= FIRST_SCROLL_ZOOM 
                    && mTouchMode <= LAST_SCROLL_ZOOM) {
                setHorizontalScrollBarEnabled(true);
                setVerticalScrollBarEnabled(true);
                mTouchMode = TOUCH_DONE_MODE;
            }
        }
        int sc = canvas.save();
        if (mTouchMode >= FIRST_SCROLL_ZOOM && mTouchMode <= LAST_SCROLL_ZOOM) {
            scrollZoomDraw(canvas);
        } else {
            nativeRecomputeFocus();
            // Update the buttons in the picture, so when we draw the picture
            // to the screen, they are in the correct state.
            nativeRecordButtons();
            drawCoreAndFocusRing(canvas, mBackgroundColor, mDrawFocusRing);
        }
        canvas.restoreToCount(sc);
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        if (params.height == LayoutParams.WRAP_CONTENT) {
            mWrapContent = true;
        }
        super.setLayoutParams(params);
    }

    @Override
    public boolean performLongClick() {
        if (inEditingMode()) {
            return mTextEntry.performLongClick();
        } else {
            return super.performLongClick();
        }
    }

    private void drawCoreAndFocusRing(Canvas canvas, int color,
        boolean drawFocus) {
        if (mDrawHistory) {
            canvas.scale(mActualScale, mActualScale);
            canvas.drawPicture(mHistoryPicture);
            return;
        }

        boolean animateZoom = mZoomScale != 0;
        boolean animateScroll = !mScroller.isFinished() 
                || mVelocityTracker != null;
        if (animateZoom) {
            float zoomScale;
            int interval = (int) (SystemClock.uptimeMillis() - mZoomStart);
            if (interval < ZOOM_ANIMATION_LENGTH) {
                float ratio = (float) interval / ZOOM_ANIMATION_LENGTH;
                zoomScale = 1.0f / (mInvInitialZoomScale 
                        + (mInvFinalZoomScale - mInvInitialZoomScale) * ratio);
                invalidate();
            } else {
                zoomScale = mZoomScale;
            }
            float scale = (mActualScale - zoomScale) * mInvActualScale;
            float tx = scale * ((getLeft() + getRight()) * 0.5f + mScrollX);
            float ty = scale * ((getTop() + getBottom()) * 0.5f + mScrollY);

            // this block pins the translate to "legal" bounds. This makes the
            // animation a bit non-obvious, but it means we won't pop when the
            // "real" zoom takes effect
            if (true) {
               // canvas.translate(mScrollX, mScrollY);
                tx -= mScrollX;
                ty -= mScrollY;
                tx = -pinLoc(-Math.round(tx), getViewWidth(), Math
                        .round(mContentWidth * zoomScale));
                ty = -pinLoc(-Math.round(ty), getViewHeight(), Math
                        .round(mContentHeight * zoomScale));
                tx += mScrollX;
                ty += mScrollY;
            }
            canvas.translate(tx, ty);
            canvas.scale(zoomScale, zoomScale);
        } else {
            canvas.scale(mActualScale, mActualScale);
        }

        mWebViewCore.drawContentPicture(canvas, color, animateZoom,
                animateScroll);

        if (mNativeClass == 0) return;
        if (mShiftIsPressed) {
            nativeDrawSelection(canvas, mSelectX, mSelectY, mExtendSelection);
        } else if (drawFocus) {
            if (mTouchMode == TOUCH_SHORTPRESS_START_MODE) {
                mTouchMode = TOUCH_SHORTPRESS_MODE;
                HitTestResult hitTest = getHitTestResult();
                if (hitTest != null &&
                        hitTest.mType != HitTestResult.UNKNOWN_TYPE) {
                    mPrivateHandler.sendMessageDelayed(mPrivateHandler
                            .obtainMessage(SWITCH_TO_LONGPRESS),
                            LONG_PRESS_TIMEOUT);
                }
            }
            nativeDrawFocusRing(canvas);
        }
    }
    
    private float scrollZoomGridScale(float invScale) {
        float griddedInvScale = (int) (invScale * SCROLL_ZOOM_GRID) 
            / (float) SCROLL_ZOOM_GRID;
        return 1.0f / griddedInvScale;
    }
    
    private float scrollZoomX(float scale) {
        int width = getViewWidth();
        float maxScrollZoomX = mContentWidth * scale - width;
        int maxX = mContentWidth - width;
        return -(maxScrollZoomX > 0 ? mZoomScrollX * maxScrollZoomX / maxX
                : maxScrollZoomX / 2);
    }

    private float scrollZoomY(float scale) {
        int height = getViewHeight();
        float maxScrollZoomY = mContentHeight * scale - height;
        int maxY = mContentHeight - height;
        return -(maxScrollZoomY > 0 ? mZoomScrollY * maxScrollZoomY / maxY
                : maxScrollZoomY / 2);
    }
    
    private void drawMagnifyFrame(Canvas canvas, Rect frame, Paint paint) {
        final float ADORNMENT_LEN = 16.0f;
        float width = frame.width();
        float height = frame.height();
        Path path = new Path();
        path.moveTo(-ADORNMENT_LEN, -ADORNMENT_LEN);
        path.lineTo(0, 0);
        path.lineTo(width, 0);
        path.lineTo(width + ADORNMENT_LEN, -ADORNMENT_LEN);
        path.moveTo(-ADORNMENT_LEN, height + ADORNMENT_LEN);
        path.lineTo(0, height);
        path.lineTo(width, height);
        path.lineTo(width + ADORNMENT_LEN, height + ADORNMENT_LEN);
        path.moveTo(0, 0);
        path.lineTo(0, height);
        path.moveTo(width, 0);
        path.lineTo(width, height);
        path.offset(frame.left, frame.top);
        canvas.drawPath(path, paint);
    }
    
    // Returns frame surrounding magified portion of screen while 
    // scroll-zoom is enabled. The frame is also used to center the
    // zoom-in zoom-out points at the start and end of the animation.
    private Rect scrollZoomFrame(int width, int height, float halfScale) {
        Rect scrollFrame = new Rect();
        scrollFrame.set(mZoomScrollX, mZoomScrollY, 
                mZoomScrollX + width, mZoomScrollY + height);
        if (mContentWidth == width) {
            float offsetX = (width * halfScale - width) / 2;
            scrollFrame.left -= offsetX;
            scrollFrame.right += offsetX;
        }
        if (mContentHeight == height) {
            float offsetY = (height * halfScale - height) / 2;
            scrollFrame.top -= offsetY;
            scrollFrame.bottom += offsetY;
        }
        return scrollFrame;
    }
    
    private float scrollZoomMagScale(float invScale) {
        return (invScale * 2 + mInvActualScale) / 3;
    }
    
    private void scrollZoomDraw(Canvas canvas) {
        float invScale = mZoomScrollInvLimit; 
        int elapsed = 0;
        if (mTouchMode != SCROLL_ZOOM_OUT) {
            elapsed = (int) Math.min(System.currentTimeMillis() 
                - mZoomScrollStart, SCROLL_ZOOM_DURATION);
            float transitionScale = (mZoomScrollInvLimit - mInvActualScale) 
                    * elapsed / SCROLL_ZOOM_DURATION;
            if (mTouchMode == SCROLL_ZOOM_ANIMATION_OUT) {
                invScale = mInvActualScale + transitionScale;
            } else { /* if (mTouchMode == SCROLL_ZOOM_ANIMATION_IN) */
                invScale = mZoomScrollInvLimit - transitionScale;
            }
        }
        float scale = scrollZoomGridScale(invScale);
        invScale = 1.0f / scale;
        int width = getViewWidth();
        int height = getViewHeight();
        float halfScale = scrollZoomMagScale(invScale);
        Rect scrollFrame = scrollZoomFrame(width, height, halfScale);
        if (elapsed == SCROLL_ZOOM_DURATION) {
            if (mTouchMode == SCROLL_ZOOM_ANIMATION_IN) {
                setHorizontalScrollBarEnabled(true);
                setVerticalScrollBarEnabled(true);
                updateTextEntry();
                scrollTo((int) (scrollFrame.centerX() * mActualScale) 
                        - (width >> 1), (int) (scrollFrame.centerY() 
                        * mActualScale) - (height >> 1));
                mTouchMode = TOUCH_DONE_MODE;
            } else {
                mTouchMode = SCROLL_ZOOM_OUT;
            }
        }
        float newX = scrollZoomX(scale);
        float newY = scrollZoomY(scale);
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "scrollZoomDraw scale=" + scale + " + (" + newX
                    + ", " + newY + ") mZoomScroll=(" + mZoomScrollX + ", "
                    + mZoomScrollY + ")" + " invScale=" + invScale + " scale=" 
                    + scale);
        }
        canvas.translate(newX, newY);
        canvas.scale(scale, scale);
        boolean animating = mTouchMode != SCROLL_ZOOM_OUT;
        if (mDrawHistory) {
            int sc = canvas.save(Canvas.CLIP_SAVE_FLAG);
            Rect clip = new Rect(0, 0, mHistoryPicture.getWidth(),
                    mHistoryPicture.getHeight());
            canvas.clipRect(clip, Region.Op.DIFFERENCE);
            canvas.drawColor(mBackgroundColor);
            canvas.restoreToCount(sc);
            canvas.drawPicture(mHistoryPicture);
        } else {
            mWebViewCore.drawContentPicture(canvas, mBackgroundColor,
                    animating, true);
        }
        if (mTouchMode == TOUCH_DONE_MODE) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(30.0f);
        paint.setARGB(0x50, 0, 0, 0);
        int maxX = mContentWidth - width;
        int maxY = mContentHeight - height;
        if (true) { // experiment: draw hint to place finger off magnify area
            drawMagnifyFrame(canvas, scrollFrame, paint);
        } else {
            canvas.drawRect(scrollFrame, paint);
        }
        int sc = canvas.save();
        canvas.clipRect(scrollFrame);
        float halfX = maxX > 0 ? (float) mZoomScrollX / maxX : 0.5f;
        float halfY = maxY > 0 ? (float) mZoomScrollY / maxY : 0.5f;
        canvas.scale(halfScale, halfScale, mZoomScrollX + width * halfX
                , mZoomScrollY + height * halfY);
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "scrollZoomDraw halfScale=" + halfScale + " w/h=(" 
                    + width + ", " + height + ") half=(" + halfX + ", "
                    + halfY + ")");
        }
        if (mDrawHistory) {
            canvas.drawPicture(mHistoryPicture);
        } else {
            mWebViewCore.drawContentPicture(canvas, mBackgroundColor,
                    animating, false);
        }
        canvas.restoreToCount(sc);
        if (mTouchMode != SCROLL_ZOOM_OUT) {
            invalidate();
        }
    }

    private void zoomScrollTap(float x, float y) {
        float scale = scrollZoomGridScale(mZoomScrollInvLimit);
        float left = scrollZoomX(scale);
        float top = scrollZoomY(scale);
        int width = getViewWidth();
        int height = getViewHeight();
        x -= width * scale / 2;
        y -= height * scale / 2;
        mZoomScrollX = Math.min(mContentWidth - width
                , Math.max(0, (int) ((x - left) / scale)));
        mZoomScrollY = Math.min(mContentHeight - height
                , Math.max(0, (int) ((y - top) / scale)));
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "zoomScrollTap scale=" + scale + " + (" + left
                    + ", " + top + ") mZoomScroll=(" + mZoomScrollX + ", "
                    + mZoomScrollY + ")" + " x=" + x + " y=" + y);
        }
    }

    private boolean canZoomScrollOut() {
        if (mContentWidth == 0 || mContentHeight == 0) {
            return false;
        }
        int width = getViewWidth();
        int height = getViewHeight();
        float x = (float) width / (float) mContentWidth;
        float y = (float) height / (float) mContentHeight;
        mZoomScrollLimit = Math.max(DEFAULT_MIN_ZOOM_SCALE, Math.min(x, y));
        mZoomScrollInvLimit = 1.0f / mZoomScrollLimit;
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "canZoomScrollOut"
                    + " mInvActualScale=" + mInvActualScale
                    + " mZoomScrollLimit=" + mZoomScrollLimit
                    + " mZoomScrollInvLimit=" + mZoomScrollInvLimit
                    + " mContentWidth=" + mContentWidth
                    + " mContentHeight=" + mContentHeight
                    );
        }
        // don't zoom out unless magnify area is at least half as wide
        // or tall as content
        float limit = mZoomScrollLimit * 2;
        return mContentWidth >= width * limit
                || mContentHeight >= height * limit;
    }
        
    private void startZoomScrollOut() {
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
        if (mZoomControlRunnable != null) {
            mPrivateHandler.removeCallbacks(mZoomControlRunnable);
        }
        if (mZoomControls != null) {
            mZoomControls.hide();
        }
        int width = getViewWidth();
        int height = getViewHeight();
        int halfW = width >> 1;
        mLastTouchX = halfW;
        int halfH = height >> 1;
        mLastTouchY = halfH;
        mScroller.abortAnimation();
        mZoomScrollStart = System.currentTimeMillis();
        Rect zoomFrame = scrollZoomFrame(width, height
                , scrollZoomMagScale(mZoomScrollInvLimit));
        mZoomScrollX = Math.max(0, (int) ((mScrollX + halfW) * mInvActualScale) 
                - (zoomFrame.width() >> 1));
        mZoomScrollY = Math.max(0, (int) ((mScrollY + halfH) * mInvActualScale) 
                - (zoomFrame.height() >> 1));
        scrollTo(0, 0); // triggers inval, starts animation
        clearTextEntry();
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "startZoomScrollOut mZoomScroll=(" 
                    + mZoomScrollX + ", " + mZoomScrollY +")");
        }
    }
    
    private void zoomScrollOut() {
        if (canZoomScrollOut() == false) return;
        startZoomScrollOut();
        mTouchMode = SCROLL_ZOOM_ANIMATION_OUT;
        invalidate();
    }

    private void moveZoomScrollWindow(float x, float y) {
        if (Math.abs(x - mLastZoomScrollRawX) < 1.5f 
                && Math.abs(y - mLastZoomScrollRawY) < 1.5f) {
            return;
        }
        mLastZoomScrollRawX = x;
        mLastZoomScrollRawY = y;
        int oldX = mZoomScrollX;
        int oldY = mZoomScrollY;
        int width = getViewWidth();
        int height = getViewHeight();
        int maxZoomX = mContentWidth - width;
        if (maxZoomX > 0) {
            int maxScreenX = width - (int) Math.ceil(width 
                    * mZoomScrollLimit) - SCROLL_ZOOM_FINGER_BUFFER;
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "moveZoomScrollWindow-X" 
                        + " maxScreenX=" + maxScreenX + " width=" + width
                        + " mZoomScrollLimit=" + mZoomScrollLimit + " x=" + x); 
            }
            x += maxScreenX * mLastScrollX / maxZoomX - mLastTouchX;
            x = Math.max(0, Math.min(maxScreenX, x));
            mZoomScrollX = (int) (x * maxZoomX / maxScreenX);
        }
        int maxZoomY = mContentHeight - height;
        if (maxZoomY > 0) {
            int maxScreenY = height - (int) Math.ceil(height 
                    * mZoomScrollLimit) - SCROLL_ZOOM_FINGER_BUFFER;
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "moveZoomScrollWindow-Y" 
                        + " maxScreenY=" + maxScreenY + " height=" + height
                        + " mZoomScrollLimit=" + mZoomScrollLimit + " y=" + y); 
            }
            y += maxScreenY * mLastScrollY / maxZoomY - mLastTouchY;
            y = Math.max(0, Math.min(maxScreenY, y));
            mZoomScrollY = (int) (y * maxZoomY / maxScreenY);
        }
        if (oldX != mZoomScrollX || oldY != mZoomScrollY) {
            invalidate();
        }
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "moveZoomScrollWindow" 
                    + " scrollTo=(" + mZoomScrollX + ", " + mZoomScrollY + ")" 
                    + " mLastTouch=(" + mLastTouchX + ", " + mLastTouchY + ")" 
                    + " maxZoom=(" + maxZoomX + ", " + maxZoomY + ")" 
                    + " last=("+mLastScrollX+", "+mLastScrollY+")" 
                    + " x=" + x + " y=" + y);
        }
    }

    private void setZoomScrollIn() {
        mZoomScrollStart = System.currentTimeMillis();
    }

    private float mZoomScrollLimit;
    private float mZoomScrollInvLimit;
    private int mLastScrollX;
    private int mLastScrollY;
    private long mZoomScrollStart;
    private int mZoomScrollX;
    private int mZoomScrollY;
    private float mLastZoomScrollRawX = -1000.0f;
    private float mLastZoomScrollRawY = -1000.0f;
    // The zoomed scale varies from 1.0 to DEFAULT_MIN_ZOOM_SCALE == 0.25.
    // The zoom animation duration SCROLL_ZOOM_DURATION == 0.5.
    // Two pressures compete for gridding; a high frame rate (e.g. 20 fps)
    // and minimizing font cache allocations (fewer frames is better).
    // A SCROLL_ZOOM_GRID of 6 permits about 20 zoom levels over 0.5 seconds:
    // the inverse of: 1.0, 1.16, 1.33, 1.5, 1.67, 1.84, 2.0, etc. to 4.0
    private static final int SCROLL_ZOOM_GRID = 6;
    private static final int SCROLL_ZOOM_DURATION = 500;
    // Make it easier to get to the bottom of a document by reserving a 32
    // pixel buffer, for when the starting drag is a bit below the bottom of
    // the magnify frame.
    private static final int SCROLL_ZOOM_FINGER_BUFFER = 32;

    // draw history
    private boolean mDrawHistory = false;
    private Picture mHistoryPicture = null;
    private int mHistoryWidth = 0;
    private int mHistoryHeight = 0;

    // Only check the flag, can be called from WebCore thread
    boolean drawHistory() {
        return mDrawHistory;
    }

    // Should only be called in UI thread
    void switchOutDrawHistory() {
        if (null == mWebViewCore) return; // CallbackProxy may trigger this
        if (mDrawHistory) {
            mDrawHistory = false;
            invalidate();
            int oldScrollX = mScrollX;
            int oldScrollY = mScrollY;
            mScrollX = pinLocX(mScrollX);
            mScrollY = pinLocY(mScrollY);
            if (oldScrollX != mScrollX || oldScrollY != mScrollY) {
                mUserScroll = false;
                mWebViewCore.sendMessage(EventHub.SYNC_SCROLL, oldScrollX,
                        oldScrollY);
            }
            sendOurVisibleRect();
        }
    }

    /**
     *  Class representing the node which is focused.
     */
    private class FocusNode {
        public FocusNode() {
            mBounds = new Rect();
        }
        // Only to be called by JNI
        private void setAll(boolean isTextField, boolean isTextArea, boolean 
                isPassword, boolean isAnchor, boolean isRtlText, int maxLength, 
                int textSize, int boundsX, int boundsY, int boundsRight, int 
                boundsBottom, int nodePointer, int framePointer, String text, 
                String name, int rootTextGeneration) {
            mIsTextField        = isTextField;
            mIsTextArea         = isTextArea;
            mIsPassword         = isPassword;
            mIsAnchor           = isAnchor;
            mIsRtlText          = isRtlText;

            mMaxLength          = maxLength;
            mTextSize           = textSize;
            
            mBounds.set(boundsX, boundsY, boundsRight, boundsBottom);
            
            
            mNodePointer        = nodePointer;
            mFramePointer       = framePointer;
            mText               = text;
            mName               = name;
            mRootTextGeneration = rootTextGeneration;
        }
        public boolean  mIsTextField;
        public boolean  mIsTextArea;
        public boolean  mIsPassword;
        public boolean  mIsAnchor;
        public boolean  mIsRtlText;

        public int      mSelectionStart;
        public int      mSelectionEnd;
        public int      mMaxLength;
        public int      mTextSize;
        
        public Rect     mBounds;
        
        public int      mNodePointer;
        public int      mFramePointer;
        public String   mText;
        public String   mName;
        public int      mRootTextGeneration;
    }
    
    // Warning: ONLY use mFocusNode AFTER calling nativeUpdateFocusNode(),
    // and ONLY if it returns true;
    private FocusNode mFocusNode = new FocusNode();
    
    /**
     *  Delete text from start to end in the focused textfield. If there is no
     *  focus, or if start == end, silently fail.  If start and end are out of 
     *  order, swap them.
     *  @param  start   Beginning of selection to delete.
     *  @param  end     End of selection to delete.
     */
    /* package */ void deleteSelection(int start, int end) {
        mWebViewCore.sendMessage(EventHub.DELETE_SELECTION, start, end,
                new WebViewCore.FocusData(mFocusData));
    }

    /**
     *  Set the selection to (start, end) in the focused textfield. If start and
     *  end are out of order, swap them.
     *  @param  start   Beginning of selection.
     *  @param  end     End of selection.
     */
    /* package */ void setSelection(int start, int end) {
        mWebViewCore.sendMessage(EventHub.SET_SELECTION, start, end,
                new WebViewCore.FocusData(mFocusData));
    }

    // Used to register the global focus change listener one time to avoid
    // multiple references to WebView
    private boolean mGlobalFocusChangeListenerAdded;
    
    private void updateTextEntry() {
        if (mTextEntry == null) {
            mTextEntry = new TextDialog(mContext, WebView.this);
            // Initialize our generation number.
            mTextGeneration = 0;
        }
        // If we do not have focus, do nothing until we gain focus.
        if (!hasFocus() && !mTextEntry.hasFocus()
                || (mTouchMode >= FIRST_SCROLL_ZOOM 
                && mTouchMode <= LAST_SCROLL_ZOOM)) {
            mNeedsUpdateTextEntry = true;
            return;
        }
        boolean alreadyThere = inEditingMode();
        if (0 == mNativeClass || !nativeUpdateFocusNode()) {
            if (alreadyThere) {
                mTextEntry.remove();
            }
            return;
        }
        FocusNode node = mFocusNode;
        if (!node.mIsTextField && !node.mIsTextArea) {
            if (alreadyThere) {
                mTextEntry.remove();
            }
            return;
        }
        mTextEntry.setTextSize(contentToView(node.mTextSize));
        Rect visibleRect = sendOurVisibleRect();
        // Note that sendOurVisibleRect calls viewToContent, so the coordinates
        // should be in content coordinates.
        if (!Rect.intersects(node.mBounds, visibleRect)) {
            if (alreadyThere) {
                mTextEntry.remove();
            }
            // Node is not on screen, so do not bother.
            return;
        }
        int x = node.mBounds.left;
        int y = node.mBounds.top;
        int width = node.mBounds.width();
        int height = node.mBounds.height();
        if (alreadyThere && mTextEntry.isSameTextField(node.mNodePointer)) {
            // It is possible that we have the same textfield, but it has moved,
            // i.e. In the case of opening/closing the screen.
            // In that case, we need to set the dimensions, but not the other
            // aspects.
            // We also need to restore the selection, which gets wrecked by
            // calling setTextEntryRect.
            Spannable spannable = (Spannable) mTextEntry.getText();
            int start = Selection.getSelectionStart(spannable);
            int end = Selection.getSelectionEnd(spannable);
            setTextEntryRect(x, y, width, height);
            // If the text has been changed by webkit, update it.  However, if
            // there has been more UI text input, ignore it.  We will receive
            // another update when that text is recognized.
            if (node.mText != null && !node.mText.equals(spannable.toString())
                    && node.mRootTextGeneration == mTextGeneration) {
                mTextEntry.setTextAndKeepSelection(node.mText);
            } else {
                Selection.setSelection(spannable, start, end);
            }
        } else {
            String text = node.mText;
            setTextEntryRect(x, y, width, height);
            mTextEntry.setGravity(node.mIsRtlText ? Gravity.RIGHT : 
                    Gravity.NO_GRAVITY);
            // this needs to be called before update adapter thread starts to
            // ensure the mTextEntry has the same node pointer
            mTextEntry.setNodePointer(node.mNodePointer);
            int maxLength = -1;
            if (node.mIsTextField) {
                maxLength = node.mMaxLength;
                if (mWebViewCore.getSettings().getSaveFormData()
                        && node.mName != null) {
                    HashMap data = new HashMap();
                    data.put("text", node.mText);
                    Message update = mPrivateHandler.obtainMessage(
                            UPDATE_TEXT_ENTRY_ADAPTER, node.mNodePointer, 0,
                            data);
                    UpdateTextEntryAdapter updater = new UpdateTextEntryAdapter(
                            node.mName, getUrl(), update);
                    Thread t = new Thread(updater);
                    t.start();
                }
            }
            mTextEntry.setMaxLength(maxLength);
            ArrayAdapter<String> adapter = null;
            mTextEntry.setAdapter(adapter);
            mTextEntry.setInPassword(node.mIsPassword);
            mTextEntry.setSingleLine(node.mIsTextField);
            if (null == text) {
                mTextEntry.setText("", 0, 0);
            } else {
                mTextEntry.setText(text, 0, text.length());
            }
            mTextEntry.requestFocus();
        }
        if (!mGlobalFocusChangeListenerAdded) {
            getViewTreeObserver().addOnGlobalFocusChangeListener(this);
            mGlobalFocusChangeListenerAdded = true;
        }
    }

    private class UpdateTextEntryAdapter implements Runnable {
        private String mName;
        private String mUrl;
        private Message mUpdateMessage;

        public UpdateTextEntryAdapter(String name, String url, Message msg) {
            mName = name;
            mUrl = url;
            mUpdateMessage = msg;
        }

        public void run() {
            ArrayList<String> pastEntries = mDatabase.getFormData(mUrl, mName);
            if (pastEntries.size() > 0) {
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        mContext, com.android.internal.R.layout.simple_list_item_1,
                        pastEntries);
                ((HashMap) mUpdateMessage.obj).put("adapter", adapter);
                mUpdateMessage.sendToTarget();
            }
        }
    }

    private void setTextEntryRect(int x, int y, int width, int height) {
        x = contentToView(x);
        y = contentToView(y);
        width = contentToView(width);
        height = contentToView(height);
        mTextEntry.setRect(x, y, width, height);
    }

    // These variables are used to determine long press with the enter key, or
    // a center key.  Does not affect long press with the trackball/touch.
    private long mDownTime = 0;
    private boolean mGotDown = false;

    // Enable copy/paste with trackball here.
    // This should be left disabled until the framework can guarantee
    // delivering matching key-up and key-down events for the shift key
    private static final boolean ENABLE_COPY_PASTE = false;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mTouchMode >= FIRST_SCROLL_ZOOM && mTouchMode <= LAST_SCROLL_ZOOM) {
            return false;
        }
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && 
                keyCode != KeyEvent.KEYCODE_ENTER) {
            mGotDown = false;
        }
        if (mAltIsPressed == false && (keyCode == KeyEvent.KEYCODE_ALT_LEFT 
                || keyCode == KeyEvent.KEYCODE_ALT_RIGHT)) {
            mAltIsPressed = true;
        }
        if (ENABLE_COPY_PASTE && mShiftIsPressed == false 
                && (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT 
                || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)) {
            mExtendSelection = false;
            mShiftIsPressed = true;
            if (mNativeClass != 0 && nativeUpdateFocusNode()) {
                FocusNode node = mFocusNode;
                mSelectX = node.mBounds.left;
                mSelectY = node.mBounds.top;
            } else {
                mSelectX = mScrollX + SELECT_CURSOR_OFFSET;
                mSelectY = mScrollY + SELECT_CURSOR_OFFSET;
            }
       }
       if (keyCode == KeyEvent.KEYCODE_CALL) {
            if (mNativeClass != 0 && nativeUpdateFocusNode()) {
                FocusNode node = mFocusNode;
                String text = node.mText;
                if (!node.mIsTextField && !node.mIsTextArea && text != null &&
                        text.startsWith(SCHEME_TEL)) {
                    Intent intent = new Intent(Intent.ACTION_DIAL,
                            Uri.parse(text));
                    getContext().startActivity(intent);
                    return true;
                }
            }
            return false;
        }

        if (event.isSystem() || mCallbackProxy.uiOverrideKeyEvent(event)) {
            return false;
        }
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "keyDown at " + System.currentTimeMillis()
                    + ", " + event);
        }
        boolean isArrowKey = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (isArrowKey && event.getEventTime() - mTrackballLastTime 
                <= TRACKBALL_KEY_TIMEOUT) {
            if (LOGV_ENABLED) Log.v(LOGTAG, "ignore arrow");
            return false;
        }
        boolean weHandledTheKey = false;

        if (event.getMetaState() == 0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    // TODO: alternatively we can do panning as touch does
                    switchOutDrawHistory();
                    weHandledTheKey = navHandledKey(keyCode, 1, false
                            , event.getEventTime());
                    if (weHandledTheKey) {
                        playSoundEffect(keyCodeToSoundsEffect(keyCode));
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (event.getRepeatCount() == 0) {
                        switchOutDrawHistory();
                        mDownTime = event.getEventTime();
                        mGotDown = true;
                        return true;
                    } 
                    if (mGotDown && event.getEventTime() - mDownTime > 
                            ViewConfiguration.getLongPressTimeout()) {
                        performLongClick();
                        mGotDown = false;
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_9:
                    if (mNativeClass != 0 && getSettings().getNavDump()) {
                        debugDump();
                    }
                    break;
                default:
                    break;
            }
        }

        // suppress sending arrow keys to webkit
        if (!weHandledTheKey && !isArrowKey) {
            mWebViewCore.sendMessage(EventHub.KEY_DOWN, keyCode, 0, event);
        }

        return weHandledTheKey;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT 
                || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (mExtendSelection) {
                // copy region so core operates on copy without touching orig.
                Region selection = new Region(nativeGetSelection());
                if (selection.isEmpty() == false) {
                    Toast.makeText(mContext
                            , com.android.internal.R.string.text_copied
                            , Toast.LENGTH_SHORT).show();
                    mWebViewCore.sendMessage(EventHub.GET_SELECTION, selection);
                }
            }
            mShiftIsPressed = false;
            return true;
        }
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "MT keyUp at" + System.currentTimeMillis()
                    + ", " + event);
        }
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT 
                || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            mAltIsPressed = false;
        }
        if (event.isSystem() || mCallbackProxy.uiOverrideKeyEvent(event)) {
            return false;
        }

        if (mTouchMode >= FIRST_SCROLL_ZOOM && mTouchMode <= LAST_SCROLL_ZOOM) {
            if (KeyEvent.KEYCODE_DPAD_CENTER == keyCode
                    && mTouchMode != SCROLL_ZOOM_ANIMATION_IN) {
                setZoomScrollIn();
                mTouchMode = SCROLL_ZOOM_ANIMATION_IN;
                invalidate();
                return true;
            }
            return false;
        }
        Rect visibleRect = sendOurVisibleRect();
        // Note that sendOurVisibleRect calls viewToContent, so the coordinates
        // should be in content coordinates.
        boolean nodeOnScreen = false;
        boolean isTextField = false;
        boolean isTextArea = false;
        FocusNode node = null;
        if (mNativeClass != 0 && nativeUpdateFocusNode()) {
            node = mFocusNode;
            isTextField = node.mIsTextField;
            isTextArea = node.mIsTextArea;
            nodeOnScreen = Rect.intersects(node.mBounds, visibleRect);
        }

        if (KeyEvent.KEYCODE_DPAD_CENTER == keyCode) {
            if (mShiftIsPressed) {
                return false;
            }
            if (getSettings().supportZoom()) {
                if (mTouchMode == TOUCH_DOUBLECLICK_MODE) {
                    zoomScrollOut();
                } else {
                    if (LOGV_ENABLED) Log.v(LOGTAG, "TOUCH_DOUBLECLICK_MODE");
                    mPrivateHandler.sendMessageDelayed(mPrivateHandler
                            .obtainMessage(SWITCH_TO_ENTER), TAP_TIMEOUT);
                    mTouchMode = TOUCH_DOUBLECLICK_MODE;
                }
                return true;
            } else {
                keyCode = KeyEvent.KEYCODE_ENTER;
            }
        }
        if (KeyEvent.KEYCODE_ENTER == keyCode) {
            if (LOGV_ENABLED) Log.v(LOGTAG, "KEYCODE_ENTER == keyCode");
            if (!nodeOnScreen) {
                return false;
            }
            if (node != null && !isTextField && !isTextArea) {
                nativeSetFollowedLink(true);
                mWebViewCore.sendMessage(EventHub.SET_FINAL_FOCUS, 
                        EventHub.BLOCK_FOCUS_CHANGE_UNTIL_KEY_UP, 0,
                        new WebViewCore.FocusData(mFocusData));
                if (mCallbackProxy.uiOverrideUrlLoading(node.mText)) {
                    return true;
                }
                playSoundEffect(SoundEffectConstants.CLICK);
            }
        }
        if (!nodeOnScreen) {
            // FIXME: Want to give Callback a chance to handle it, and
            // possibly pass down to javascript.
            return false;
        }
        if (LOGV_ENABLED) Log.v(LOGTAG, "onKeyUp send EventHub.KEY_UP");
        mWebViewCore.sendMessage(EventHub.KEY_UP, keyCode, 0, event);
        return true;
    }
    
    // Set this as a hierarchy change listener so we can know when this view
    // is removed and still have access to our parent.
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup p = (ViewGroup) parent;
            p.setOnHierarchyChangeListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup p = (ViewGroup) parent;
            p.setOnHierarchyChangeListener(null);
        }
    }
    
    // Implementation for OnHierarchyChangeListener
    public void onChildViewAdded(View parent, View child) {}
    
    // When we are removed, remove this as a global focus change listener.
    public void onChildViewRemoved(View p, View child) {
        if (child == this) {
            p.getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
            mGlobalFocusChangeListenerAdded = false;
        }
    }
    
    // Use this to know when the textview has lost focus, and something other
    // than the webview has gained focus.  Stop drawing the focus ring, remove
    // the TextView, and set a flag to put it back when we regain focus.
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == mTextEntry && newFocus != this) {
            mDrawFocusRing = false;
            mTextEntry.updateCachedTextfield();
            removeView(mTextEntry);
            mNeedsUpdateTextEntry = true;
        }
    }

    // To avoid drawing the focus ring, and remove the TextView when our window
    // loses focus.
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) {
            if (hasFocus()) {
                // If our window regained focus, and we have focus, then begin
                // drawing the focus ring, and restore the TextView if
                // necessary.
                mDrawFocusRing = true;
                if (mNeedsUpdateTextEntry) {
                    updateTextEntry();
                }
            } else {
                // If our window gained focus, but we do not have it, do not
                // draw the focus ring.
                mDrawFocusRing = false;
            }
        } else {
            // If our window has lost focus, stop drawing the focus ring, and
            // remove the TextView if displayed, and flag it to be added when
            // we regain focus.
            mDrawFocusRing = false;
            mGotKeyDown = false;
            if (inEditingMode()) {
                clearTextEntry();
                mNeedsUpdateTextEntry = true;
            }
        }
        invalidate();
        super.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction,
            Rect previouslyFocusedRect) {
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "MT focusChanged " + focused + ", " + direction);
        }
        if (focused) {
            // When we regain focus, if we have window focus, resume drawing
            // the focus ring, and add the TextView if necessary.
            if (hasWindowFocus()) {
                mDrawFocusRing = true;
                if (mNeedsUpdateTextEntry) {
                    updateTextEntry();
                    mNeedsUpdateTextEntry = false;
                }
            }
        } else {
            // When we lost focus, unless focus went to the TextView (which is
            // true if we are in editing mode), stop drawing the focus ring.
            if (!inEditingMode()) {
                mDrawFocusRing = false;
            }
            mGotKeyDown = false;
        }

        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);

        // we always force, in case our height changed, in which case we still
        // want to send the notification over to webkit
        setNewZoomScale(mActualScale, true);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        sendOurVisibleRect();
    }
    
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean dispatch = true;

        if (!inEditingMode()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mGotKeyDown = true;
            } else {
                if (!mGotKeyDown) {
                    /*
                     * We got a key up for which we were not the recipient of
                     * the original key down. Don't give it to the view.
                     */
                    dispatch = false;
                }
                mGotKeyDown = false;
            }
        }

        if (dispatch) {
            return super.dispatchKeyEvent(event);
        } else {
            // We didn't dispatch, so let something else handle the key
            return false;
        }
    }

    // Here are the snap align logic:
    // 1. If it starts nearly horizontally or vertically, snap align;
    // 2. If there is a dramitic direction change, let it go;
    // 3. If there is a same direction back and forth, lock it.

    // adjustable parameters
    private static final float MAX_SLOPE_FOR_DIAG = 1.5f;
    private static final int MIN_LOCK_SNAP_REVERSE_DISTANCE = 
            ViewConfiguration.getTouchSlop();
    private static final int MIN_BREAK_SNAP_CROSS_DISTANCE = 80;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mNativeClass == 0 || !isClickable() || !isLongClickable() || 
                !hasFocus()) {
            return false;
        }

        if (LOGV_ENABLED) {
            Log.v(LOGTAG, ev + " at " + ev.getEventTime() + " mTouchMode="
                    + mTouchMode);
        }

        int action = ev.getAction();
        float x = ev.getX();
        float y = ev.getY();
        long eventTime = ev.getEventTime();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (mTouchMode == SCROLL_ZOOM_ANIMATION_IN
                        || mTouchMode == SCROLL_ZOOM_ANIMATION_OUT) {
                    // no interaction while animation is in progress
                    break;
                } else if (mTouchMode == SCROLL_ZOOM_OUT) {
                    mLastScrollX = mZoomScrollX;
                    mLastScrollY = mZoomScrollY;
                    // If two taps are close, ignore the first tap
                } else if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                    mTouchMode = TOUCH_DRAG_START_MODE;
                    mPrivateHandler.removeMessages(RESUME_WEBCORE_UPDATE);
                } else {
                    mTouchMode = TOUCH_INIT_MODE;
                }
                if (mTouchMode == TOUCH_INIT_MODE) {
                    mPrivateHandler.sendMessageDelayed(mPrivateHandler
                            .obtainMessage(SWITCH_TO_SHORTPRESS), TAP_TIMEOUT);
                }
                // Remember where the motion event started
                mLastTouchX = x;
                mLastTouchY = y;
                mLastTouchTime = eventTime;
                mVelocityTracker = VelocityTracker.obtain();
                mSnapScrollMode = SNAP_NONE;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mTouchMode == TOUCH_DONE_MODE 
                        || mTouchMode == SCROLL_ZOOM_ANIMATION_IN
                        || mTouchMode == SCROLL_ZOOM_ANIMATION_OUT) {
                    // no dragging during scroll zoom animation
                    break;
                }
                if (mTouchMode == SCROLL_ZOOM_OUT) {
                    // while fully zoomed out, move the virtual window
                    moveZoomScrollWindow(x, y);
                    break;
                }
                mVelocityTracker.addMovement(ev);

                int deltaX = (int) (mLastTouchX - x);
                int deltaY = (int) (mLastTouchY - y);

                if (mTouchMode != TOUCH_DRAG_MODE) {
                    if ((deltaX * deltaX + deltaY * deltaY)
                            < TOUCH_SLOP_SQUARE) {
                        break;
                    }

                    if (mTouchMode == TOUCH_SHORTPRESS_MODE
                            || mTouchMode == TOUCH_SHORTPRESS_START_MODE) {
                        mPrivateHandler.removeMessages(SWITCH_TO_LONGPRESS);
                    } else if (mTouchMode == TOUCH_INIT_MODE) {
                        mPrivateHandler.removeMessages(SWITCH_TO_SHORTPRESS);
                    }

                    // if it starts nearly horizontal or vertical, enforce it
                    int ax = Math.abs(deltaX);
                    int ay = Math.abs(deltaY);
                    if (ax > MAX_SLOPE_FOR_DIAG * ay) {
                        mSnapScrollMode = SNAP_X;
                        mSnapPositive = deltaX > 0;
                    } else if (ay > MAX_SLOPE_FOR_DIAG * ax) {
                        mSnapScrollMode = SNAP_Y;
                        mSnapPositive = deltaY > 0;
                    }

                    mTouchMode = TOUCH_DRAG_MODE;
                    WebViewCore.pauseUpdate(mWebViewCore);
                    int contentX = viewToContent((int) x + mScrollX);
                    int contentY = viewToContent((int) y + mScrollY);
                    if (inEditingMode()) {
                        mTextEntry.updateCachedTextfield();
                    }
                    nativeClearFocus(contentX, contentY);
                    // remove the zoom anchor if there is any
                    if (mZoomScale != 0) {
                        mWebViewCore
                                .sendMessage(EventHub.SET_SNAP_ANCHOR, 0, 0);
                    }
                }

                // do pan
                int newScrollX = pinLocX(mScrollX + deltaX);
                deltaX = newScrollX - mScrollX;
                int newScrollY = pinLocY(mScrollY + deltaY);
                deltaY = newScrollY - mScrollY;
                boolean done = false;
                if (deltaX == 0 && deltaY == 0) {
                    done = true;
                } else {
                    if (mSnapScrollMode == SNAP_X || mSnapScrollMode == SNAP_Y) {
                        int ax = Math.abs(deltaX);
                        int ay = Math.abs(deltaY);
                        if (mSnapScrollMode == SNAP_X) {
                            // radical change means getting out of snap mode
                            if (ay > MAX_SLOPE_FOR_DIAG * ax
                                    && ay > MIN_BREAK_SNAP_CROSS_DISTANCE) {
                                mSnapScrollMode = SNAP_NONE;
                            }
                            // reverse direction means lock in the snap mode
                            if ((ax > MAX_SLOPE_FOR_DIAG * ay) &&
                                    ((mSnapPositive && 
                                    deltaX < -MIN_LOCK_SNAP_REVERSE_DISTANCE)
                                    || (!mSnapPositive &&
                                    deltaX > MIN_LOCK_SNAP_REVERSE_DISTANCE))) {
                                mSnapScrollMode = SNAP_X_LOCK;
                            }
                        } else {
                            // radical change means getting out of snap mode
                            if ((ax > MAX_SLOPE_FOR_DIAG * ay)
                                    && ax > MIN_BREAK_SNAP_CROSS_DISTANCE) {
                                mSnapScrollMode = SNAP_NONE;
                            }
                            // reverse direction means lock in the snap mode
                            if ((ay > MAX_SLOPE_FOR_DIAG * ax) &&
                                    ((mSnapPositive && 
                                    deltaY < -MIN_LOCK_SNAP_REVERSE_DISTANCE)
                                    || (!mSnapPositive && 
                                    deltaY > MIN_LOCK_SNAP_REVERSE_DISTANCE))) {
                                mSnapScrollMode = SNAP_Y_LOCK;
                            }
                        }
                    }

                    if (mSnapScrollMode == SNAP_X
                            || mSnapScrollMode == SNAP_X_LOCK) {
                        scrollBy(deltaX, 0);
                        mLastTouchX = x;
                    } else if (mSnapScrollMode == SNAP_Y
                            || mSnapScrollMode == SNAP_Y_LOCK) {
                        scrollBy(0, deltaY);
                        mLastTouchY = y;
                    } else {
                        scrollBy(deltaX, deltaY);
                        mLastTouchX = x;
                        mLastTouchY = y;
                    }
                    mLastTouchTime = eventTime;
                    mUserScroll = true;
                }

                if (mZoomControls != null && mMinZoomScale < mMaxZoomScale) {
                    if (mZoomControls.getVisibility() == View.VISIBLE) {
                        mPrivateHandler.removeCallbacks(mZoomControlRunnable);
                    } else {
                        mZoomControls.show(canZoomScrollOut());
                    }
                    mPrivateHandler.postDelayed(mZoomControlRunnable,
                            ZOOM_CONTROLS_TIMEOUT);
                }
                if (done) {
                    // return false to indicate that we can't pan out of the
                    // view space
                    return false;
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                switch (mTouchMode) {
                    case TOUCH_INIT_MODE: // tap
                        mPrivateHandler.removeMessages(SWITCH_TO_SHORTPRESS);
                        if (getSettings().supportZoom()) {
                            mPrivateHandler.sendMessageDelayed(mPrivateHandler
                                    .obtainMessage(RELEASE_SINGLE_TAP),
                                    DOUBLE_TAP_TIMEOUT);
                        } else {
                            // do short press now
                            mTouchMode = TOUCH_DONE_MODE;
                            doShortPress();
                        }
                        break;
                    case SCROLL_ZOOM_ANIMATION_IN:
                    case SCROLL_ZOOM_ANIMATION_OUT:
                        // no action during scroll animation
                        break;
                    case SCROLL_ZOOM_OUT:
                        if (LOGV_ENABLED) {
                            Log.v(LOGTAG, "ACTION_UP SCROLL_ZOOM_OUT"
                                    + " eventTime - mLastTouchTime="
                                    + (eventTime - mLastTouchTime));
                        }
                        // for now, always zoom back when the drag completes
                        if (true || eventTime - mLastTouchTime < TAP_TIMEOUT) {
                            // but if we tap, zoom in where we tap
                            if (eventTime - mLastTouchTime < TAP_TIMEOUT) {
                                zoomScrollTap(x, y);
                            }
                            // start zooming in back to the original view
                            setZoomScrollIn();
                            mTouchMode = SCROLL_ZOOM_ANIMATION_IN;
                            invalidate();
                        }
                        break;
                    case TOUCH_SHORTPRESS_START_MODE:
                    case TOUCH_SHORTPRESS_MODE: {
                        mPrivateHandler.removeMessages(SWITCH_TO_LONGPRESS);
                        if (eventTime - mLastTouchTime < TAP_TIMEOUT
                                && getSettings().supportZoom()) {
                            // Note: window manager will not release ACTION_UP
                            // until all the previous action events are
                            // returned. If GC happens, it can cause
                            // SWITCH_TO_SHORTPRESS message fired before
                            // ACTION_UP sent even time stamp of ACTION_UP is
                            // less than the tap time out. We need to treat this
                            // as tap instead of short press.
                            mTouchMode = TOUCH_INIT_MODE;
                            mPrivateHandler.sendMessageDelayed(mPrivateHandler
                                    .obtainMessage(RELEASE_SINGLE_TAP),
                                    DOUBLE_TAP_TIMEOUT);
                        } else {
                            mTouchMode = TOUCH_DONE_MODE;
                            doShortPress();
                        }
                        break;
                    }
                    case TOUCH_DRAG_MODE:
                        // if the user waits a while w/o moving before the
                        // up, we don't want to do a fling
                        if (eventTime - mLastTouchTime <= MIN_FLING_TIME) {
                            mVelocityTracker.addMovement(ev);
                            doFling();
                            break;
                        }
                        WebViewCore.resumeUpdate(mWebViewCore);
                        break;
                    case TOUCH_DRAG_START_MODE:
                    case TOUCH_DONE_MODE:
                        // do nothing
                        break;
                }
                // we also use mVelocityTracker == null to tell us that we are
                // not "moving around", so we can take the slower/prettier
                // mode in the drawing code
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                // we also use mVelocityTracker == null to tell us that we are
                // not "moving around", so we can take the slower/prettier
                // mode in the drawing code
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                if (mTouchMode == SCROLL_ZOOM_OUT ||
                        mTouchMode == SCROLL_ZOOM_ANIMATION_IN) {
                    scrollTo(mZoomScrollX, mZoomScrollY);
                } else if (mTouchMode == TOUCH_DRAG_MODE) {
                    WebViewCore.resumeUpdate(mWebViewCore);
                }
                mPrivateHandler.removeMessages(SWITCH_TO_SHORTPRESS);
                mPrivateHandler.removeMessages(SWITCH_TO_LONGPRESS);
                mPrivateHandler.removeMessages(RELEASE_SINGLE_TAP);
                mTouchMode = TOUCH_DONE_MODE;
                int contentX = viewToContent((int) mLastTouchX + mScrollX);
                int contentY = viewToContent((int) mLastTouchY + mScrollY);
                if (inEditingMode()) {
                    mTextEntry.updateCachedTextfield();
                }
                nativeClearFocus(contentX, contentY);
                break;
            }
        }
        return true;
    }
    
    private long mTrackballFirstTime = 0;
    private long mTrackballLastTime = 0;
    private float mTrackballRemainsX = 0.0f;
    private float mTrackballRemainsY = 0.0f;
    private int mTrackballXMove = 0;
    private int mTrackballYMove = 0;
    private boolean mExtendSelection = false;
    private static final int TRACKBALL_KEY_TIMEOUT = 1000;
    private static final int TRACKBALL_TIMEOUT = 200;
    private static final int TRACKBALL_WAIT = 100;
    private static final int TRACKBALL_SCALE = 400;
    private static final int TRACKBALL_SCROLL_COUNT = 5;
    private static final int TRACKBALL_MULTIPLIER = 3;
    private static final int SELECT_CURSOR_OFFSET = 16;
    private int mSelectX = 0;
    private int mSelectY = 0;
    private boolean mShiftIsPressed = false;
    private boolean mAltIsPressed = false;
    private boolean mTrackballDown = false;
    private long mTrackballUpTime = 0;
    private long mLastFocusTime = 0;
    private Rect mLastFocusBounds;
    
    // Used to determine that the trackball is down AND that it has not
    // been moved while down, whereas mTrackballDown is true until we
    // receive an ACTION_UP
    private boolean mTrackTrackball = false;
    
    // Set by default; BrowserActivity clears to interpret trackball data
    // directly for movement. Currently, the framework only passes 
    // arrow key events, not trackball events, from one child to the next
    private boolean mMapTrackballToArrowKeys = true;
    
    public void setMapTrackballToArrowKeys(boolean setMap) {
        mMapTrackballToArrowKeys = setMap;
    }

    void resetTrackballTime() {
        mTrackballLastTime = 0;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        long time = ev.getEventTime();
        if (mAltIsPressed) {
            if (ev.getY() > 0) pageDown(true);
            if (ev.getY() < 0) pageUp(true);
            return true;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mPrivateHandler.removeMessages(SWITCH_TO_ENTER);
            mPrivateHandler.sendMessageDelayed(
                    mPrivateHandler.obtainMessage(LONG_PRESS_TRACKBALL), 1000);
            mTrackTrackball = true;
            mTrackballDown = true;
            if (time - mLastFocusTime <= TRACKBALL_TIMEOUT
                    && !mLastFocusBounds.equals(nativeGetFocusRingBounds())) {
                nativeSelectBestAt(mLastFocusBounds);
            }
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "onTrackballEvent down ev=" + ev
                        + " time=" + time 
                        + " mLastFocusTime=" + mLastFocusTime);
            }
            return false; // let common code in onKeyDown at it
        } else if (mTrackTrackball) {
            mPrivateHandler.removeMessages(LONG_PRESS_TRACKBALL);
            mTrackTrackball = false;
        } 
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            mTrackballDown = false;
            mTrackballUpTime = time;
            if (mShiftIsPressed) {
                mExtendSelection = true;
            }
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "onTrackballEvent up ev=" + ev
                        + " time=" + time 
                );
            }
            return false; // let common code in onKeyUp at it
        }
        if (mMapTrackballToArrowKeys && mShiftIsPressed == false) {
            if (LOGV_ENABLED) Log.v(LOGTAG, "onTrackballEvent gmail quit");
            return false;
        }
        // no move if we're still waiting on SWITCH_TO_ENTER timeout
        if (mTouchMode == TOUCH_DOUBLECLICK_MODE) {
            if (LOGV_ENABLED) Log.v(LOGTAG, "onTrackballEvent 2 click quit");
            return true;
        }
        if (mTrackballDown) {
            if (LOGV_ENABLED) Log.v(LOGTAG, "onTrackballEvent down quit");
            return true; // discard move if trackball is down
        }
        if (time - mTrackballUpTime < TRACKBALL_TIMEOUT) {
            if (LOGV_ENABLED) Log.v(LOGTAG, "onTrackballEvent up timeout quit");
            return true;
        }
        // TODO: alternatively we can do panning as touch does
        switchOutDrawHistory();
        if (time - mTrackballLastTime > TRACKBALL_TIMEOUT) {
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "onTrackballEvent time=" 
                        + time + " last=" + mTrackballLastTime);
            }
            mTrackballFirstTime = time;
            mTrackballXMove = mTrackballYMove = 0;
        }
        mTrackballLastTime = time;
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "onTrackballEvent ev=" + ev + " time=" + time);
        }
        mTrackballRemainsX += ev.getX();
        mTrackballRemainsY += ev.getY();
        doTrackball(time);
        return true;
    }
    
    void moveSelection(float xRate, float yRate) {
        if (mNativeClass == 0)
            return;
        int width = getViewWidth();
        int height = getViewHeight();
        mSelectX += scaleTrackballX(xRate, width);
        mSelectY += scaleTrackballY(yRate, height);
        int maxX = width + mScrollX;
        int maxY = height + mScrollY;
        mSelectX = Math.min(maxX, Math.max(mScrollX - SELECT_CURSOR_OFFSET
                , mSelectX));
        mSelectY = Math.min(maxY, Math.max(mScrollY - SELECT_CURSOR_OFFSET
                , mSelectY));
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "moveSelection" 
                    + " mSelectX=" + mSelectX
                    + " mSelectY=" + mSelectY
                    + " mScrollX=" + mScrollX
                    + " mScrollY=" + mScrollY
                    + " xRate=" + xRate
                    + " yRate=" + yRate
                    );
        }
        nativeMoveSelection(viewToContent(mSelectX)
                , viewToContent(mSelectY), mExtendSelection);
        int scrollX = mSelectX < mScrollX ? -SELECT_CURSOR_OFFSET
                : mSelectX > maxX - SELECT_CURSOR_OFFSET ? SELECT_CURSOR_OFFSET 
                : 0;
        int scrollY = mSelectY < mScrollY ? -SELECT_CURSOR_OFFSET
                : mSelectY > maxY - SELECT_CURSOR_OFFSET ? SELECT_CURSOR_OFFSET 
                : 0;
        pinScrollBy(scrollX, scrollY, true);
        Rect select = new Rect(mSelectX, mSelectY, mSelectX + 1, mSelectY + 1);
        requestRectangleOnScreen(select);
        invalidate();
   }

    private int scaleTrackballX(float xRate, int width) {
        int xMove = (int) (xRate / TRACKBALL_SCALE * width);
        int nextXMove = xMove;
        if (xMove > 0) {
            if (xMove > mTrackballXMove) {
                xMove -= mTrackballXMove;
            }
        } else if (xMove < mTrackballXMove) {
            xMove -= mTrackballXMove;
        }
        mTrackballXMove = nextXMove;
        return xMove;
    }

    private int scaleTrackballY(float yRate, int height) {
        int yMove = (int) (yRate / TRACKBALL_SCALE * height);
        int nextYMove = yMove;
        if (yMove > 0) {
            if (yMove > mTrackballYMove) {
                yMove -= mTrackballYMove;
            }
        } else if (yMove < mTrackballYMove) {
            yMove -= mTrackballYMove;
        }
        mTrackballYMove = nextYMove;
        return yMove;
    }

    private int keyCodeToSoundsEffect(int keyCode) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return SoundEffectConstants.NAVIGATION_UP;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return SoundEffectConstants.NAVIGATION_RIGHT;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return SoundEffectConstants.NAVIGATION_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return SoundEffectConstants.NAVIGATION_LEFT;
        }
        throw new IllegalArgumentException("keyCode must be one of " +
                "{KEYCODE_DPAD_UP, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_DOWN, " +
                "KEYCODE_DPAD_LEFT}.");
    }

    private void doTrackball(long time) {
        int elapsed = (int) (mTrackballLastTime - mTrackballFirstTime);
        if (elapsed == 0) {
            elapsed = TRACKBALL_TIMEOUT;
        }
        float xRate = mTrackballRemainsX * 1000 / elapsed; 
        float yRate = mTrackballRemainsY * 1000 / elapsed;
        if (mShiftIsPressed) {
            moveSelection(xRate, yRate);
            mTrackballRemainsX = mTrackballRemainsY = 0;
            return;
        }
        float ax = Math.abs(xRate);
        float ay = Math.abs(yRate);
        float maxA = Math.max(ax, ay);
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "doTrackball elapsed=" + elapsed
                    + " xRate=" + xRate
                    + " yRate=" + yRate
                    + " mTrackballRemainsX=" + mTrackballRemainsX
                    + " mTrackballRemainsY=" + mTrackballRemainsY);
        }
        int width = mContentWidth - getViewWidth();
        int height = mContentHeight - getViewHeight();
        if (width < 0) width = 0;
        if (height < 0) height = 0;
        if (mTouchMode == SCROLL_ZOOM_OUT) {
            int oldX = mZoomScrollX;
            int oldY = mZoomScrollY;
            int maxWH = Math.max(width, height);
            mZoomScrollX += scaleTrackballX(xRate, maxWH);
            mZoomScrollY += scaleTrackballY(yRate, maxWH);
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "doTrackball SCROLL_ZOOM_OUT"
                        + " mZoomScrollX=" + mZoomScrollX 
                        + " mZoomScrollY=" + mZoomScrollY);
            }
            mZoomScrollX = Math.min(width, Math.max(0, mZoomScrollX));
            mZoomScrollY = Math.min(height, Math.max(0, mZoomScrollY));
            if (oldX != mZoomScrollX || oldY != mZoomScrollY) {
                invalidate();
            }
            mTrackballRemainsX = mTrackballRemainsY = 0;
            return;
        }
        ax = Math.abs(mTrackballRemainsX * TRACKBALL_MULTIPLIER);
        ay = Math.abs(mTrackballRemainsY * TRACKBALL_MULTIPLIER);
        maxA = Math.max(ax, ay);
        int count = Math.max(0, (int) maxA);
        int oldScrollX = mScrollX;
        int oldScrollY = mScrollY;
        if (count > 0) {
            int selectKeyCode = ax < ay ? mTrackballRemainsY < 0 ? 
                    KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN : 
                    mTrackballRemainsX < 0 ? KeyEvent.KEYCODE_DPAD_LEFT :
                    KeyEvent.KEYCODE_DPAD_RIGHT;
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "doTrackball keyCode=" + selectKeyCode 
                        + " count=" + count
                        + " mTrackballRemainsX=" + mTrackballRemainsX
                        + " mTrackballRemainsY=" + mTrackballRemainsY);
            }
            if (navHandledKey(selectKeyCode, count, false, time)) {
                playSoundEffect(keyCodeToSoundsEffect(selectKeyCode));
            }
            mTrackballRemainsX = mTrackballRemainsY = 0;
        }
        if (count >= TRACKBALL_SCROLL_COUNT) {
            int xMove = scaleTrackballX(xRate, width);
            int yMove = scaleTrackballY(yRate, height);
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "doTrackball pinScrollBy"
                        + " count=" + count
                        + " xMove=" + xMove + " yMove=" + yMove
                        + " mScrollX-oldScrollX=" + (mScrollX-oldScrollX) 
                        + " mScrollY-oldScrollY=" + (mScrollY-oldScrollY) 
                        );
            }
            if (Math.abs(mScrollX - oldScrollX) > Math.abs(xMove)) {
                xMove = 0;
            }
            if (Math.abs(mScrollY - oldScrollY) > Math.abs(yMove)) {
                yMove = 0;
            }
            if (xMove != 0 || yMove != 0) {
                pinScrollBy(xMove, yMove, true);
            }
            mUserScroll = true;
        } 
        mWebViewCore.sendMessage(EventHub.UNBLOCK_FOCUS);        
    }

    public void flingScroll(int vx, int vy) {
        int maxX = Math.max(computeHorizontalScrollRange() - getViewWidth(), 0);
        int maxY = Math.max(computeVerticalScrollRange() - getViewHeight(), 0);
        
        mScroller.fling(mScrollX, mScrollY, vx, vy, 0, maxX, 0, maxY);
        invalidate();
    }
    
    private void doFling() {
        if (mVelocityTracker == null) {
            return;
        }
        int maxX = Math.max(computeHorizontalScrollRange() - getViewWidth(), 0);
        int maxY = Math.max(computeVerticalScrollRange() - getViewHeight(), 0);

        mVelocityTracker.computeCurrentVelocity(1000);
        int vx = (int) mVelocityTracker.getXVelocity();
        int vy = (int) mVelocityTracker.getYVelocity();

        if (mSnapScrollMode != SNAP_NONE) {
            if (mSnapScrollMode == SNAP_X || mSnapScrollMode == SNAP_X_LOCK) {
                vy = 0;
            } else {
                vx = 0;
            }
        }
        
        if (true /* EMG release: make our fling more like Maps' */) {
            // maps cuts their velocity in half
            vx = vx * 3 / 4;
            vy = vy * 3 / 4;
        }

        mScroller.fling(mScrollX, mScrollY, -vx, -vy, 0, maxX, 0, maxY);
        // TODO: duration is calculated based on velocity, if the range is
        // small, the animation will stop before duration is up. We may
        // want to calculate how long the animation is going to run to precisely
        // resume the webcore update.
        final int time = mScroller.getDuration();
        mPrivateHandler.sendEmptyMessageDelayed(RESUME_WEBCORE_UPDATE, time);
        invalidate();
    }

    private boolean zoomWithPreview(float scale) {
        float oldScale = mActualScale;

        // snap to 100% if it is close
        if (scale > 0.95f && scale < 1.05f) {
            scale = 1.0f;
        }

        setNewZoomScale(scale, false);

        if (oldScale != mActualScale) {
            // use mZoomPickerScale to see zoom preview first
            mZoomStart = SystemClock.uptimeMillis();
            mInvInitialZoomScale = 1.0f / oldScale;
            mInvFinalZoomScale = 1.0f / mActualScale;
            mZoomScale = mActualScale;
            invalidate();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a view containing zoom controls i.e. +/- buttons. The caller is
     * in charge of installing this view to the view hierarchy. This view will
     * become visible when the user starts scrolling via touch and fade away if
     * the user does not interact with it.
     */
    public View getZoomControls() {
        if (!getSettings().supportZoom()) {
            Log.w(LOGTAG, "This WebView doesn't support zoom.");
            return null;
        }
        if (mZoomControls == null) {
            mZoomControls = createZoomControls();
            
            /*
             * need to be set to VISIBLE first so that getMeasuredHeight() in
             * {@link #onSizeChanged()} can return the measured value for proper
             * layout.
             */
            mZoomControls.setVisibility(View.VISIBLE);
            mZoomControlRunnable = new Runnable() {
                public void run() {
                    
                    /* Don't dismiss the controls if the user has
                     * focus on them. Wait and check again later.
                     */
                    if (!mZoomControls.hasFocus()) {
                        mZoomControls.hide();
                    } else {
                        mPrivateHandler.removeCallbacks(mZoomControlRunnable);
                        mPrivateHandler.postDelayed(mZoomControlRunnable,
                                ZOOM_CONTROLS_TIMEOUT);
                    }
                }
            };
        }
        return mZoomControls;
    }

    /**
     * Perform zoom in in the webview
     * @return TRUE if zoom in succeeds. FALSE if no zoom changes.
     */
    public boolean zoomIn() {
        // TODO: alternatively we can disallow this during draw history mode
        switchOutDrawHistory();
        return zoomWithPreview(mActualScale * 1.25f);
    }

    /**
     * Perform zoom out in the webview
     * @return TRUE if zoom out succeeds. FALSE if no zoom changes.
     */
    public boolean zoomOut() {
        // TODO: alternatively we can disallow this during draw history mode
        switchOutDrawHistory();
        return zoomWithPreview(mActualScale * 0.8f);
    }

    private ExtendedZoomControls createZoomControls() {
        ExtendedZoomControls zoomControls = new ExtendedZoomControls(mContext
            , null);
        zoomControls.setOnZoomInClickListener(new OnClickListener() {
            public void onClick(View v) {
                // reset time out
                mPrivateHandler.removeCallbacks(mZoomControlRunnable);
                mPrivateHandler.postDelayed(mZoomControlRunnable,
                        ZOOM_CONTROLS_TIMEOUT);
                zoomIn();
            }
        });
        zoomControls.setOnZoomOutClickListener(new OnClickListener() {
            public void onClick(View v) {
                // reset time out
                mPrivateHandler.removeCallbacks(mZoomControlRunnable);
                mPrivateHandler.postDelayed(mZoomControlRunnable,
                        ZOOM_CONTROLS_TIMEOUT);
                zoomOut();
            }
        });
        zoomControls.setOnZoomMagnifyClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPrivateHandler.removeCallbacks(mZoomControlRunnable);
                mPrivateHandler.postDelayed(mZoomControlRunnable,
                        ZOOM_CONTROLS_TIMEOUT);
                zoomScrollOut();
            }
        });
        return zoomControls;
    }

    private void updateSelection() {
        if (mNativeClass == 0) {
            return;
        }
        // mLastTouchX and mLastTouchY are the point in the current viewport
        int contentX = viewToContent((int) mLastTouchX + mScrollX);
        int contentY = viewToContent((int) mLastTouchY + mScrollY);
        int contentSize = ViewConfiguration.getTouchSlop();
        Rect rect = new Rect(contentX - contentSize, contentY - contentSize,
                contentX + contentSize, contentY + contentSize);
        // If we were already focused on a textfield, update its cache.
        if (inEditingMode()) {
            mTextEntry.updateCachedTextfield();
        }
        nativeSelectBestAt(rect);
    }

    /*package*/ void shortPressOnTextField() {
        if (inEditingMode()) {
            View v = mTextEntry;
            int x = viewToContent((v.getLeft() + v.getRight()) >> 1);
            int y = viewToContent((v.getTop() + v.getBottom()) >> 1);
            int contentSize = ViewConfiguration.getTouchSlop();
            nativeMotionUp(x, y, contentSize, true);
        }
    }

    private void doShortPress() {
        if (mNativeClass == 0) {
            return;
        }
        switchOutDrawHistory();
        // mLastTouchX and mLastTouchY are the point in the current viewport
        int contentX = viewToContent((int) mLastTouchX + mScrollX);
        int contentY = viewToContent((int) mLastTouchY + mScrollY);
        int contentSize = ViewConfiguration.getTouchSlop();
        nativeMotionUp(contentX, contentY, contentSize, true);

        // call uiOverride next to check whether it is a special node,
        // phone/email/address, which are not handled by WebKit
        if (nativeUpdateFocusNode()) {
            FocusNode node = mFocusNode;
            if (!node.mIsTextField && !node.mIsTextArea) {
                if (mCallbackProxy.uiOverrideUrlLoading(node.mText)) {
                    return;
                }
            }
            playSoundEffect(SoundEffectConstants.CLICK);
        }
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        boolean result = false;
        if (inEditingMode()) {
            result = mTextEntry.requestFocus(direction, previouslyFocusedRect);
        } else {
            result = super.requestFocus(direction, previouslyFocusedRect);
            if (mWebViewCore.getSettings().getNeedInitialFocus()) {
                // For cases such as GMail, where we gain focus from a direction,
                // we want to move to the first available link.
                // FIXME: If there are no visible links, we may not want to
                int fakeKeyDirection = 0;
                switch(direction) {
                    case View.FOCUS_UP:
                        fakeKeyDirection = KeyEvent.KEYCODE_DPAD_UP;
                        break;
                    case View.FOCUS_DOWN:
                        fakeKeyDirection = KeyEvent.KEYCODE_DPAD_DOWN;
                        break;
                    case View.FOCUS_LEFT:
                        fakeKeyDirection = KeyEvent.KEYCODE_DPAD_LEFT;
                        break;
                    case View.FOCUS_RIGHT:
                        fakeKeyDirection = KeyEvent.KEYCODE_DPAD_RIGHT;
                        break;
                    default:
                        return result;
                }
                if (mNativeClass != 0 && !nativeUpdateFocusNode()) {
                    navHandledKey(fakeKeyDirection, 1, true, 0);
                }
            }
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int measuredHeight = heightSize;
        int measuredWidth = widthSize;

        // Grab the content size from WebViewCore.
        int contentHeight = mContentHeight;
        int contentWidth = mContentWidth;

//        Log.d(LOGTAG, "------- measure " + heightMode);

        if (heightMode != MeasureSpec.EXACTLY) {
            mHeightCanMeasure = true;
            measuredHeight = contentHeight;
            if (heightMode == MeasureSpec.AT_MOST) {
                // If we are larger than the AT_MOST height, then our height can
                // no longer be measured and we should scroll internally.
                if (measuredHeight > heightSize) {
                    measuredHeight = heightSize;
                    mHeightCanMeasure = false;
                }
            }
        }
        if (mNativeClass != 0) {
            nativeSetHeightCanMeasure(mHeightCanMeasure);
        }
        // For the width, always use the given size unless unspecified.
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            mWidthCanMeasure = true;
            measuredWidth = contentWidth;
        }

        synchronized (this) {
            setMeasuredDimension(measuredWidth, measuredHeight);
        }
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child,
                                                 Rect rect,
                                                 boolean immediate) {
        rect.offset(child.getLeft() - child.getScrollX(),
                child.getTop() - child.getScrollY());

        int height = getHeight() - getHorizontalScrollbarHeight();
        int screenTop = mScrollY;
        int screenBottom = screenTop + height;

        int scrollYDelta = 0;

        if (rect.bottom > screenBottom && rect.top > screenTop) {
            if (rect.height() > height) {
                scrollYDelta += (rect.top - screenTop);
            } else {
                scrollYDelta += (rect.bottom - screenBottom);
            }
        } else if (rect.top < screenTop) {
            scrollYDelta -= (screenTop - rect.top);
        }

        int width = getWidth() - getVerticalScrollbarWidth();
        int screenLeft = mScrollX;
        int screenRight = screenLeft + width;

        int scrollXDelta = 0;

        if (rect.right > screenRight && rect.left > screenLeft) {
            if (rect.width() > width) {
                scrollXDelta += (rect.left - screenLeft);
            } else {
                scrollXDelta += (rect.right - screenRight);
            }
        } else if (rect.left < screenLeft) {
            scrollXDelta -= (screenLeft - rect.left);
        }

        if ((scrollYDelta | scrollXDelta) != 0) {
            return pinScrollBy(scrollXDelta, scrollYDelta, !immediate);
        }

        return false;
    }
    
    /* package */ void replaceTextfieldText(int oldStart, int oldEnd,
            String replace, int newStart, int newEnd) {
        HashMap arg = new HashMap();
        arg.put("focusData", new WebViewCore.FocusData(mFocusData));
        arg.put("replace", replace);
        arg.put("start", new Integer(newStart));
        arg.put("end", new Integer(newEnd));
        mWebViewCore.sendMessage(EventHub.REPLACE_TEXT, oldStart, oldEnd, arg);
    }

    /* package */ void passToJavaScript(String currentText, KeyEvent event) {
        HashMap arg = new HashMap();
        arg.put("focusData", new WebViewCore.FocusData(mFocusData));
        arg.put("event", event);
        arg.put("currentText", currentText);
        // Increase our text generation number, and pass it to webcore thread
        mTextGeneration++;
        mWebViewCore.sendMessage(EventHub.PASS_TO_JS, mTextGeneration, 0, arg);
        // WebKit's document state is not saved until about to leave the page.
        // To make sure the host application, like Browser, has the up to date 
        // document state when it goes to background, we force to save the 
        // document state.
        mWebViewCore.removeMessages(EventHub.SAVE_DOCUMENT_STATE);
        mWebViewCore.sendMessageDelayed(EventHub.SAVE_DOCUMENT_STATE,
                new WebViewCore.FocusData(mFocusData), 1000);
    }

    /* package */ WebViewCore getWebViewCore() {
        return mWebViewCore;
    }

    //-------------------------------------------------------------------------
    // Methods can be called from a separate thread, like WebViewCore
    // If it needs to call the View system, it has to send message.
    //-------------------------------------------------------------------------

    /**
     * General handler to receive message coming from webkit thread
     */
    class PrivateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REMEMBER_PASSWORD: {
                    mDatabase.setUsernamePassword(
                            msg.getData().getString("host"),
                            msg.getData().getString("username"),
                            msg.getData().getString("password"));
                    ((Message) msg.obj).sendToTarget();
                    break;
                }
                case NEVER_REMEMBER_PASSWORD: {
                    mDatabase.setUsernamePassword(
                            msg.getData().getString("host"), null, null);
                    ((Message) msg.obj).sendToTarget();
                    break;
                }
                case SWITCH_TO_SHORTPRESS: {
                    if (mTouchMode == TOUCH_INIT_MODE) {
                        mTouchMode = TOUCH_SHORTPRESS_START_MODE;
                        updateSelection();
                    }
                    break;
                }
                case SWITCH_TO_LONGPRESS: {
                    mTouchMode = TOUCH_DONE_MODE;
                    performLongClick();
                    updateTextEntry();
                    break;
                }
                case RELEASE_SINGLE_TAP: {
                    mTouchMode = TOUCH_DONE_MODE;
                    doShortPress();
                    break;
                }
                case SWITCH_TO_ENTER:
                    if (LOGV_ENABLED) Log.v(LOGTAG, "SWITCH_TO_ENTER");
                    mTouchMode = TOUCH_DONE_MODE;
                    onKeyUp(KeyEvent.KEYCODE_ENTER
                            , new KeyEvent(KeyEvent.ACTION_UP
                            , KeyEvent.KEYCODE_ENTER));
                    break;
                case SCROLL_BY_MSG_ID:
                    setContentScrollBy(msg.arg1, msg.arg2);
                    break;
                case SYNC_SCROLL_TO_MSG_ID:
                    if (mUserScroll) {
                        // if user has scrolled explicitly, don't sync the 
                        // scroll position any more
                        mUserScroll = false;
                        break;
                    }
                    // fall through
                case SCROLL_TO_MSG_ID:
                    if (setContentScrollTo(msg.arg1, msg.arg2)) {
                        // if we can't scroll to the exact position due to pin,
                        // send a message to WebCore to re-scroll when we get a 
                        // new picture
                        mUserScroll = false;
                        mWebViewCore.sendMessage(EventHub.SYNC_SCROLL,
                                msg.arg1, msg.arg2);
                    }
                    break;
                case SPAWN_SCROLL_TO_MSG_ID:
                    spawnContentScrollTo(msg.arg1, msg.arg2);
                    break;
                case NEW_PICTURE_MSG_ID:
                    // called for new content
                    final Point viewSize = (Point) msg.obj;
                    if (mZoomScale > 0) {
                        if (Math.abs(mZoomScale * viewSize.x -
                                getViewWidth()) < 1) {
                            mZoomScale = 0;
                            mWebViewCore.sendMessage(EventHub.SET_SNAP_ANCHOR,
                                    0, 0);
                        }
                    }
                    // We update the layout (i.e. request a layout from the
                    // view system) if the last view size that we sent to
                    // WebCore matches the view size of the picture we just
                    // received in the fixed dimension.
                    final boolean updateLayout = viewSize.x == mLastWidthSent
                            && viewSize.y == mLastHeightSent;
                    recordNewContentSize(msg.arg1, msg.arg2, updateLayout);
                    invalidate();
                    if (mPictureListener != null) {
                        mPictureListener.onNewPicture(WebView.this, capturePicture());
                    }
                    break;
                case WEBCORE_INITIALIZED_MSG_ID:
                    // nativeCreate sets mNativeClass to a non-zero value
                    nativeCreate(msg.arg1);
                    break;
                case UPDATE_TEXTFIELD_TEXT_MSG_ID:
                    // Make sure that the textfield is currently focused
                    // and representing the same node as the pointer.  
                    if (inEditingMode() && 
                            mTextEntry.isSameTextField(msg.arg1)) {
                        if (msg.getData().getBoolean("password")) {
                            Spannable text = (Spannable) mTextEntry.getText();
                            int start = Selection.getSelectionStart(text);
                            int end = Selection.getSelectionEnd(text);
                            mTextEntry.setInPassword(true);
                            // Restore the selection, which may have been
                            // ruined by setInPassword.
                            Spannable pword = (Spannable) mTextEntry.getText();
                            Selection.setSelection(pword, start, end);
                        // If the text entry has created more events, ignore
                        // this one.
                        } else if (msg.arg2 == mTextGeneration) {
                            mTextEntry.setTextAndKeepSelection(
                                    (String) msg.obj);
                        }
                    }
                    break;
                case DID_FIRST_LAYOUT_MSG_ID:
                    if (mNativeClass == 0) {
                        break;
                    }
// Do not reset the focus or clear the text; the user may have already
// navigated or entered text at this point. The focus should have gotten 
// reset, if need be, when the focus cache was built. Similarly, the text
// view should already be torn down and rebuilt if needed.
//                    nativeResetFocus();
//                    clearTextEntry();
                    HashMap scaleLimit = (HashMap) msg.obj;
                    int minScale = (Integer) scaleLimit.get("minScale");
                    if (minScale == 0) {
                        mMinZoomScale = DEFAULT_MIN_ZOOM_SCALE;
                    } else {
                        mMinZoomScale = (float) (minScale / 100.0);
                    }
                    int maxScale = (Integer) scaleLimit.get("maxScale");
                    if (maxScale == 0) {
                        mMaxZoomScale = DEFAULT_MAX_ZOOM_SCALE;
                    } else {
                        mMaxZoomScale = (float) (maxScale / 100.0);
                    }
                    // If history Picture is drawn, don't update zoomWidth
                    if (mDrawHistory) {
                        break;
                    }
                    int width = getViewWidth();
                    if (width == 0) {
                        break;
                    }
                    int initialScale = msg.arg1;
                    int viewportWidth = msg.arg2;
                    float scale = 1.0f;
                    if (mInitialScale > 0) {
                        scale = mInitialScale / 100.0f;
                    } else  {
                        if (mWebViewCore.getSettings().getUseWideViewPort()) {
                            // force viewSizeChanged by setting mLastWidthSent
                            // to 0
                            mLastWidthSent = 0;
                        }
                        // by default starting a new page with 100% zoom scale.
                        scale = initialScale == 0 ? (viewportWidth > 0 ? 
                                ((float) width / viewportWidth) : 1.0f)
                                : initialScale / 100.0f;
                    }
                    setNewZoomScale(scale, false);
                    break;
                case MARK_NODE_INVALID_ID:
                    nativeMarkNodeInvalid(msg.arg1);
                    break;
                case NOTIFY_FOCUS_SET_MSG_ID:
                    if (mNativeClass != 0) {
                        nativeNotifyFocusSet(inEditingMode());
                    }
                    break;
                case UPDATE_TEXT_ENTRY_MSG_ID:
                    updateTextEntry();
                    break;
                case RECOMPUTE_FOCUS_MSG_ID:
                    if (mNativeClass != 0) {
                        nativeRecomputeFocus();
                    }
                    break;
                case UPDATE_TEXT_ENTRY_ADAPTER:
                    HashMap data = (HashMap) msg.obj;
                    if (mTextEntry.isSameTextField(msg.arg1)) {
                        ArrayAdapter<String> adapter =
                                (ArrayAdapter<String>) data.get("adapter");
                        mTextEntry.setAdapter(adapter);
                    }
                    break;
                case UPDATE_CLIPBOARD:
                    String str = (String) msg.obj;
                    if (LOGV_ENABLED) {
                        Log.v(LOGTAG, "UPDATE_CLIPBOARD " + str);
                    }
                    try {
                        IClipboard clip = IClipboard.Stub.asInterface(
                                ServiceManager.getService("clipboard"));
                                clip.setClipboardText(str);
                    } catch (android.os.RemoteException e) {
                        Log.e(LOGTAG, "Clipboard failed", e);
                    }
                    break;
                case RESUME_WEBCORE_UPDATE:
                    WebViewCore.resumeUpdate(mWebViewCore);
                    break;

                case LONG_PRESS_TRACKBALL:
                    mTrackTrackball = false;
                    mTrackballDown = false;
                    performLongClick();
                    break;

                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    // Class used to use a dropdown for a <select> element
    private class InvokeListBox implements Runnable {
        // Strings for the labels in the listbox.
        private String[]    mArray;
        // Array representing whether each item is enabled.
        private boolean[]   mEnableArray;
        // Whether the listbox allows multiple selection.
        private boolean     mMultiple;
        // Passed in to a list with multiple selection to tell
        // which items are selected.
        private int[]       mSelectedArray;
        // Passed in to a list with single selection to tell 
        // where the initial selection is.
        private int         mSelection;

        private Container[] mContainers;

        // Need these to provide stable ids to my ArrayAdapter,
        // which normally does not have stable ids. (Bug 1250098)
        private class Container extends Object {
            String  mString;
            boolean mEnabled;
            int     mId;

            public String toString() {
                return mString;
            }
        }

        /**
         *  Subclass ArrayAdapter so we can disable OptionGroupLabels, 
         *  and allow filtering.
         */
        private class MyArrayListAdapter extends ArrayAdapter<Container> {
            public MyArrayListAdapter(Context context, Container[] objects, boolean multiple) {
                super(context, 
                            multiple ? com.android.internal.R.layout.select_dialog_multichoice :
                            com.android.internal.R.layout.select_dialog_singlechoice, 
                            objects);
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }

            private Container item(int position) {
                if (position < 0 || position >= getCount()) {
                    return null;
                }
                return (Container) getItem(position);
            }

            @Override
            public long getItemId(int position) {
                Container item = item(position);
                if (item == null) {
                    return -1;
                }
                return item.mId;
            }

            @Override
            public boolean areAllItemsEnabled() {
                return false;
            }

            @Override
            public boolean isEnabled(int position) {
                Container item = item(position);
                if (item == null) {
                    return false;
                }
                return item.mEnabled;
            }
        }

        private InvokeListBox(String[] array,
                boolean[] enabled, int[] selected) {
            mMultiple = true;
            mSelectedArray = selected;

            int length = array.length;
            mContainers = new Container[length];
            for (int i = 0; i < length; i++) {
                mContainers[i] = new Container();
                mContainers[i].mString = array[i];
                mContainers[i].mEnabled = enabled[i];
                mContainers[i].mId = i;
            }
        }

        private InvokeListBox(String[] array, boolean[] enabled, int 
                selection) {
            mSelection = selection;
            mMultiple = false;

            int length = array.length;
            mContainers = new Container[length];
            for (int i = 0; i < length; i++) {
                mContainers[i] = new Container();
                mContainers[i].mString = array[i];
                mContainers[i].mEnabled = enabled[i];
                mContainers[i].mId = i;
            }
        }

        public void run() {
            final ListView listView = (ListView) LayoutInflater.from(mContext)
                    .inflate(com.android.internal.R.layout.select_dialog, null);
            final MyArrayListAdapter adapter = new 
                    MyArrayListAdapter(mContext, mContainers, mMultiple);
            AlertDialog.Builder b = new AlertDialog.Builder(mContext)
                    .setView(listView).setCancelable(true)
                    .setInverseBackgroundForced(true);
                    
            if (mMultiple) {
                b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mWebViewCore.sendMessage(
                                EventHub.LISTBOX_CHOICES, 
                                adapter.getCount(), 0,
                                listView.getCheckedItemPositions());
                    }});
                b.setNegativeButton(android.R.string.cancel, null);
            }
            final AlertDialog dialog = b.create();
            listView.setAdapter(adapter);
            listView.setFocusableInTouchMode(true);
            // There is a bug (1250103) where the checks in a ListView with
            // multiple items selected are associated with the positions, not
            // the ids, so the items do not properly retain their checks when 
            // filtered.  Do not allow filtering on multiple lists until
            // that bug is fixed.
            
            // Disable filter altogether
            // listView.setTextFilterEnabled(!mMultiple);
            if (mMultiple) {
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                int length = mSelectedArray.length;
                for (int i = 0; i < length; i++) {
                    listView.setItemChecked(mSelectedArray[i], true);
                }
            } else {
                listView.setOnItemClickListener(new OnItemClickListener() {
                    public void onItemClick(AdapterView parent, View v,
                            int position, long id) {
                        mWebViewCore.sendMessage(
                                EventHub.SINGLE_LISTBOX_CHOICE, (int)id, 0);
                        dialog.dismiss();
                    }
                });
                if (mSelection != -1) {
                    listView.setSelection(mSelection);
                }
            }
            dialog.show();
        }
    }

    /*
     * Request a dropdown menu for a listbox with multiple selection.
     *
     * @param array Labels for the listbox.
     * @param enabledArray  Which positions are enabled.
     * @param selectedArray Which positions are initally selected.
     */
    void requestListBox(String[] array, boolean[]enabledArray, int[]
            selectedArray) {
        mPrivateHandler.post(
                new InvokeListBox(array, enabledArray, selectedArray));
    }

    /*
     * Request a dropdown menu for a listbox with single selection or a single
     * <select> element.
     *
     * @param array Labels for the listbox.
     * @param enabledArray  Which positions are enabled.
     * @param selection Which position is initally selected.
     */
    void requestListBox(String[] array, boolean[]enabledArray, int selection) {
        mPrivateHandler.post(
                new InvokeListBox(array, enabledArray, selection));
    }

    // called by JNI
    private void sendFinalFocus(int frame, int node, int x, int y) {
        WebViewCore.FocusData focusData = new WebViewCore.FocusData();
        focusData.mFrame = frame;
        focusData.mNode = node;
        focusData.mX = x;
        focusData.mY = y;
        mWebViewCore.sendMessage(EventHub.SET_FINAL_FOCUS, 
                EventHub.NO_FOCUS_CHANGE_BLOCK, 0, focusData);
    }

    // called by JNI
    private void setFocusData(int moveGeneration, int buildGeneration,
            int frame, int node, int x, int y, boolean ignoreNullFocus) {
        mFocusData.mMoveGeneration = moveGeneration;
        mFocusData.mBuildGeneration = buildGeneration;
        mFocusData.mFrame = frame;
        mFocusData.mNode = node;
        mFocusData.mX = x;
        mFocusData.mY = y;
        mFocusData.mIgnoreNullFocus = ignoreNullFocus;
    }
    
    // called by JNI
    private void sendKitFocus() {
        WebViewCore.FocusData focusData = new WebViewCore.FocusData(mFocusData);
        mWebViewCore.sendMessageDelayed(EventHub.SET_KIT_FOCUS, focusData,
                SET_KIT_FOCUS_DELAY);
    }

    // called by JNI
    private void sendMotionUp(int touchGeneration, int buildGeneration,
            int frame, int node, int x, int y, int size, boolean isClick,
            boolean retry) {
        WebViewCore.TouchUpData touchUpData = new WebViewCore.TouchUpData();
        touchUpData.mMoveGeneration = touchGeneration;
        touchUpData.mBuildGeneration = buildGeneration;
        touchUpData.mSize = size;
        touchUpData.mIsClick = isClick;
        touchUpData.mRetry = retry;
        mFocusData.mFrame = touchUpData.mFrame = frame;
        mFocusData.mNode = touchUpData.mNode = node;
        mFocusData.mX = touchUpData.mX = x;
        mFocusData.mY = touchUpData.mY = y;
        mWebViewCore.sendMessage(EventHub.TOUCH_UP, touchUpData);
    }


    private int getScaledMaxXScroll() {
        int width;
        if (mHeightCanMeasure == false) {
            width = getViewWidth() / 4;
        } else {
            Rect visRect = new Rect();
            calcOurVisibleRect(visRect);
            width = visRect.width() / 2;
        }
        // FIXME the divisor should be retrieved from somewhere
        return viewToContent(width);
    }

    private int getScaledMaxYScroll() {
        int height;
        if (mHeightCanMeasure == false) {
            height = getViewHeight() / 4;
        } else {
            Rect visRect = new Rect();
            calcOurVisibleRect(visRect);
            height = visRect.height() / 2;
        }
        // FIXME the divisor should be retrieved from somewhere
        // the closest thing today is hard-coded into ScrollView.java
        // (from ScrollView.java, line 363)   int maxJump = height/2;
        return viewToContent(height);
    }

    /**
     * Called by JNI to invalidate view
     */
    private void viewInvalidate() {
        invalidate();
    }
    
    // return true if the key was handled
    private boolean navHandledKey(int keyCode, int count, boolean noScroll
            , long time) {
        if (mNativeClass == 0) {
            return false;
        }
        mLastFocusTime = time;
        mLastFocusBounds = nativeGetFocusRingBounds();
        boolean keyHandled = nativeMoveFocus(keyCode, count, noScroll) == false;
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "navHandledKey mLastFocusBounds=" + mLastFocusBounds
                    + " mLastFocusTime=" + mLastFocusTime
                    + " handled=" + keyHandled);
        }
        if (keyHandled == false || mHeightCanMeasure == false) {
            return keyHandled;
        }
        Rect contentFocus = nativeGetFocusRingBounds();
        if (contentFocus.isEmpty()) return keyHandled;
        Rect viewFocus = new Rect(contentToView(contentFocus.left)
                , contentToView(contentFocus.top)
                , contentToView(contentFocus.right)
                , contentToView(contentFocus.bottom));
        Rect visRect = new Rect();
        calcOurVisibleRect(visRect);
        Rect outset = new Rect(visRect);
        int maxXScroll = visRect.width() / 2;
        int maxYScroll = visRect.height() / 2;
        outset.inset(-maxXScroll, -maxYScroll);
        if (Rect.intersects(outset, viewFocus) == false) {
            return keyHandled;
        }
        // FIXME: Necessary because ScrollView/ListView do not scroll left/right
        int maxH = Math.min(viewFocus.right - visRect.right, maxXScroll);
        if (maxH > 0) {
            pinScrollBy(maxH, 0, true);
        } else {
            maxH = Math.max(viewFocus.left - visRect.left, -maxXScroll);
            if (maxH < 0) {
                pinScrollBy(maxH, 0, true);
            }
        }
        if (mLastFocusBounds.isEmpty()) return keyHandled;
        if (mLastFocusBounds.equals(contentFocus)) return keyHandled;
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "navHandledKey contentFocus=" + contentFocus);
        }
        requestRectangleOnScreen(viewFocus);
        mUserScroll = true;
        return keyHandled;
    }
    
    /**
     * Set the background color. It's white by default. Pass
     * zero to make the view transparent.
     * @param color   the ARGB color described by Color.java
     */
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mWebViewCore.sendMessage(EventHub.SET_BACKGROUND_COLOR, color);
    }

    public void debugDump() {
        nativeDebugDump();
        mWebViewCore.sendMessage(EventHub.DUMP_WEBKIT);
    }
    
    /**
     *  Update our cache with updatedText.
     *  @param updatedText  The new text to put in our cache.
     */
    /* package */ void updateCachedTextfield(String updatedText) {
        // Also place our generation number so that when we look at the cache
        // we recognize that it is up to date.
        nativeUpdateCachedTextfield(updatedText, mTextGeneration);
    }
    
    // Never call this version except by updateCachedTextfield(String) -
    // we always want to pass in our generation number.
    private native void     nativeUpdateCachedTextfield(String updatedText, 
            int generation);
    private native void     nativeClearFocus(int x, int y);
    private native void     nativeCreate(int ptr);
    private native void     nativeDebugDump();
    private native void     nativeDestroy();
    private native void     nativeDrawFocusRing(Canvas content);
    private native void     nativeDrawSelection(Canvas content
            , int x, int y, boolean extendSelection);
    private native boolean  nativeUpdateFocusNode();
    private native Rect     nativeGetFocusRingBounds();
    private native Rect     nativeGetNavBounds();
    private native void     nativeMarkNodeInvalid(int node);
    private native void     nativeMotionUp(int x, int y, int slop, boolean isClick);
    // returns false if it handled the key
    private native boolean  nativeMoveFocus(int keyCode, int count, 
            boolean noScroll);
    private native void     nativeNotifyFocusSet(boolean inEditingMode);
    private native void     nativeRecomputeFocus();
    private native void     nativeRecordButtons();
    private native void     nativeResetFocus();
    private native void     nativeResetNavClipBounds();
    private native void     nativeSelectBestAt(Rect rect);
    private native void     nativeSetFollowedLink(boolean followed);
    private native void     nativeSetHeightCanMeasure(boolean measure);
    private native void     nativeSetNavBounds(Rect rect);
    private native void     nativeSetNavClipBounds(Rect rect);
    private native boolean  nativeHasSrcUrl();
    private native boolean  nativeIsImage(int x, int y);
    private native void     nativeMoveSelection(int x, int y
            , boolean extendSelection);
    private native Region   nativeGetSelection();
}
