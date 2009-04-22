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

package android.view;

import android.graphics.*;
import android.os.Parcelable;
import android.os.Parcel;
import android.util.Log;

/**
 * Handle on to a raw buffer that is being managed by the screen compositor.
 */
public class Surface implements Parcelable {
    private static final String LOG_TAG = "Surface";
    
    /* flags used in constructor (keep in sync with ISurfaceComposer.h) */

    /** Surface is created hidden */
    public static final int HIDDEN              = 0x00000004;

    /** The surface is to be used by hardware accelerators or DMA engines */
    public static final int HARDWARE            = 0x00000010;

    /** Implies "HARDWARE", the surface is to be used by the GPU
     * additionally the backbuffer is never preserved for these
     * surfaces. */
    public static final int GPU                 = 0x00000028;

    /** The surface contains secure content, special measures will
     * be taken to disallow the surface's content to be copied from
     * another process. In particular, screenshots and VNC servers will
     * be disabled, but other measures can take place, for instance the
     * surface might not be hardware accelerated. */
    public static final int SECURE              = 0x00000080;
    
    /** Creates a surface where color components are interpreted as 
     *  "non pre-multiplied" by their alpha channel. Of course this flag is
     *  meaningless for surfaces without an alpha channel. By default
     *  surfaces are pre-multiplied, which means that each color component is
     *  already multiplied by its alpha value. In this case the blending
     *  equation used is:
     *  
     *    DEST = SRC + DEST * (1-SRC_ALPHA)
     *    
     *  By contrast, non pre-multiplied surfaces use the following equation:
     *  
     *    DEST = SRC * SRC_ALPHA * DEST * (1-SRC_ALPHA)
     *    
     *  pre-multiplied surfaces must always be used if transparent pixels are
     *  composited on top of each-other into the surface. A pre-multiplied
     *  surface can never lower the value of the alpha component of a given
     *  pixel.
     *  
     *  In some rare situations, a non pre-multiplied surface is preferable.
     *  
     */
    public static final int NON_PREMULTIPLIED   = 0x00000100;
    
    /**
     * Creates a surface without a rendering buffer. Instead, the content
     * of the surface must be pushed by an external entity. This is type
     * of surface can be used for efficient camera preview or movie
     * play back.
     */
    public static final int PUSH_BUFFERS        = 0x00000200;
    
    /** Creates a normal surface. This is the default */
    public static final int FX_SURFACE_NORMAL   = 0x00000000;
    
    /** Creates a Blur surface. Everything behind this surface is blurred
     * by some amount. The quality and refresh speed of the blur effect
     * is not settable or guaranteed.
     * It is an error to lock a Blur surface, since it doesn't have
     * a backing store.
     */
    public static final int FX_SURFACE_BLUR     = 0x00010000;
    
    /** Creates a Dim surface. Everything behind this surface is dimmed
     * by the amount specified in setAlpha(). 
     * It is an error to lock a Dim surface, since it doesn't have
     * a backing store.
     */
    public static final int FX_SURFACE_DIM     = 0x00020000;

    /** Mask used for FX values above */
    public static final int FX_SURFACE_MASK     = 0x000F0000;

    /* flags used with setFlags() (keep in sync with ISurfaceComposer.h) */
    
    /** Hide the surface. Equivalent to calling hide() */
    public static final int SURFACE_HIDDEN    = 0x01;
    
    /** Freeze the surface. Equivalent to calling freeze() */ 
    public static final int SURACE_FROZEN     = 0x02;
    
    /** Enable dithering when compositing this surface */
    public static final int SURFACE_DITHER    = 0x04;

    public static final int SURFACE_BLUR_FREEZE= 0x10;

    /* orientations for setOrientation() */
    public static final int ROTATION_0       = 0;
    public static final int ROTATION_90      = 1;
    public static final int ROTATION_180     = 2;
    public static final int ROTATION_270     = 3;
    
    @SuppressWarnings("unused")
    private int mSurface;
    @SuppressWarnings("unused")
    private int mSaveCount;
    @SuppressWarnings("unused")
    private Canvas mCanvas;

    /**
     * Exception thrown when a surface couldn't be created or resized
     */
    public static class OutOfResourcesException extends Exception {
        public OutOfResourcesException() {
        }
        public OutOfResourcesException(String name) {
            super(name);
        }
    }

    /*
     * We use a class initializer to allow the native code to cache some
     * field offsets.
     */
    native private static void nativeClassInit();
    static { nativeClassInit(); }

    
    /**
     * create a surface
     * {@hide}
     */
    public Surface(SurfaceSession s,
            int pid, int display, int w, int h, int format, int flags)
        throws OutOfResourcesException {
        mCanvas = new Canvas();
        init(s,pid,display,w,h,format,flags);
    }

    /**
     * Create an empty surface, which will later be filled in by
     * readFromParcel().
     * {@hide}
     */
    public Surface() {
        mCanvas = new Canvas();
    }
    
    /**
     * Copy another surface to this one.  This surface now holds a reference
     * to the same data as the original surface, and is -not- the owner.
     * {@hide}
     */
    public native   void copyFrom(Surface o);
    
    /**
     * Does this object hold a valid surface?  Returns true if it holds
     * a physical surface, so lockCanvas() will succeed.  Otherwise
     * returns false.
     */
    public native   boolean isValid();
    
    /** Call this free the surface up. {@hide} */
    public native   void clear();
    
    /** draw into a surface */
    public Canvas lockCanvas(Rect dirty) throws OutOfResourcesException {
        /* the dirty rectangle may be expanded to the surface's size, if
         * for instance it has been resized or if the bits were lost, since
         * the last call.
         */
        return lockCanvasNative(dirty);
    }

    private native Canvas lockCanvasNative(Rect dirty);

    /** unlock the surface and asks a page flip */
    public native   void unlockCanvasAndPost(Canvas canvas);

    /** 
     * unlock the surface. the screen won't be updated until
     * post() or postAll() is called
     */
    public native   void unlockCanvas(Canvas canvas);
    
    /** start/end a transaction {@hide} */
    public static native   void openTransaction();
    /** {@hide} */
    public static native   void closeTransaction();

    /**
     * Freezes the specified display, No updating of the screen will occur
     * until unfreezeDisplay() is called. Everything else works as usual though,
     * in particular transactions.
     * @param display
     * {@hide}
     */
    public static native   void freezeDisplay(int display);

    /**
     * resume updating the specified display.
     * @param display
     * {@hide}
     */
    public static native   void unfreezeDisplay(int display);

    /**
     * set the orientation of the given display.
     * @param display
     * @param orientation
     */
    public static native   void setOrientation(int display, int orientation);

    /**
     * set surface parameters.
     * needs to be inside open/closeTransaction block
     */
    public native   void setLayer(int zorder);
    public native   void setPosition(int x, int y);
    public native   void setSize(int w, int h);

    public native   void hide();
    public native   void show();
    public native   void setTransparentRegionHint(Region region);
    public native   void setAlpha(float alpha);
    public native   void setMatrix(float dsdx, float dtdx,
                                   float dsdy, float dtdy);

    public native   void freeze();
    public native   void unfreeze();

    public native   void setFreezeTint(int tint);

    public native   void setFlags(int flags, int mask);

    @Override
    public String toString() {
        return "Surface(native-token=" + mSurface + ")";
    }

    private Surface(Parcel source) throws OutOfResourcesException {
        init(source);
    }
    
    public int describeContents() {
        return 0;
    }

    public native   void readFromParcel(Parcel source);
    public native   void writeToParcel(Parcel dest, int flags);

    public static final Parcelable.Creator<Surface> CREATOR
            = new Parcelable.Creator<Surface>()
    {
        public Surface createFromParcel(Parcel source) {
            try {
                return new Surface(source);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Exception creating surface from parcel", e);
            }
            return null;
        }

        public Surface[] newArray(int size) {
            return new Surface[size];
        }
    };

    /* no user serviceable parts here ... */
    @Override
    protected void finalize() throws Throwable {
        clear();
    }
    
    private native void init(SurfaceSession s,
            int pid, int display, int w, int h, int format, int flags)
            throws OutOfResourcesException;

    private native void init(Parcel source);
}
