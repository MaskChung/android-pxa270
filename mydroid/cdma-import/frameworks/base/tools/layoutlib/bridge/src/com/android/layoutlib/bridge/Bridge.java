/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.layoutlib.bridge;

import com.android.layoutlib.api.IProjectCallback;
import com.android.layoutlib.api.ILayoutBridge;
import com.android.layoutlib.api.ILayoutLog;
import com.android.layoutlib.api.ILayoutResult;
import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IStyleResourceValue;
import com.android.layoutlib.api.IXmlPullParser;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;
import com.android.layoutlib.bridge.LayoutResult.LayoutViewInfo;
import com.android.ninepatch.NinePatch;
import com.android.tools.layoutlib.create.OverrideMethod;
import com.android.tools.layoutlib.create.OverrideMethod.MethodListener;

import org.xmlpull.v1.XmlPullParser;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.android.internal.util.XmlUtils;

import android.util.TypedValue;
import android.view.BridgeInflater;
import android.view.IWindow;
import android.view.IWindowSession;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.AttachInfo;
import android.view.View.MeasureSpec;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point of the LayoutLib Bridge.
 * <p/>To use this bridge, simply instantiate an object of type {@link Bridge} and call
 * {@link #computeLayout(XmlPullParser, int, int, boolean, Map)}.
 */
public final class Bridge implements ILayoutBridge {
    
    private static final int DEFAULT_TITLE_BAR_HEIGHT = 25;
    private static final int DEFAULT_STATUS_BAR_HEIGHT = 25;

    private ILayoutLog mLogger;

    /**
     * Maps from id to resource name/type.
     */
    private final static Map<Integer, String[]> sRMap = new HashMap<Integer, String[]>();
    /**
     * Same as sRMap except for int[] instead of int resources.
     */
    private final static Map<int[], String> sRArrayMap = new HashMap<int[], String>();
    /**
     * Reverse map compared to sRMap, resource type -> (resource name -> id)
     */
    private final static Map<String, Map<String, Integer>> sRFullMap =
        new HashMap<String, Map<String,Integer>>();
    
    private final static Map<Object, Map<String, Bitmap>> sProjectBitmapCache =
        new HashMap<Object, Map<String, Bitmap>>();
    private final static Map<Object, Map<String, NinePatch>> sProject9PatchCache =
        new HashMap<Object, Map<String, NinePatch>>();

    private final static Map<String, Bitmap> sFrameworkBitmapCache = new HashMap<String, Bitmap>();
    private final static Map<String, NinePatch> sFramework9PatchCache =
        new HashMap<String, NinePatch>();
    
    private static Map<String, Map<String, Integer>> sEnumValueMap;
    
    private final static MethodListener sNullMethodListener = new MethodListener() {
        public void onInvoke(String signature, Object caller) {
            // pass
        }
    };

    private final static ILayoutLog sDefaultLogger = new ILayoutLog() {
        public void error(String message) {
            System.err.println(message);
        }

        public void error(Throwable t) {
            String message = t.getMessage();
            if (message == null) {
                message = t.getClass().getName();
            }

            System.err.println(message);
        }

        public void warning(String message) {
            System.out.println(message);
        }
    };

    private final static String[] IGNORED_STATIC_METHODS = new String[] {
        "android.content.res.AssetManager#init()V",
        "android.content.res.AssetManager#deleteTheme(I)V",
        "android.content.res.AssetManager#destroy()V",
        "android.graphics._Original_Paint#native_init()I",
        "android.graphics.Bitmap#nativeRecycle(I)V",
        "android.graphics.Bitmap#nativeDestructor(I)V",
        "android.view.animation.Transformation#<clinit>()V",
        "android.view.animation.Transformation#<init>()V",
        "android.view.animation.Transformation#clear()V",
    };

    /*
     * (non-Javadoc)
     * @see com.android.layoutlib.api.ILayoutLibBridge#init(java.lang.String, java.util.Map)
     */
    public boolean init(
            String fontOsLocation, Map<String, Map<String, Integer>> enumValueMap) {

        return sinit(fontOsLocation, enumValueMap);
    }
    
    private static synchronized boolean sinit(String fontOsLocation,
            Map<String, Map<String, Integer>> enumValueMap) {

        // set an empty method listener for some known static methods we don't care about.
        for (String method : IGNORED_STATIC_METHODS) {
            OverrideMethod.setMethodListener(method, sNullMethodListener);
        }

        
        // set a the default listener for the rest of the static methods. It prints out
        // missing stub methods but only if the environment variable DEBUG_LAYOUT is set.
        if (System.getenv("DEBUG_LAYOUT") != null) {
            OverrideMethod.setDefaultListener(new MethodListener() {
                public void onInvoke(String signature, Object caller) {
                    System.out.println("Missing Stub: " + signature);
                }
            });
        }

        // load the fonts.
        FontLoader fontLoader = FontLoader.create(fontOsLocation);
        if (fontLoader != null) {
            Typeface.init(fontLoader);
        } else {
            return false;
        }
        
        sEnumValueMap = enumValueMap;

        // now parse com.android.internal.R (and only this one as android.R is a subset of
        // the internal version), and put the content in the maps.
        try {
            // WARNING: this only works because the class is already loaded, and therefore
            // the objects returned by Field.get() are the same as the onea used by
            // the code accessing the R class.
            // int[] does not implement equals/hashCode, and if the parsing used a different class
            // loader for the R class, this would NOT work.
            Class<?> r = com.android.internal.R.class;
            
            for (Class<?> inner : r.getDeclaredClasses()) {
                String resType = inner.getSimpleName();

                Map<String, Integer> fullMap = new HashMap<String, Integer>();
                sRFullMap.put(resType, fullMap);
                
                for (Field f : inner.getDeclaredFields()) {
                    // only process static final fields. Since the final attribute may have
                    // been altered by layoutlib_create, we only check static
                    int modifiers = f.getModifiers();
                    if (Modifier.isStatic(modifiers)) {
                        Class<?> type = f.getType();
                        if (type.isArray() && type.getComponentType() == int.class) {
                            // if the object is an int[] we put it in sRArrayMap
                            sRArrayMap.put((int[]) f.get(null), f.getName());
                        } else if (type == int.class) {
                            Integer value = (Integer) f.get(null); 
                            sRMap.put(value, new String[] { f.getName(), resType });
                            fullMap.put(f.getName(), value);
                        } else {
                            assert false;
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            // FIXME: log/return the error (there's no logger object at this point!)
            e.printStackTrace();
            return false;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /*
     * (non-Javadoc)
     * @see com.android.layoutlib.api.ILayoutBridge#computeLayout(com.android.layoutlib.api.IXmlPullParser, java.lang.Object, int, int, java.lang.String, java.util.Map, java.util.Map, com.android.layoutlib.api.IProjectCallback, com.android.layoutlib.api.ILayoutLog)
     */
    public ILayoutResult computeLayout(IXmlPullParser layoutDescription,
            Object projectKey,
            int screenWidth, int screenHeight, String themeName,
            Map<String, Map<String, IResourceValue>> projectResources,
            Map<String, Map<String, IResourceValue>> frameworkResources,
            IProjectCallback customViewLoader, ILayoutLog logger) {
        // DEBUG
        //long time1 = System.currentTimeMillis();
        
        if (logger == null) {
            logger = sDefaultLogger;
        }
        
        mLogger = logger;
        
        // find the current theme and compute the style inheritance map
        Map<IStyleResourceValue, IStyleResourceValue> styleParentMap =
            new HashMap<IStyleResourceValue, IStyleResourceValue>();
        
        IStyleResourceValue currentTheme = computeStyleMaps(themeName,
                projectResources.get(BridgeConstants.RES_STYLE),
                frameworkResources.get(BridgeConstants.RES_STYLE), styleParentMap);
        
        BridgeContext context = new BridgeContext(projectKey, currentTheme, projectResources,
                frameworkResources, styleParentMap, customViewLoader, logger);
        BridgeInflater inflater = new BridgeInflater(context, customViewLoader);
        context.setBridgeInflater(inflater);
        
        IResourceValue windowBackground = null;
        int screenOffset = 0;
        if (currentTheme != null) {
            windowBackground = context.findItemInStyle(currentTheme, "windowBackground");
            windowBackground = context.resolveResValue(windowBackground);

            screenOffset = getScreenOffset(currentTheme, context);
        }
        
        // we need to make sure the Looper has been initialized for this thread.
        // this is required for View that creates Handler objects.
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        
        BridgeXmlBlockParser parser = new BridgeXmlBlockParser(layoutDescription,
                context, false /* platformResourceFlag */);
        
        ViewGroup root = new FrameLayout(context);
        
        try {
            View view = inflater.inflate(parser, root);
            
            // set the AttachInfo on the root view.
            AttachInfo info = new AttachInfo(new Handler());
            info.mHasWindowFocus = true;
            info.mWindowVisibility = View.VISIBLE;
            info.mInTouchMode = false; // this is so that we can display selections.
            info.mSession = new WindowSession();
            root.dispatchAttachedToWindow(info, 0);

            // get the background drawable
            if (windowBackground != null) {
                Drawable d = ResourceHelper.getDrawable(windowBackground.getValue(),
                        context, true /* isFramework */);
                root.setBackgroundDrawable(d);
            }

            int w_spec = MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY);
            int h_spec = MeasureSpec.makeMeasureSpec(screenHeight - screenOffset,
                    MeasureSpec.EXACTLY);

            // measure the views
            view.measure(w_spec, h_spec);
            view.layout(0, screenOffset, screenWidth, screenHeight);
            
            // draw them
            BridgeCanvas canvas = new BridgeCanvas(screenWidth, screenHeight - screenOffset,
                    logger);
            
            root.draw(canvas);
            canvas.dispose();
            
            // DEBUG
            //long time2 = System.currentTimeMillis();
            //System.out.println("Layout: " + (time2 - time1));

            return new LayoutResult(visit(((ViewGroup)view).getChildAt(0), context),
                    canvas.getImage());
        } catch (Throwable e) {
            // get the real cause of the exception.
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }

            // log it
            logger.error(t);

            // then return with an ERROR status and the message from the real exception
            return new LayoutResult(ILayoutResult.ERROR,
                    t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.layoutlib.api.ILayoutLibBridge#clearCaches(java.lang.Object)
     */
    public void clearCaches(Object projectKey) {
        if (projectKey != null) {
            sProjectBitmapCache.remove(projectKey);
            sProject9PatchCache.remove(projectKey);
        }
    }
    
    /**
     * Returns details of a framework resource from its integer value.
     * @param value the integer value
     * @return an array of 2 strings containing the resource name and type, or null if the id
     * does not match any resource. 
     */
    public static String[] resolveResourceValue(int value) {
        return sRMap.get(value);
        
    }
    
    /**
     * Returns the name of a framework resource whose value is an int array.
     * @param array
     * @return
     */
    public static String resolveResourceValue(int[] array) {
        return sRArrayMap.get(array);
    }
    
    /**
     * Returns the integer id of a framework resource, from a given resource type and resource name.
     * @param type the type of the resource
     * @param name the name of the resource.
     * @return an {@link Integer} containing the resource id, or null if no resource were found.
     */
    public static Integer getResourceValue(String type, String name) {
        Map<String, Integer> map = sRFullMap.get(type);
        if (map != null) {
            return map.get(name);
        }
        
        return null;
    }
    
    static Map<String, Integer> getEnumValues(String attributeName) {
        if (sEnumValueMap != null) {
            return sEnumValueMap.get(attributeName);
        }
        
        return null;
    }

    /**
     * Visits a View and its children and generate a {@link ILayoutViewInfo} containing the
     * bounds of all the views.
     * @param view the root View
     * @param context the context.
     * @return
     */
    private ILayoutViewInfo visit(View view, BridgeContext context) {
        if (view == null) {
            return null;
        }

        LayoutViewInfo result = new LayoutViewInfo(view.getClass().getName(),
                context.getViewKey(view),
                view.getLeft(), view.getTop(), view.getRight(), view.getBottom());

        if (view instanceof ViewGroup) {
            ViewGroup group = ((ViewGroup) view);
            int n = group.getChildCount();
            ILayoutViewInfo[] children = new ILayoutViewInfo[n];
            for (int i = 0; i < group.getChildCount(); i++) {
                children[i] = visit(group.getChildAt(i), context);
            }
            result.setChildren(children);
        }

        return result;
    }
    
    /**
     * Compute style information from the given list of style for the project and framework.
     * @param themeName the name of the current theme.  In order to differentiate project and
     * platform themes sharing the same name, all project themes must be prepended with
     * a '*' character.
     * @param inProjectStyleMap the project style map
     * @param inFrameworkStyleMap the framework style map
     * @param outInheritanceMap the map of style inheritance. This is filled by the method
     * @return the {@link IStyleResourceValue} matching <var>themeName</var>
     */
    private IStyleResourceValue computeStyleMaps(
            String themeName,
            Map<String, IResourceValue> inProjectStyleMap,
            Map<String, IResourceValue> inFrameworkStyleMap,
            Map<IStyleResourceValue, IStyleResourceValue> outInheritanceMap) {
        
        if (inProjectStyleMap != null && inFrameworkStyleMap != null) {
            // first, get the theme
            IResourceValue theme = null;
            
            // project theme names have been prepended with a *
            if (themeName.charAt(0) == '*') {
                themeName = themeName.substring(1);
                theme = inProjectStyleMap.get(themeName);
            }

            if (theme == null) {
                theme = inFrameworkStyleMap.get(themeName);
            }
            
            if (theme instanceof IStyleResourceValue) {
                // compute the inheritance map for both the project and framework styles
                computeStyleInheritance(inProjectStyleMap.values(), inProjectStyleMap,
                        inFrameworkStyleMap, outInheritanceMap);
    
                // Compute the style inheritance for the framework styles/themes.
                // Since, for those, the style parent values do not contain 'android:'
                // we want to force looking in the framework style only to avoid using
                // similarly named styles from the project.
                // To do this, we pass null in lieu of the project style map.
                computeStyleInheritance(inFrameworkStyleMap.values(), null /*inProjectStyleMap */,
                        inFrameworkStyleMap, outInheritanceMap);
    
                return (IStyleResourceValue)theme;
            }
        }
        
        return null;
    }

    /**
     * Compute the parent style for all the styles in a given list.
     * @param styles the styles for which we compute the parent.
     * @param inProjectStyleMap the map of project styles.
     * @param inFrameworkStyleMap the map of framework styles.
     * @param outInheritanceMap the map of style inheritance. This is filled by the method.
     */
    private void computeStyleInheritance(Collection<IResourceValue> styles,
            Map<String, IResourceValue> inProjectStyleMap,
            Map<String, IResourceValue> inFrameworkStyleMap,
            Map<IStyleResourceValue, IStyleResourceValue> outInheritanceMap) {
        for (IResourceValue value : styles) {
            if (value instanceof IStyleResourceValue) {
                IStyleResourceValue style = (IStyleResourceValue)value;
                IStyleResourceValue parentStyle = null;

                // first look for a specified parent.
                String parentName = style.getParentStyle();
                
                // no specified parent? try to infer it from the name of the style.
                if (parentName == null) {
                    parentName = getParentName(value.getName());
                }

                if (parentName != null) {
                    parentStyle = getStyle(parentName, inProjectStyleMap, inFrameworkStyleMap);
                    
                    if (parentStyle != null) {
                        outInheritanceMap.put(style, parentStyle);
                    }
                }
            }
        }
    }
    
    /**
     * Searches for and returns the {@link IStyleResourceValue} from a given name.
     * <p/>The format of the name can be:
     * <ul>
     * <li>[android:]&lt;name&gt;</li>
     * <li>[android:]style/&lt;name&gt;</li>
     * <li>@[android:]style/&lt;name&gt;</li>
     * </ul>
     * @param parentName the name of the style.
     * @param inProjectStyleMap the project style map. Can be <code>null</code>
     * @param inFrameworkStyleMap the framework style map.
     * @return The matching {@link IStyleResourceValue} object or <code>null</code> if not found.
     */
    private IStyleResourceValue getStyle(String parentName,
            Map<String, IResourceValue> inProjectStyleMap,
            Map<String, IResourceValue> inFrameworkStyleMap) {
        boolean frameworkOnly = false;
        
        String name = parentName;
        
        // remove the useless @ if it's there
        if (name.startsWith(BridgeConstants.PREFIX_RESOURCE_REF)) {
            name = name.substring(BridgeConstants.PREFIX_RESOURCE_REF.length());
        }
        
        // check for framework identifier.
        if (name.startsWith(BridgeConstants.PREFIX_ANDROID)) {
            frameworkOnly = true;
            name = name.substring(BridgeConstants.PREFIX_ANDROID.length());
        }
        
        // at this point we could have the format style/<name>. we want only the name
        if (name.startsWith(BridgeConstants.REFERENCE_STYLE)) {
            name = name.substring(BridgeConstants.REFERENCE_STYLE.length());
        }

        IResourceValue parent = null;
        
        // if allowed, search in the project resources.
        if (frameworkOnly == false && inProjectStyleMap != null) {
            parent = inProjectStyleMap.get(name);
        }

        // if not found, then look in the framework resources.
        if (parent == null) {
            parent = inFrameworkStyleMap.get(name);
        }
        
        // make sure the result is the proper class type and return it.
        if (parent instanceof IStyleResourceValue) {
            return (IStyleResourceValue)parent;
        }
        
        mLogger.error(String.format("Unable to resolve parent style name: ", parentName));
        
        return null;
    }
    
    /**
     * Compute the name of the parent style, or <code>null</code> if the style is a root style.
     * @param styleName
     * @return
     */
    private String getParentName(String styleName) {
        int index = styleName.lastIndexOf('.');
        if (index != -1) {
            return styleName.substring(0, index);
        }
        
        return null;
    }
    
    /**
     * Returns the top screen offset. This depends on whether the current theme defines the user
     * of the title and status bars.
     * @param currentTheme
     * @param styleInheritanceMap
     * @return the pixel height offset
     */
    private int getScreenOffset(IStyleResourceValue currentTheme, BridgeContext context) {
        int offset = 0;

        // get the title bar flag from the current theme.
        IResourceValue value = context.findItemInStyle(currentTheme, "windowNoTitle");
        
        // because it may reference something else, we resolve it.
        value = context.resolveResValue(value);

        // if there's a value and it's true (default is false)
        if (value == null || value.getValue() == null ||
                XmlUtils.convertValueToBoolean(value.getValue(), false /* defValue */) == false) {
            // get value from the theme.
            value = context.findItemInStyle(currentTheme, "windowTitleSize");
            
            // resolve it
            value = context.resolveResValue(value);
            
            // default value
            offset = DEFAULT_TITLE_BAR_HEIGHT;

            // get the real value;
            if (value != null) {
                TypedValue typedValue = ResourceHelper.getValue(value.getValue());
                if (typedValue != null) {
                    offset = (int)typedValue.getDimension(context.getResources().mMetrics);   
                }
            }
        }
        
        // get the fullscreen flag from the current theme.
        value = context.findItemInStyle(currentTheme, "windowFullscreen");
        
        // because it may reference something else, we resolve it.
        value = context.resolveResValue(value);
        
        if (value == null || value.getValue() == null ||
                XmlUtils.convertValueToBoolean(value.getValue(), false /* defValue */) == false) {
            // FIXME: Right now this is hard-coded in the platform, but once there's a constant, we'll need to use it.
            offset += DEFAULT_STATUS_BAR_HEIGHT;
        }

        return offset;
    }

    /**
     * Returns the bitmap for a specific path, from a specific project cache, or from the
     * framework cache.
     * @param value the path of the bitmap
     * @param projectKey the key of the project, or null to query the framework cache.
     * @return the cached Bitmap or null if not found.
     */
    static Bitmap getCachedBitmap(String value, Object projectKey) {
        if (projectKey != null) {
            Map<String, Bitmap> map = sProjectBitmapCache.get(projectKey);
            if (map != null) {
                return map.get(value);
            }
            
            return null;
        }
        
        return sFrameworkBitmapCache.get(value);
    }

    /**
     * Sets a bitmap in a project cache or in the framework cache.
     * @param value the path of the bitmap
     * @param bmp the Bitmap object
     * @param projectKey the key of the project, or null to put the bitmap in the framework cache.
     */
    static void setCachedBitmap(String value, Bitmap bmp, Object projectKey) {
        if (projectKey != null) {
            Map<String, Bitmap> map = sProjectBitmapCache.get(projectKey);

            if (map == null) {
                map = new HashMap<String, Bitmap>();
                sProjectBitmapCache.put(projectKey, map);
            }
            
            map.put(value, bmp);
        }
        
        sFrameworkBitmapCache.put(value, bmp);
    }

    /**
     * Returns the 9 patch for a specific path, from a specific project cache, or from the
     * framework cache.
     * @param value the path of the 9 patch
     * @param projectKey the key of the project, or null to query the framework cache.
     * @return the cached 9 patch or null if not found.
     */
    static NinePatch getCached9Patch(String value, Object projectKey) {
        if (projectKey != null) {
            Map<String, NinePatch> map = sProject9PatchCache.get(projectKey);
            
            if (map != null) {
                return map.get(value);
            }
            
            return null;
        }
        
        return sFramework9PatchCache.get(value);
    }

    /**
     * Sets a 9 patch in a project cache or in the framework cache.
     * @param value the path of the 9 patch
     * @param ninePatch the 9 patch object
     * @param projectKey the key of the project, or null to put the bitmap in the framework cache.
     */
    static void setCached9Patch(String value, NinePatch ninePatch, Object projectKey) {
        if (projectKey != null) {
            Map<String, NinePatch> map = sProject9PatchCache.get(projectKey);

            if (map == null) {
                map = new HashMap<String, NinePatch>();
                sProject9PatchCache.put(projectKey, map);
            }
            
            map.put(value, ninePatch);
        }
        
        sFramework9PatchCache.put(value, ninePatch);
    }
    
    /**
     * Implementation of IWindowSession so that mSession is not null in the SurfaceView.
     */
    private static final class WindowSession implements IWindowSession {

        public int add(IWindow arg0, LayoutParams arg1, int arg2, Rect arg3) throws RemoteException {
            // pass for now.
            return 0;
        }

        public void finishDrawing(IWindow arg0) throws RemoteException {
            // pass for now.
        }

        public void finishKey(IWindow arg0) throws RemoteException {
            // pass for now.
        }

        public boolean getInTouchMode() throws RemoteException {
            // pass for now.
            return false;
        }

        public MotionEvent getPendingPointerMove(IWindow arg0) throws RemoteException {
            // pass for now.
            return null;
        }

        public MotionEvent getPendingTrackballMove(IWindow arg0) throws RemoteException {
            // pass for now.
            return null;
        }

        public int relayout(IWindow arg0, LayoutParams arg1, int arg2, int arg3, int arg4,
                Rect arg5, Rect arg6, Surface arg7) throws RemoteException {
            // pass for now.
            return 0;
        }

        public void remove(IWindow arg0) throws RemoteException {
            // pass for now.
        }

        public void setInTouchMode(boolean arg0) throws RemoteException {
            // pass for now.
        }

        public void setTransparentRegion(IWindow arg0, Region arg1) throws RemoteException {
            // pass for now.
        }

        public IBinder asBinder() {
            // pass for now.
            return null;
        }
        
    };

}
