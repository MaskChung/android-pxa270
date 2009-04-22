/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DrawFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Picture;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Config;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;

import junit.framework.Assert;

final class WebViewCore {

    private static final String LOGTAG = "webcore";
    static final boolean DEBUG = false;
    static final boolean LOGV_ENABLED = DEBUG ? Config.LOGD : Config.LOGV;

    static {
        // Load libwebcore during static initialization. This happens in the
        // zygote process so it will be shared read-only across all app
        // processes.
        System.loadLibrary("webcore");
    }

    /*
     * WebViewCore always executes in the same thread as the native webkit.
     */

    // The WebView that corresponds to this WebViewCore.
    private WebView mWebView;
    // Proxy for handling callbacks from native code
    private final CallbackProxy mCallbackProxy;
    // Settings object for maintaining all settings
    private final WebSettings mSettings;
    // Context for initializing the BrowserFrame with the proper assets.
    private final Context mContext;
    // The pointer to a native view object.
    private int mNativeClass;
    // The BrowserFrame is an interface to the native Frame component.
    private BrowserFrame mBrowserFrame;


    /*  This is a ring of pictures for content. After B is built, it is swapped
        with A.
    */
    private Picture mContentPictureA = new Picture();   // draw()
    private Picture mContentPictureB = new Picture();   // nativeDraw()

    /*
     * range is from 200 to 10,000. 0 is a special value means device-width. -1
     * means undefined.
     */
    private int mViewportWidth = -1;

    /*
     * range is from 200 to 10,000. 0 is a special value means device-height. -1
     * means undefined.
     */
    private int mViewportHeight = -1;

    /*
     * scale in percent, range is from 1 to 1000. 0 means undefined.
     */
    private int mViewportInitialScale = 0;

    /*
     * scale in percent, range is from 1 to 1000. 0 means undefined.
     */
    private int mViewportMinimumScale = 0;

    /*
     * scale in percent, range is from 1 to 1000. 0 means undefined.
     */
    private int mViewportMaximumScale = 0;

    private boolean mViewportUserScalable = true;
    
    private int mRestoredScale = 100;
    private int mRestoredX = 0;
    private int mRestoredY = 0;

    private int mWebkitScrollX = 0;
    private int mWebkitScrollY = 0;

    // The thread name used to identify the WebCore thread and for use in
    // debugging other classes that require operation within the WebCore thread.
    /* package */ static final String THREAD_NAME = "WebViewCoreThread";

    public WebViewCore(Context context, WebView w, CallbackProxy proxy) {
        // No need to assign this in the WebCore thread.
        mCallbackProxy = proxy;
        mWebView = w;
        // This context object is used to initialize the WebViewCore during
        // subwindow creation.
        mContext = context;

        // We need to wait for the initial thread creation before sending
        // a message to the WebCore thread.
        // XXX: This is the only time the UI thread will wait for the WebCore
        // thread!
        synchronized (WebViewCore.class) {
            if (sWebCoreHandler == null) {
                // Create a global thread and start it.
                Thread t = new Thread(new WebCoreThread());
                t.setName(THREAD_NAME);
                t.start();
                try {
                    WebViewCore.class.wait();
                } catch (InterruptedException e) {
                    Log.e(LOGTAG, "Caught exception while waiting for thread " +
                           "creation.");
                    Log.e(LOGTAG, Log.getStackTraceString(e));
                }
            }
        }
        // Create an EventHub to handle messages before and after the thread is
        // ready.
        mEventHub = new EventHub();
        // Create a WebSettings object for maintaining all settings
        mSettings = new WebSettings(mContext);
        // The WebIconDatabase needs to be initialized within the UI thread so
        // just request the instance here.
        WebIconDatabase.getInstance();
        // Send a message to initialize the WebViewCore.
        Message init = sWebCoreHandler.obtainMessage(
                WebCoreThread.INITIALIZE, this);
        sWebCoreHandler.sendMessage(init);
    }

    /* Initialize private data within the WebCore thread.
     */
    private void initialize() {
        /* Initialize our private BrowserFrame class to handle all
         * frame-related functions. We need to create a new view which
         * in turn creates a C level FrameView and attaches it to the frame.
         */
        mBrowserFrame = new BrowserFrame(mContext, this, mCallbackProxy,
                mSettings);
        // Sync the native settings and also create the WebCore thread handler.
        mSettings.syncSettingsAndCreateHandler(mBrowserFrame);
        // Create the handler and transfer messages for the IconDatabase
        WebIconDatabase.getInstance().createHandler();
        // The transferMessages call will transfer all pending messages to the
        // WebCore thread handler.
        mEventHub.transferMessages();

        // Send a message back to WebView to tell it that we have set up the
        // WebCore thread.
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.WEBCORE_INITIALIZED_MSG_ID,
                    mNativeClass, 0).sendToTarget();
        }

    }

    /* Handle the initialization of WebViewCore during subwindow creation. This
     * method is called from the WebCore thread but it is called before the
     * INITIALIZE message can be handled.
     */
    /* package */ void initializeSubwindow() {
        // Go ahead and initialize the core components.
        initialize();
        // Remove the INITIALIZE method so we don't try to initialize twice.
        sWebCoreHandler.removeMessages(WebCoreThread.INITIALIZE, this);
    }

    /* Get the BrowserFrame component. This is used for subwindow creation. */
    /* package */ BrowserFrame getBrowserFrame() {
        return mBrowserFrame;
    }

    //-------------------------------------------------------------------------
    // Common methods
    //-------------------------------------------------------------------------

    /**
     * Causes all timers to pause. This applies to all WebViews in the current
     * app process.
     */
    public static void pauseTimers() {
        if (BrowserFrame.sJavaBridge == null) {
            throw new IllegalStateException(
                    "No WebView has been created in this process!");
        }
        BrowserFrame.sJavaBridge.pause();
    }

    /**
     * Resume all timers. This applies to all WebViews in the current process.
     */
    public static void resumeTimers() {
        if (BrowserFrame.sJavaBridge == null) {
            throw new IllegalStateException(
                    "No WebView has been created in this process!");
        }
        BrowserFrame.sJavaBridge.resume();
    }

    public WebSettings getSettings() {
        return mSettings;
    }

    /**
     * Invoke a javascript alert.
     * @param message The message displayed in the alert.
     */
    protected void jsAlert(String url, String message) {
        mCallbackProxy.onJsAlert(url, message);
    }

    /**
     * Invoke a javascript confirm dialog.
     * @param message The message displayed in the dialog.
     * @return True if the user confirmed or false if the user cancelled.
     */
    protected boolean jsConfirm(String url, String message) {
        return mCallbackProxy.onJsConfirm(url, message);
    }

    /**
     * Invoke a javascript prompt dialog.
     * @param message The message to be displayed in the dialog.
     * @param defaultValue The default value in the prompt input.
     * @return The input from the user or null to indicate the user cancelled
     *         the dialog.
     */
    protected String jsPrompt(String url, String message, String defaultValue) {
        return mCallbackProxy.onJsPrompt(url, message, defaultValue);
    }

    /**
     * Invoke a javascript before unload dialog.
     * @param url The url that is requesting the dialog.
     * @param message The message displayed in the dialog.
     * @return True if the user confirmed or false if the user cancelled. False
     *         will cancel the navigation.
     */
    protected boolean jsUnload(String url, String message) {
        return mCallbackProxy.onJsBeforeUnload(url, message);
    }

    //-------------------------------------------------------------------------
    // JNI methods
    //-------------------------------------------------------------------------

    static native String nativeFindAddress(String addr);

    /**
     * Find and highlight an occurance of text matching find
     * @param find The text to find.
     * @param forward If true, search forward.  Else, search backwards.
     * @param fromSelection Whether to start from the current selection or from
     *                      the beginning of the viewable page.
     * @return boolean Whether the text was found.
     */
    private native boolean nativeFind(String find,
                                      boolean forward,
                                      boolean fromSelection);

    /**
     * Find all occurances of text matching find and highlight them.
     * @param find The text to find.
     * @return int The number of occurances of find found.
     */
    private native int nativeFindAll(String find);
    
    /**
     * Clear highlights on text created by nativeFindAll.
     */
    private native void nativeClearMatches();

    private native void nativeDraw(Picture content);

    private native boolean nativeKeyUp(int keycode, int keyvalue);

    private native void nativeSendListBoxChoices(boolean[] choices, int size);

    private native void nativeSendListBoxChoice(int choice);

    /*  Tell webkit what its width and height are, for the purposes
        of layout/line-breaking. These coordinates are in document space,
        which is the same as View coords unless we have zoomed the document
        (see nativeSetZoom).
        screenWidth is used by layout to wrap column around. If viewport uses
        fixed size, screenWidth can be different from width with zooming.
        should this be called nativeSetViewPortSize?
    */
    private native void nativeSetSize(int width, int height, int screenWidth,
            float scale);

    private native int nativeGetContentMinPrefWidth();
    
    // Start: functions that deal with text editing
    private native void nativeReplaceTextfieldText(int frame, int node, int x, 
            int y, int oldStart, int oldEnd, String replace, int newStart, 
            int newEnd);

    private native void passToJs(int frame, int node, int x, int y, int gen,
            String currentText, int keyCode, int keyValue, boolean down,
            boolean cap, boolean fn, boolean sym);

    private native void nativeSaveDocumentState(int frame, int node, int x,
            int y);

    private native void nativeSetFinalFocus(int framePtr, int nodePtr, int x,
            int y, boolean block);

    private native void nativeSetKitFocus(int moveGeneration,
            int buildGeneration, int framePtr, int nodePtr, int x, int y,
            boolean ignoreNullFocus);

    private native String nativeRetrieveHref(int framePtr, int nodePtr);
    
    /**
     *  Return the url of the image located at (x,y) in content coordinates, or
     *  null if there is no image at that point.
     *
     *  @param x    x content ordinate
     *  @param y    y content ordinate
     *  @return String  url of the image located at (x,y), or null if there is
     *                  no image there.
     */
    private native String nativeRetrieveImageRef(int x, int y);

    private native void nativeTouchUp(int touchGeneration, 
            int buildGeneration, int framePtr, int nodePtr, int x, int y, 
            int size, boolean isClick, boolean retry);

    private native void nativeUnblockFocus();
    
    private native void nativeUpdateFrameCache();
    
    private native void nativeSetSnapAnchor(int x, int y);
    
    private native void nativeSnapToAnchor();
    
    private native void nativeSetBackgroundColor(int color);
    
    private native void nativeDump();

    private native void nativeRefreshPlugins(boolean reloadOpenPages);
    
    /**
     *  Delete text from start to end in the focused textfield. If there is no
     *  focus, or if start == end, silently fail.  If start and end are out of 
     *  order, swap them.
     *  @param  start   Beginning of selection to delete.
     *  @param  end     End of selection to delete.
     */
    private native void nativeDeleteSelection(int frame, int node, int x, int y,
        int start, int end);

    /**
     *  Set the selection to (start, end) in the focused textfield. If start and
     *  end are out of order, swap them.
     *  @param  start   Beginning of selection.
     *  @param  end     End of selection.
     */
    private native void nativeSetSelection(int frame, int node, int x, int y,
        int start, int end);

    private native String nativeGetSelection(Region sel);
    
    // EventHub for processing messages
    private final EventHub mEventHub;
    // WebCore thread handler
    private static Handler sWebCoreHandler;
    // Class for providing Handler creation inside the WebCore thread.
    private static class WebCoreThread implements Runnable {
        // Message id for initializing a new WebViewCore.
        private static final int INITIALIZE = 0;
        private static final int REDUCE_PRIORITY = 1;
        private static final int RESUME_PRIORITY = 2;
        private static final int CACHE_TICKER = 3;
        private static final int BLOCK_CACHE_TICKER = 4;
        private static final int RESUME_CACHE_TICKER = 5;

        private static final int CACHE_TICKER_INTERVAL = 60 * 1000; // 1 minute

        private static boolean mCacheTickersBlocked = true;

        public void run() {
            Looper.prepare();
            Assert.assertNull(sWebCoreHandler);
            synchronized (WebViewCore.class) {
                sWebCoreHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case INITIALIZE:
                                WebViewCore core = (WebViewCore) msg.obj;
                                synchronized (core) {
                                    core.initialize();
                                    core.notify();
                                }
                                break;

                            case REDUCE_PRIORITY:
                                // 3 is an adjustable number.
                                Process.setThreadPriority(
                                        Process.THREAD_PRIORITY_DEFAULT + 3 *
                                        Process.THREAD_PRIORITY_LESS_FAVORABLE);
                                break;

                            case RESUME_PRIORITY:
                                Process.setThreadPriority(
                                        Process.THREAD_PRIORITY_DEFAULT);
                                break;

                            case CACHE_TICKER:
                                if (!mCacheTickersBlocked) {
                                    CacheManager.endCacheTransaction();
                                    CacheManager.startCacheTransaction();
                                    sendMessageDelayed(
                                            obtainMessage(CACHE_TICKER), 
                                            CACHE_TICKER_INTERVAL);
                                }
                                break;

                            case BLOCK_CACHE_TICKER:
                                if (CacheManager.endCacheTransaction()) {
                                    mCacheTickersBlocked = true;
                                }
                                break;

                            case RESUME_CACHE_TICKER:
                                if (CacheManager.startCacheTransaction()) {
                                    mCacheTickersBlocked = false;
                                }
                                break;
                        }
                    }
                };
                WebViewCore.class.notify();
            }
            Looper.loop();
        }
    }

    static class FocusData {
        FocusData() {}
        FocusData(FocusData d) {
            mMoveGeneration = d.mMoveGeneration;
            mBuildGeneration = d.mBuildGeneration;
            mFrame = d.mFrame;
            mNode = d.mNode;
            mX = d.mX;
            mY = d.mY;
            mIgnoreNullFocus = d.mIgnoreNullFocus;
        }
        int mMoveGeneration;
        int mBuildGeneration;
        int mFrame;
        int mNode;
        int mX;
        int mY;
        boolean mIgnoreNullFocus;
    }

    static class TouchUpData {
        int mMoveGeneration;
        int mBuildGeneration;
        int mFrame;
        int mNode;
        int mX;
        int mY;
        int mSize;
        boolean mIsClick;
        boolean mRetry;
    }

    class EventHub {
        // Message Ids
        static final int LOAD_URL = 100;
        static final int STOP_LOADING = 101;
        static final int RELOAD = 102;
        static final int KEY_DOWN = 103;
        static final int KEY_UP = 104;
        static final int VIEW_SIZE_CHANGED = 105;
        static final int GO_BACK_FORWARD = 106;
        static final int SET_VISIBLE_RECT = 107;
        static final int RESTORE_STATE = 108;
        static final int PAUSE_TIMERS = 109;
        static final int RESUME_TIMERS = 110;
        static final int CLEAR_CACHE = 111;
        static final int CLEAR_HISTORY = 112;
        static final int SET_SELECTION = 113;
        static final int REPLACE_TEXT = 114;
        static final int PASS_TO_JS = 115;
        static final int FIND = 116;
        static final int UPDATE_CACHE_AND_TEXT_ENTRY = 117;
        static final int FIND_ALL = 118;
        static final int CLEAR_MATCHES = 119;
        static final int DOC_HAS_IMAGES = 120;
        static final int SET_SNAP_ANCHOR = 121;
        static final int DELETE_SELECTION = 122;
        static final int LISTBOX_CHOICES = 123;
        static final int SINGLE_LISTBOX_CHOICE = 124;
        static final int DUMP_WEBKIT = 125;
        static final int SET_BACKGROUND_COLOR = 126;
        static final int UNBLOCK_FOCUS = 127;
        static final int SAVE_DOCUMENT_STATE = 128;
        static final int GET_SELECTION = 129;
        static final int WEBKIT_DRAW = 130;
        static final int SYNC_SCROLL = 131;
        static final int REFRESH_PLUGINS = 132;

        // UI nav messages
        static final int REQUEST_IMAGE_HREF = 134;
        static final int SET_FINAL_FOCUS = 135;
        static final int SET_KIT_FOCUS = 136;
        static final int REQUEST_FOCUS_HREF = 137;
        static final int ADD_JS_INTERFACE = 138;
        static final int LOAD_DATA = 139;

        // motion
        static final int TOUCH_UP = 140;

        // Network-based messaging
        static final int CLEAR_SSL_PREF_TABLE = 150;

        // Test harness messages
        static final int REQUEST_EXT_REPRESENTATION = 160;
        static final int REQUEST_DOC_AS_TEXT = 161;
        // private message ids
        private static final int DESTROY =     200;
        
        // flag values passed to message SET_FINAL_FOCUS
        static final int NO_FOCUS_CHANGE_BLOCK = 0;
        static final int BLOCK_FOCUS_CHANGE_UNTIL_KEY_UP = 1;

        // Private handler for WebCore messages.
        private Handler mHandler;
        // Message queue for containing messages before the WebCore thread is
        // ready.
        private ArrayList<Message> mMessages = new ArrayList<Message>();
        // Flag for blocking messages. This is used during DESTROY to avoid
        // posting more messages to the EventHub or to WebView's event handler.
        private boolean mBlockMessages;

        private int mTid;
        private int mSavedPriority;

        /**
         * Prevent other classes from creating an EventHub.
         */
        private EventHub() {}

        /**
         * Transfer all messages to the newly created webcore thread handler.
         */
        private void transferMessages() {
            mTid = Process.myTid();
            mSavedPriority = Process.getThreadPriority(mTid);

            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case WEBKIT_DRAW:
                            webkitDraw();
                            break;

                        case DESTROY:
                            // Time to take down the world. Cancel all pending
                            // loads and destroy the native view and frame.
                            mBrowserFrame.destroy();
                            mBrowserFrame = null;
                            mNativeClass = 0;
                            break;

                        case LOAD_URL:
                            loadUrl((String) msg.obj);
                            break;

                        case LOAD_DATA:
                            HashMap loadParams = (HashMap) msg.obj;
                            mBrowserFrame.loadData(
                                    (String) loadParams.get("baseUrl"),
                                    (String) loadParams.get("data"),
                                    (String) loadParams.get("mimeType"),
                                    (String) loadParams.get("encoding"),
                                    (String) loadParams.get("failUrl"));
                            break;

                        case STOP_LOADING:
                            // If the WebCore has committed the load, but not 
                            // finished the first layout yet, we need to set 
                            // first layout done to trigger the interpreted side sync 
                            // up with native side
                            if (mBrowserFrame.committed()
                                    && !mBrowserFrame.firstLayoutDone()) {
                                mBrowserFrame.didFirstLayout(mBrowserFrame
                                        .currentUrl());
                            }
                            // Do this after syncing up the layout state.
                            stopLoading();
                            break;

                        case RELOAD:
                            mBrowserFrame.reload(false);
                            break;

                        case KEY_DOWN:
                            keyDown(msg.arg1, (KeyEvent) msg.obj);
                            break;

                        case KEY_UP:
                            keyUp(msg.arg1, (KeyEvent) msg.obj);
                            break;

                        case VIEW_SIZE_CHANGED:
                            viewSizeChanged(msg.arg1, msg.arg2,
                                    ((Float) msg.obj).floatValue());
                            break;

                        case SET_VISIBLE_RECT:
                            Rect r = (Rect) msg.obj;
                            // note: these are in document coordinates
                            // (inv-zoom)
                            nativeSetVisibleRect(r.left, r.top, r.width(),
                                    r.height());
                            break;

                        case GO_BACK_FORWARD:
                            // If it is a standard load and the load is not
                            // committed yet, we interpret BACK as RELOAD
                            if (!mBrowserFrame.committed() && msg.arg1 == -1 &&
                                    (mBrowserFrame.loadType() == 
                                    BrowserFrame.FRAME_LOADTYPE_STANDARD)) {
                                mBrowserFrame.reload(true);
                            } else {
                                mBrowserFrame.goBackOrForward(msg.arg1);
                            }
                            break;

                        case RESTORE_STATE:
                            stopLoading();
                            restoreState(msg.arg1);
                            break;

                        case PAUSE_TIMERS:
                            mSavedPriority = Process.getThreadPriority(mTid);
                            Process.setThreadPriority(mTid,
                                    Process.THREAD_PRIORITY_BACKGROUND);
                            pauseTimers();
                            if (CacheManager.disableTransaction()) {
                                WebCoreThread.mCacheTickersBlocked = true;
                                sWebCoreHandler.removeMessages(
                                        WebCoreThread.CACHE_TICKER);
                            }
                            break;

                        case RESUME_TIMERS:
                            Process.setThreadPriority(mTid, mSavedPriority);
                            resumeTimers();
                            if (CacheManager.enableTransaction()) {
                                WebCoreThread.mCacheTickersBlocked = false;
                                sWebCoreHandler.sendMessageDelayed(
                                        sWebCoreHandler.obtainMessage(
                                        WebCoreThread.CACHE_TICKER),
                                        WebCoreThread.CACHE_TICKER_INTERVAL);
                            }
                            break;

                        case CLEAR_CACHE:
                            mBrowserFrame.clearCache();
                            if (msg.arg1 == 1) {
                                CacheManager.removeAllCacheFiles();
                            }
                            break;

                        case CLEAR_HISTORY:
                            mCallbackProxy.getBackForwardList().
                                    close(mBrowserFrame.mNativeFrame);
                            break;

                        case REPLACE_TEXT: 
                            HashMap jMap = (HashMap) msg.obj;
                            FocusData fData = (FocusData) jMap.get("focusData");
                            String replace = (String) jMap.get("replace");
                            int newStart = 
                                    ((Integer) jMap.get("start")).intValue();
                            int newEnd = 
                                    ((Integer) jMap.get("end")).intValue();
                            nativeReplaceTextfieldText(fData.mFrame,
                                    fData.mNode, fData.mX, fData.mY, msg.arg1,
                                    msg.arg2, replace, newStart, newEnd);
                            break;

                        case PASS_TO_JS: {
                            HashMap jsMap = (HashMap) msg.obj;
                            FocusData fDat = (FocusData) jsMap.get("focusData");
                            KeyEvent evt = (KeyEvent) jsMap.get("event");
                            int keyCode = evt.getKeyCode();
                            int keyValue = evt.getUnicodeChar();
                            int generation = msg.arg1;
                            passToJs(fDat.mFrame, fDat.mNode, fDat.mX, fDat.mY,
                                    generation,
                                    (String) jsMap.get("currentText"),
                                    keyCode,
                                    keyValue,
                                    evt.isDown(),
                                    evt.isShiftPressed(), evt.isAltPressed(),
                                    evt.isSymPressed());
                            break;
                        }

                        case SAVE_DOCUMENT_STATE: {
                            FocusData fDat = (FocusData) msg.obj;
                            nativeSaveDocumentState(fDat.mFrame, fDat.mNode,
                                    fDat.mX, fDat.mY);
                            break;
                        }

                        case FIND:
                            /* arg1:
                             *   1 - Find next
                             *  -1 - Find previous
                             *   0 - Find first
                             */
                            Message response = (Message) msg.obj;
                            boolean find = nativeFind(msg.getData().getString("find"),
                                    msg.arg1 != -1, msg.arg1 != 0);
                            response.arg1 = find ? 1 : 0;
                            response.sendToTarget();
                            break;
                            
                        case FIND_ALL:
                            int found = nativeFindAll(msg.getData().getString("find"));
                            Message resAll = (Message) msg.obj;
                            resAll.arg1 = found;
                            resAll.sendToTarget();
                            break;
                            
                        case CLEAR_MATCHES:
                            nativeClearMatches();
                            break;

                        case CLEAR_SSL_PREF_TABLE:
                            Network.getInstance(mContext)
                                    .clearUserSslPrefTable();
                            break;

                        case TOUCH_UP:
                            TouchUpData touchUpData = (TouchUpData) msg.obj;
                            nativeTouchUp(touchUpData.mMoveGeneration,
                                    touchUpData.mBuildGeneration,
                                    touchUpData.mFrame, touchUpData.mNode,
                                    touchUpData.mX, touchUpData.mY, 
                                    touchUpData.mSize, touchUpData.mIsClick,
                                    touchUpData.mRetry);
                            break;

                        case ADD_JS_INTERFACE:
                            HashMap map = (HashMap) msg.obj;
                            Object obj = map.get("object");
                            String interfaceName = (String)
                                    map.get("interfaceName");
                            mBrowserFrame.addJavascriptInterface(obj,
                                    interfaceName);
                            break;

                        case REQUEST_EXT_REPRESENTATION:
                            mBrowserFrame.externalRepresentation(
                                    (Message) msg.obj);
                            break;

                        case REQUEST_DOC_AS_TEXT:
                            mBrowserFrame.documentAsText((Message) msg.obj);
                            break;

                        case SET_FINAL_FOCUS:
                            FocusData finalData = (FocusData) msg.obj;
                            nativeSetFinalFocus(finalData.mFrame,
                                     finalData.mNode, finalData.mX, 
                                     finalData.mY, msg.arg1 
                                     != EventHub.NO_FOCUS_CHANGE_BLOCK);
                            break;

                        case UNBLOCK_FOCUS:
                            nativeUnblockFocus();
                            break;

                        case SET_KIT_FOCUS:
                            FocusData focusData = (FocusData) msg.obj;
                            nativeSetKitFocus(focusData.mMoveGeneration,
                                    focusData.mBuildGeneration,
                                    focusData.mFrame, focusData.mNode,
                                    focusData.mX, focusData.mY,
                                    focusData.mIgnoreNullFocus);
                            break;

                        case REQUEST_FOCUS_HREF: {
                            Message hrefMsg = (Message) msg.obj;
                            String res = nativeRetrieveHref(msg.arg1, msg.arg2);
                            Bundle data = hrefMsg.getData();
                            data.putString("url", res);
                            hrefMsg.setData(data);
                            hrefMsg.sendToTarget();
                            break;
                        }
                            
                        case REQUEST_IMAGE_HREF: {
                            Message refMsg = (Message) msg.obj;
                            String ref = 
                                    nativeRetrieveImageRef(msg.arg1, msg.arg2);
                            Bundle data = refMsg.getData();
                            data.putString("url", ref);
                            refMsg.setData(data);
                            refMsg.sendToTarget();
                            break;
                        }

                        case UPDATE_CACHE_AND_TEXT_ENTRY:
                            nativeUpdateFrameCache();
                            sendViewInvalidate();
                            sendUpdateTextEntry();
                            break;

                        case DOC_HAS_IMAGES:
                            Message imageResult = (Message) msg.obj;
                            imageResult.arg1 =
                                    mBrowserFrame.documentHasImages() ? 1 : 0;
                            imageResult.sendToTarget();
                            break;

                        case SET_SNAP_ANCHOR:
                            nativeSetSnapAnchor(msg.arg1, msg.arg2);
                            break;
                            
                        case DELETE_SELECTION:
                            FocusData delData = (FocusData) msg.obj;
                            nativeDeleteSelection(delData.mFrame,
                                     delData.mNode, delData.mX, 
                                     delData.mY, msg.arg1, msg.arg2);
                            break;

                        case SET_SELECTION:
                            FocusData selData = (FocusData) msg.obj;
                            nativeSetSelection(selData.mFrame,
                                     selData.mNode, selData.mX, 
                                     selData.mY, msg.arg1, msg.arg2);
                            break;
                            
                        case LISTBOX_CHOICES:
                            SparseBooleanArray choices = (SparseBooleanArray)
                                    msg.obj;
                            int choicesSize = msg.arg1;
                            boolean[] choicesArray = new boolean[choicesSize];
                            for (int c = 0; c < choicesSize; c++) {
                                choicesArray[c] = choices.get(c);
                            }
                            nativeSendListBoxChoices(choicesArray, 
                                    choicesSize);
                            break;

                        case SINGLE_LISTBOX_CHOICE:
                            nativeSendListBoxChoice(msg.arg1);
                            break;
                            
                        case SET_BACKGROUND_COLOR:
                            nativeSetBackgroundColor(msg.arg1);
                            break;
                            
                        case GET_SELECTION:
                            String str = nativeGetSelection((Region) msg.obj);
                            Message.obtain(mWebView.mPrivateHandler
                                    , WebView.UPDATE_CLIPBOARD, str)
                                    .sendToTarget();
                            break;
                            
                        case DUMP_WEBKIT:
                            nativeDump();
                            break;

                        case SYNC_SCROLL:
                            mWebkitScrollX = msg.arg1;
                            mWebkitScrollY = msg.arg2;
                            break;

                        case REFRESH_PLUGINS:
                            nativeRefreshPlugins(msg.arg1 != 0);
                            break;
                    }
                }
            };
            // Take all queued messages and resend them to the new handler.
            synchronized (this) {
                int size = mMessages.size();
                for (int i = 0; i < size; i++) {
                    mHandler.sendMessage(mMessages.get(i));
                }
                mMessages = null;
            }
        }

        /**
         * Send a message internally to the queue or to the handler
         */
        private synchronized void sendMessage(Message msg) {
            if (mBlockMessages) {
                return;
            }
            if (mMessages != null) {
                mMessages.add(msg);
            } else {
                mHandler.sendMessage(msg);
            }
        }

        private synchronized void removeMessages(int what) {
            if (mBlockMessages) {
                return;
            }
            if (what == EventHub.WEBKIT_DRAW) {
                mDrawIsScheduled = false;
            }
            if (mMessages != null) {
                Log.w(LOGTAG, "Not supported in this case.");
            } else {
                mHandler.removeMessages(what);
            }
        }

        private synchronized void sendMessageDelayed(Message msg, long delay) {
            if (mBlockMessages) {
                return;
            }
            mHandler.sendMessageDelayed(msg, delay);
        }

        /**
         * Send a message internally to the front of the queue.
         */
        private synchronized void sendMessageAtFrontOfQueue(Message msg) {
            if (mBlockMessages) {
                return;
            }
            if (mMessages != null) {
                mMessages.add(0, msg);
            } else {
                mHandler.sendMessageAtFrontOfQueue(msg);
            }
        }

        /**
         * Remove all the messages.
         */
        private synchronized void removeMessages() {
            // reset mDrawIsScheduled flag as WEBKIT_DRAW may be removed
            mDrawIsScheduled = false;
            if (mMessages != null) {
                mMessages.clear();
            } else {
                mHandler.removeCallbacksAndMessages(null);
            }
        }

        /**
         * Block sending messages to the EventHub.
         */
        private synchronized void blockMessages() {
            mBlockMessages = true;
        }
    }

    //-------------------------------------------------------------------------
    // Methods called by host activity (in the same thread)
    //-------------------------------------------------------------------------

    public void stopLoading() {
        if (LOGV_ENABLED) Log.v(LOGTAG, "CORE stopLoading");
        if (mBrowserFrame != null) {
            mBrowserFrame.stopLoading();
        }
    }

    //-------------------------------------------------------------------------
    // Methods called by WebView
    // If it refers to local variable, it needs synchronized().
    // If it needs WebCore, it has to send message.
    //-------------------------------------------------------------------------

    void sendMessage(Message msg) {
        mEventHub.sendMessage(msg);
    }

    void sendMessage(int what) {
        mEventHub.sendMessage(Message.obtain(null, what));
    }

    void sendMessage(int what, Object obj) {
        mEventHub.sendMessage(Message.obtain(null, what, obj));
    }

    void sendMessage(int what, int arg1) {
        // just ignore the second argument (make it 0)
        mEventHub.sendMessage(Message.obtain(null, what, arg1, 0));
    }

    void sendMessage(int what, int arg1, int arg2) {
        mEventHub.sendMessage(Message.obtain(null, what, arg1, arg2));
    }

    void sendMessage(int what, int arg1, Object obj) {
        // just ignore the second argument (make it 0)
        mEventHub.sendMessage(Message.obtain(null, what, arg1, 0, obj));
    }

    void sendMessage(int what, int arg1, int arg2, Object obj) {
        mEventHub.sendMessage(Message.obtain(null, what, arg1, arg2, obj));
    }

    void sendMessageDelayed(int what, Object obj, long delay) {
        mEventHub.sendMessageDelayed(Message.obtain(null, what, obj), delay);
    }

    void removeMessages(int what) {
        mEventHub.removeMessages(what);
    }

    void removeMessages() {
        mEventHub.removeMessages();
    }

    /**
     * Removes pending messages and trigger a DESTROY message to send to
     * WebCore.
     * Called from UI thread.
     */
    void destroy() {
        // We don't want anyone to post a message between removing pending
        // messages and sending the destroy message.
        synchronized (mEventHub) {
            mEventHub.removeMessages();
            mEventHub.sendMessageAtFrontOfQueue(
                    Message.obtain(null, EventHub.DESTROY));
            mEventHub.blockMessages();
            mWebView = null;
        }
    }

    //-------------------------------------------------------------------------
    // WebViewCore private methods
    //-------------------------------------------------------------------------

    private void loadUrl(String url) {
        if (LOGV_ENABLED) Log.v(LOGTAG, " CORE loadUrl " + url);
        mBrowserFrame.loadUrl(url);
    }

    private void keyDown(int code, KeyEvent event) {
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "CORE keyDown at " + System.currentTimeMillis()
                    + ", " + event);
        }
        mCallbackProxy.onUnhandledKeyEvent(event);
    }

    private void keyUp(int code, KeyEvent event) {
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "CORE keyUp at " + System.currentTimeMillis()
                    + ", " + event);
        }
        if (!nativeKeyUp(code, event.getUnicodeChar())) {
            mCallbackProxy.onUnhandledKeyEvent(event);
        }
    }

    // These values are used to avoid requesting a layout based on old values
    private int mCurrentViewWidth = 0;
    private int mCurrentViewHeight = 0;

    // notify webkit that our virtual view size changed size (after inv-zoom)
    private void viewSizeChanged(int w, int h, float scale) {
        if (LOGV_ENABLED) Log.v(LOGTAG, "CORE onSizeChanged");
        if (mSettings.getUseWideViewPort()
                && (w < mViewportWidth || mViewportWidth == -1)) {
            int width = mViewportWidth;
            if (mViewportWidth == -1) {
                if (mSettings.getLayoutAlgorithm() == 
                        WebSettings.LayoutAlgorithm.NORMAL) {
                    width = WebView.ZOOM_OUT_WIDTH;
                } else {
                    /*
                     * if a page's minimum preferred width is wider than the
                     * given "w", use it instead to get better layout result. If
                     * we start a page with MAX_ZOOM_WIDTH, "w" will be always
                     * wider. If we start a page with screen width, due to the
                     * delay between {@link #didFirstLayout} and
                     * {@link #viewSizeChanged},
                     * {@link #nativeGetContentMinPrefWidth} will return a more
                     * accurate value than initial 0 to result a better layout.
                     * In the worse case, the native width will be adjusted when
                     * next zoom or screen orientation change happens.
                     */
                    width = Math.max(w, nativeGetContentMinPrefWidth());
                }
            }
            nativeSetSize(width, Math.round((float) width * h / w), w, scale);
        } else {
            nativeSetSize(w, h, w, scale);
        }
        // Remember the current width and height
        boolean needInvalidate = (mCurrentViewWidth == 0);
        mCurrentViewWidth = w;
        mCurrentViewHeight = h;
        if (needInvalidate) {
            // ensure {@link #webkitDraw} is called as we were blocking in
            // {@link #contentInvalidate} when mCurrentViewWidth is 0
            contentInvalidate();
        }
        mEventHub.sendMessage(Message.obtain(null,
                EventHub.UPDATE_CACHE_AND_TEXT_ENTRY));
    }

    private void sendUpdateTextEntry() {
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.UPDATE_TEXT_ENTRY_MSG_ID).sendToTarget();
        }
    }

    // Used to avoid posting more than one draw message.
    private boolean mDrawIsScheduled;

    // Used to end scale+scroll mode, accessed by both threads
    boolean mEndScaleZoom = false;
    
    private void webkitDraw() {
        mDrawIsScheduled = false;
        nativeDraw(mContentPictureB);
        int w;
        int h;
        synchronized (this) {
            Picture temp = mContentPictureB;
            mContentPictureB = mContentPictureA;
            mContentPictureA = temp;
            w = mContentPictureA.getWidth();
            h = mContentPictureA.getHeight();
        }

        if (mWebView != null) {
            // Send the native view size that was used during the most recent
            // layout.
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.NEW_PICTURE_MSG_ID, w, h,
                    new Point(mCurrentViewWidth, mCurrentViewHeight))
                    .sendToTarget();
            if (mWebkitScrollX != 0 || mWebkitScrollY != 0) {
                // as we have the new picture, try to sync the scroll position
                Message.obtain(mWebView.mPrivateHandler,
                        WebView.SYNC_SCROLL_TO_MSG_ID, mWebkitScrollX,
                        mWebkitScrollY).sendToTarget();
                mWebkitScrollX = mWebkitScrollY = 0;
            }
            // nativeSnapToAnchor() needs to be called after NEW_PICTURE_MSG_ID
            // is sent, so that scroll will be based on the new content size.
            nativeSnapToAnchor();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // These are called from the UI thread, not our thread

    static final int ZOOM_BITS = Paint.FILTER_BITMAP_FLAG |
                                         Paint.DITHER_FLAG |
                                         Paint.SUBPIXEL_TEXT_FLAG;
    static final int SCROLL_BITS = Paint.FILTER_BITMAP_FLAG |
                                           Paint.DITHER_FLAG;

    final DrawFilter mZoomFilter =
                    new PaintFlagsDrawFilter(ZOOM_BITS, Paint.LINEAR_TEXT_FLAG);
    final DrawFilter mScrollFilter =
                    new PaintFlagsDrawFilter(SCROLL_BITS, 0);

    /* package */ void drawContentPicture(Canvas canvas, int color,
                                          boolean animatingZoom,
                                          boolean animatingScroll) {
        DrawFilter df = null;
        if (animatingZoom) {
            df = mZoomFilter;
        } else if (animatingScroll) {
            df = mScrollFilter;
        }
        canvas.setDrawFilter(df);
        synchronized (this) {
            Picture picture = mContentPictureA;
            int sc = canvas.save(Canvas.CLIP_SAVE_FLAG);
            Rect clip = new Rect(0, 0, picture.getWidth(), picture.getHeight());
            canvas.clipRect(clip, Region.Op.DIFFERENCE);
            canvas.drawColor(color);
            canvas.restoreToCount(sc);
    // experiment commented out
    //      if (TEST_BUCKET) {
    //          nativeDrawContentPicture(canvas);
    //      } else {
                canvas.drawPicture(picture);
    //      }
        }
        canvas.setDrawFilter(null);
    }

    /* package */ void clearContentPicture() {
    // experiment commented out
    //   if (TEST_BUCKET) {
    //        nativeClearContentPicture();
    //    }
        synchronized (this) {
            mContentPictureA = new Picture();
        }
    }

    /*package*/ Picture copyContentPicture() {
        synchronized (this) {
            return new Picture(mContentPictureA);
        }
    }

    static void pauseUpdate(WebViewCore core) {
        // remove the pending REDUCE_PRIORITY and RESUME_PRIORITY messages
        sWebCoreHandler.removeMessages(WebCoreThread.REDUCE_PRIORITY);
        sWebCoreHandler.removeMessages(WebCoreThread.RESUME_PRIORITY);
        sWebCoreHandler.sendMessageAtFrontOfQueue(sWebCoreHandler
                .obtainMessage(WebCoreThread.REDUCE_PRIORITY));
        // Note: there is one possible failure mode. If pauseUpdate() is called
        // from UI thread while in webcore thread WEBKIT_DRAW is just pulled out 
        // of the queue and about to be executed. mDrawIsScheduled may be set to 
        // false in webkitDraw(). So update won't be blocked. But at least the 
        // webcore thread priority is still lowered.
        if (core != null) {
            synchronized (core) {
                core.mDrawIsScheduled = true;
                core.mEventHub.removeMessages(EventHub.WEBKIT_DRAW);
            }
        }
    }

    static void resumeUpdate(WebViewCore core) {
        // remove the pending REDUCE_PRIORITY and RESUME_PRIORITY messages
        sWebCoreHandler.removeMessages(WebCoreThread.REDUCE_PRIORITY);
        sWebCoreHandler.removeMessages(WebCoreThread.RESUME_PRIORITY);
        sWebCoreHandler.sendMessageAtFrontOfQueue(sWebCoreHandler
                .obtainMessage(WebCoreThread.RESUME_PRIORITY));
        if (core != null) {
            synchronized (core) {
                core.mDrawIsScheduled = false;
                core.contentInvalidate();
            }
        }
    }

    static void startCacheTransaction() {
        sWebCoreHandler.sendMessage(sWebCoreHandler
                .obtainMessage(WebCoreThread.RESUME_CACHE_TICKER));
    }

    static void endCacheTransaction() {
        sWebCoreHandler.sendMessage(sWebCoreHandler
                .obtainMessage(WebCoreThread.BLOCK_CACHE_TICKER));
    }

    //////////////////////////////////////////////////////////////////////////

    private void restoreState(int index) {
        WebBackForwardList list = mCallbackProxy.getBackForwardList();
        int size = list.getSize();
        for (int i = 0; i < size; i++) {
            list.getItemAtIndex(i).inflate(mBrowserFrame.mNativeFrame);
        }
        list.restoreIndex(mBrowserFrame.mNativeFrame, index);
    }

    //-------------------------------------------------------------------------
    // Implement abstract methods in WebViewCore, native WebKit callback part
    //-------------------------------------------------------------------------

    // called from JNI or WebView thread
    /* package */ void contentInvalidate() {
        // don't update the Picture until we have an initial width and finish
        // the first layout
        if (mCurrentViewWidth == 0 || !mBrowserFrame.firstLayoutDone()) {
            return;
        }
        // only fire an event if this is our first request
        synchronized (this) {
            if (mDrawIsScheduled) {
                return;
            }
            mDrawIsScheduled = true;
            mEventHub.sendMessage(Message.obtain(null, EventHub.WEBKIT_DRAW));
        }
    }

    // called by JNI
    private void contentScrollBy(int dx, int dy) {
        if (!mBrowserFrame.firstLayoutDone()) {
            // Will this happen? If yes, we need to do something here.
            return;
        }
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.SCROLL_BY_MSG_ID, dx, dy).sendToTarget();
        }
    }

    // called by JNI
    private void contentScrollTo(int x, int y) {
        if (!mBrowserFrame.firstLayoutDone()) {
            /*
             * WebKit restore state will be called before didFirstLayout(),
             * remember the position as it has to be applied after restoring
             * zoom factor which is controlled by screenWidth.
             */
            mRestoredX = x;
            mRestoredY = y;
            return;
        }
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.SCROLL_TO_MSG_ID, x, y).sendToTarget();
        }
    }

    // called by JNI
    private void contentSpawnScrollTo(int x, int y) {
        if (!mBrowserFrame.firstLayoutDone()) {
            /*
             * WebKit restore state will be called before didFirstLayout(),
             * remember the position as it has to be applied after restoring
             * zoom factor which is controlled by screenWidth.
             */
            mRestoredX = x;
            mRestoredY = y;
            return;
        }
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.SPAWN_SCROLL_TO_MSG_ID, x, y).sendToTarget();
        }
    }

    // called by JNI
    private void sendMarkNodeInvalid(int node) {
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.MARK_NODE_INVALID_ID, node, 0).sendToTarget();
        }
    }

    // called by JNI
    private void sendNotifyFocusSet() {
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.NOTIFY_FOCUS_SET_MSG_ID).sendToTarget();
        }
    }

    // called by JNI
    private void sendNotifyProgressFinished() {
        sendUpdateTextEntry();
        // as CacheManager can behave based on database transaction, we need to
        // call tick() to trigger endTransaction
        sWebCoreHandler.removeMessages(WebCoreThread.CACHE_TICKER);
        sWebCoreHandler.sendMessage(sWebCoreHandler
                .obtainMessage(WebCoreThread.CACHE_TICKER));
    }

    // called by JNI
    private void sendRecomputeFocus() {
        if (mWebView != null) {
            Message.obtain(mWebView.mPrivateHandler,
                    WebView.RECOMPUTE_FOCUS_MSG_ID).sendToTarget();
        }
    }

    // called by JNI
    private void sendViewInvalidate() {
        if (mWebView != null) {
            mWebView.postInvalidate();
        }
    }

    /* package */ WebView getWebView() {
        return mWebView;
    }

    private native void setViewportSettingsFromNative();
    
    // called by JNI
    private void didFirstLayout(String url) {
        // Trick to ensure that the Picture has the exact height for the content
        // by forcing to layout with 0 height after the page is ready, which is
        // indicated by didFirstLayout. This is essential to get rid of the 
        // white space in the GMail which uses WebView for message view.
        if (mWebView != null && mWebView.mHeightCanMeasure) {
            mWebView.mLastHeightSent = 0;
            // Send a negative scale to indicate that WebCore should reuse the
            // current scale
            mEventHub.sendMessage(Message.obtain(null,
                    EventHub.VIEW_SIZE_CHANGED, mWebView.mLastWidthSent,
                    mWebView.mLastHeightSent, -1.0f));
        }

        mBrowserFrame.didFirstLayout(url);

        // reset the scroll position as it is a new page now
        mWebkitScrollX = mWebkitScrollY = 0;

        // set the viewport settings from WebKit
        setViewportSettingsFromNative();

        // infer the values if they are not defined.
        if (mViewportWidth == 0) {
            if (mViewportInitialScale == 0) {
                mViewportInitialScale = 100;
            }
            if (mViewportMinimumScale == 0) {
                mViewportMinimumScale = 100;
            }
        }
        if (mViewportUserScalable == false) {
            mViewportInitialScale = 100;
            mViewportMinimumScale = 100;
            mViewportMaximumScale = 100;
        }
        if (mViewportMinimumScale > mViewportInitialScale) {
            if (mViewportInitialScale == 0) {
                mViewportInitialScale = mViewportMinimumScale;
            } else {
                mViewportMinimumScale = mViewportInitialScale;
            }
        }
        if (mViewportMaximumScale > 0) {
            if (mViewportMaximumScale < mViewportInitialScale) {
                mViewportMaximumScale = mViewportInitialScale;
            } else if (mViewportInitialScale == 0) {
                mViewportInitialScale = mViewportMaximumScale;
            }            
        }
        if (mViewportWidth < 0 && mViewportInitialScale == 100) {
            mViewportWidth = 0;
        }

        // now notify webview
        if (mWebView != null) {
            HashMap scaleLimit = new HashMap();
            scaleLimit.put("minScale", mViewportMinimumScale);
            scaleLimit.put("maxScale", mViewportMaximumScale);

            if (mRestoredScale > 0) {
                Message.obtain(mWebView.mPrivateHandler,
                        WebView.DID_FIRST_LAYOUT_MSG_ID, mRestoredScale, 0,
                        scaleLimit).sendToTarget();
                mRestoredScale = 0;
            } else {
                Message.obtain(mWebView.mPrivateHandler,
                        WebView.DID_FIRST_LAYOUT_MSG_ID, mViewportInitialScale,
                        mViewportWidth, scaleLimit).sendToTarget();
            }

            // if no restored offset, move the new page to (0, 0)
            Message.obtain(mWebView.mPrivateHandler, WebView.SCROLL_TO_MSG_ID,
                    mRestoredX, mRestoredY).sendToTarget();
            mRestoredX = mRestoredY = 0;

            // force an early draw for quick feedback after the first layout
            if (mCurrentViewWidth != 0) {
                synchronized (this) {
                    if (mDrawIsScheduled) {
                        mEventHub.removeMessages(EventHub.WEBKIT_DRAW);
                    }
                    mDrawIsScheduled = true;
                    mEventHub.sendMessageAtFrontOfQueue(Message.obtain(null,
                            EventHub.WEBKIT_DRAW));
                }
            }
        }
    }

    // called by JNI
    private void restoreScale(int scale) {
        if (mBrowserFrame.firstLayoutDone() == false) {
            mRestoredScale = scale;
        }
    }
    
    // called by JNI
    private void updateTextfield(int ptr, boolean changeToPassword,
            String text, int textGeneration) {
        if (mWebView != null) {
            Message msg = Message.obtain(mWebView.mPrivateHandler,
                    WebView.UPDATE_TEXTFIELD_TEXT_MSG_ID, ptr, 
                    textGeneration, text);
            msg.getData().putBoolean("password", changeToPassword);
            msg.sendToTarget();
        }
    }

    // these must be in document space (i.e. not scaled/zoomed.
    private native void nativeSetVisibleRect(int x, int y, int width,
            int height);

    // called by JNI
    private void requestListBox(String[] array, boolean[] enabledArray,
            int[] selectedArray) {
        if (mWebView != null) {
            mWebView.requestListBox(array, enabledArray, selectedArray);
        }
    }

    // called by JNI
    private void requestListBox(String[] array, boolean[] enabledArray,
            int selection) {
        if (mWebView != null) {
            mWebView.requestListBox(array, enabledArray, selection);
        }
        
    }
}
