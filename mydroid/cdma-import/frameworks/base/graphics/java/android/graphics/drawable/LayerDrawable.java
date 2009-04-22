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

package android.graphics.drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;

/** Drawable that manages an array of other drawables. These are drawn in array
    order, so the element with the largest index will be drawn on top.
*/
public class LayerDrawable extends Drawable implements Drawable.Callback {
    
    /* package */ LayerState  mLayerState;

    private int[]       mPaddingL;
    private int[]       mPaddingT;
    private int[]       mPaddingR;
    private int[]       mPaddingB;

    private final Rect  mTmpRect = new Rect();

    public LayerDrawable(Drawable[] array) {
        this((LayerState)null);
        int length = array.length;
        Rec[] r = new Rec[length];

        for (int i = 0; i < length; i++) {
            r[i] = new Rec();
            r[i].mDrawable = array[i];
            array[i].setCallback(this);
            mLayerState.mChildrenChangingConfigurations
                    |= array[i].getChangingConfigurations();
        }
        mLayerState.mNum = length;
        mLayerState.mArray = r;
        ensurePadding();
    }
    
    /* package */ LayerDrawable() {
        this((LayerState) null);
    }
    

    /* package */ LayerDrawable(LayerState state) {
        LayerState as = createConstantState(state);
        mLayerState = as;
        if (as.mNum > 0) {
            ensurePadding();
        }
    }

    /* package */ LayerState createConstantState(LayerState state) {
        return new LayerState(state, this);
    }
    

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs);

        int type;

        final int innerDepth = parser.getDepth() + 1;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            if (depth > innerDepth || !parser.getName().equals("item")) {
                continue;
            }

            TypedArray a = r.obtainAttributes(attrs,
                    com.android.internal.R.styleable.LayerDrawableItem);

            int left = a.getDimensionPixelOffset(
                    com.android.internal.R.styleable.LayerDrawableItem_left, 0);
            int top = a.getDimensionPixelOffset(
                    com.android.internal.R.styleable.LayerDrawableItem_top, 0);
            int right = a.getDimensionPixelOffset(
                    com.android.internal.R.styleable.LayerDrawableItem_right, 0);
            int bottom = a.getDimensionPixelOffset(
                    com.android.internal.R.styleable.LayerDrawableItem_bottom, 0);
            int drawableRes = a.getResourceId(
                    com.android.internal.R.styleable.LayerDrawableItem_drawable, 0);
            int id = a.getResourceId(com.android.internal.R.styleable.LayerDrawableItem_id,
                    View.NO_ID);

            a.recycle();

            Drawable dr;
            if (drawableRes != 0) {
                dr = r.getDrawable(drawableRes);
            } else {
                while ((type = parser.next()) == XmlPullParser.TEXT) {
                }
                if (type != XmlPullParser.START_TAG) {
                    throw new XmlPullParserException(parser.getPositionDescription()
                            + ": <item> tag requires a 'drawable' attribute or "
                            + "child tag defining a drawable");
                }
                dr = Drawable.createFromXmlInner(r, parser, attrs);
            }

            addLayer(id, dr, left, top, right, bottom);
        }

        ensurePadding();
        onStateChange(getState());
    }

    private void addLayer(int id, Drawable dr, int l, int t, int r, int b) {
        final LayerState st = mLayerState;
        int N = st.mArray != null ? st.mArray.length : 0;
        int i = st.mNum;
        if (i >= N) {
            Rec[] nu = new Rec[N + 10];
            if (i > 0) {
                System.arraycopy(st.mArray, 0, nu, 0, i);
            }
            st.mArray = nu;
        }

        mLayerState.mChildrenChangingConfigurations
                |= dr.getChangingConfigurations();
        
        Rec rec = new Rec();
        st.mArray[i] = rec;
        rec.mId = id;
        rec.mDrawable = dr;
        rec.mInsetL = l;
        rec.mInsetT = t;
        rec.mInsetR = r;
        rec.mInsetB = b;
        st.mNum++;

        dr.setCallback(this);
    }

    /**
     * Look for a layer with the given id, and returns its {@link Drawable}.
     *
     * @param id The layer ID to search for.
     * @return The {@link Drawable} of the layer that has the given id in the hierarchy or null.
     */
    public Drawable findDrawableByLayerId(int id) {
        final Rec[] layers = mLayerState.mArray;
        
        for (int i = mLayerState.mNum - 1; i >= 0; i--) {
            if (layers[i].mId == id) {
                return layers[i].mDrawable;
            }
        }
        
        return null;
    }
    
    /**
     * Sets the ID of a layer.
     * 
     * @param index The index of the layer which will received the ID. 
     * @param id The ID to assign to the layer.
     */
    public void setId(int index, int id) {
        mLayerState.mArray[index].mId = id;
    }
    
    /**
     * Returns the number of layers contained within this.
     * @return The number of layers.
     */
    // TODO: Remove this once XML inflation is there for ShapeDrawable?
    public int getNumberOfLayers() {
        return mLayerState.mNum;
    }
    
    // TODO: Remove once XML inflation...
    public Drawable getDrawable(int index) {
        return mLayerState.mArray[index].mDrawable;
    }
    
    public int getId(int index) {
        return mLayerState.mArray[index].mId;
    }
    
    
    /**
     * Sets (or replaces) the {@link Drawable} for the layer with the given id.
     * 
     * @param id The layer ID to search for.
     * @param drawable The replacement {@link Drawable}.
     * @return Whether the {@link Drawable} was replaced (could return false if
     *         the id was not found).
     */
    public boolean setDrawableByLayerId(int id, Drawable drawable) {
        final Rec[] layers = mLayerState.mArray;
        
        for (int i = mLayerState.mNum - 1; i >= 0; i--) {
            if (layers[i].mId == id) {
                layers[i].mDrawable = drawable;
                return true;
            }
        }
        
        return false;
    }
    
    /** Specify modifiers to the bounds for the drawable[index].
        left += l
        top += t;
        right -= r;
        bottom -= b;
    */
    public void setLayerInset(int index, int l, int t, int r, int b) {
        Rec rec = mLayerState.mArray[index];
        rec.mInsetL = l;
        rec.mInsetT = t;
        rec.mInsetR = r;
        rec.mInsetB = b;
    }

    // overrides from Drawable.Callback

    public void invalidateDrawable(Drawable who) {
        if (mCallback != null) {
            mCallback.invalidateDrawable(this);
        }
    }

    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        if (mCallback != null) {
            mCallback.scheduleDrawable(this, what, when);
        }
    }

    public void unscheduleDrawable(Drawable who, Runnable what) {
        if (mCallback != null) {
            mCallback.unscheduleDrawable(this, what);
        }
    }

    // overrides from Drawable

    @Override
    public void draw(Canvas canvas) {
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        for (int i=0; i<N; i++) {
            array[i].mDrawable.draw(canvas);
        }
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations()
                | mLayerState.mChangingConfigurations
                | mLayerState.mChildrenChangingConfigurations;
    }
    
    @Override
    public boolean getPadding(Rect padding) {
        // Arbitrarily get the padding from the first image.
        // Technically we should maybe do something more intelligent,
        // like take the max padding of all the images.
        padding.left = 0;
        padding.top = 0;
        padding.right = 0;
        padding.bottom = 0;
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        for (int i=0; i<N; i++) {
            reapplyPadding(i, array[i]);
            padding.left += mPaddingL[i];
            padding.top += mPaddingT[i];
            padding.right += mPaddingR[i];
            padding.bottom += mPaddingB[i];
        }
        return true;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        for (int i=0; i<N; i++) {
            array[i].mDrawable.setVisible(visible, restart);
        }
        return changed;
    }

    @Override
    public void setDither(boolean dither) {
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        for (int i=0; i<N; i++) {
            array[i].mDrawable.setDither(dither);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        for (int i=0; i<N; i++) {
            array[i].mDrawable.setAlpha(alpha);
        }
    }
    
    @Override
    public void setColorFilter(ColorFilter cf) {
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        for (int i=0; i<N; i++) {
            array[i].mDrawable.setColorFilter(cf);
        }
    }
    
    @Override
    public int getOpacity() {
        return mLayerState.getOpacity();
    }

    @Override
    public boolean isStateful() {
        return mLayerState.isStateful();
    }
    
    @Override
    protected boolean onStateChange(int[] state) {
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        boolean paddingChanged = false;
        boolean changed = false;
        for (int i=0; i<N; i++) {
            final Rec r = array[i];
            if (r.mDrawable.setState(state)) {
                changed = true;
            }
            if (reapplyPadding(i, r)) {
                paddingChanged = true;
            }
        }
        if (paddingChanged) {
            onBoundsChange(getBounds());
        }
        return changed;
    }
    
    @Override
    protected boolean onLevelChange(int level) {
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        boolean paddingChanged = false;
        boolean changed = false;
        for (int i=0; i<N; i++) {
            final Rec r = array[i];
            if (r.mDrawable.setLevel(level)) {
                changed = true;
            }
            if (reapplyPadding(i, r)) {
                paddingChanged = true;
            }
        }
        if (paddingChanged) {
            onBoundsChange(getBounds());
        }
        return changed;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        int padL=0, padT=0, padR=0, padB=0;
        for (int i=0; i<N; i++) {
            final Rec r = array[i];
            r.mDrawable.setBounds(bounds.left + r.mInsetL + padL,
                                  bounds.top + r.mInsetT + padT,
                                  bounds.right - r.mInsetR - padR,
                                  bounds.bottom - r.mInsetB - padB);
            padL += mPaddingL[i];
            padR += mPaddingR[i];
            padT += mPaddingT[i];
            padB += mPaddingB[i];
        }
    }

    @Override
    public int getIntrinsicWidth() {
        int width = -1;
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        int padL=0, padR=0;
        for (int i=0; i<N; i++) {
            final Rec r = array[i];
            int w = r.mDrawable.getIntrinsicWidth()
                  + r.mInsetL + r.mInsetR + padL + padR;
            if (w > width) {
                width = w;
            }
            padL += mPaddingL[i];
            padR += mPaddingR[i];
        }
        //System.out.println("Intrinsic width: " + width);
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        int height = -1;
        final Rec[] array = mLayerState.mArray;
        final int N = mLayerState.mNum;
        int padT=0, padB=0;
        for (int i=0; i<N; i++) {
            final Rec r = array[i];
            int h = r.mDrawable.getIntrinsicHeight()
                  + r.mInsetT + r.mInsetB + + padT + padB;
            if (h > height) {
                height = h;
            }
            padT += mPaddingT[i];
            padB += mPaddingB[i];
        }
        //System.out.println("Intrinsic height: " + height);
        return height;
    }

    private boolean reapplyPadding(int i, Rec r) {
        final Rect rect = mTmpRect;
        r.mDrawable.getPadding(rect);
        if (rect.left != mPaddingL[i] || rect.top != mPaddingT[i]
            || rect.right != mPaddingR[i] || rect.bottom != mPaddingB[i]) {
            mPaddingL[i] = rect.left;
            mPaddingT[i] = rect.top;
            mPaddingR[i] = rect.right;
            mPaddingB[i] = rect.bottom;
            return true;
        }
        return false;
    }

    private void ensurePadding() {
        final int N = mLayerState.mNum;
        if (mPaddingL != null && mPaddingL.length >= N) {
            return;
        }
        mPaddingL = new int[N];
        mPaddingT = new int[N];
        mPaddingR = new int[N];
        mPaddingB = new int[N];
    }

    @Override
    public ConstantState getConstantState() {
        if (mLayerState.canConstantState()) {
            mLayerState.mChangingConfigurations = super.getChangingConfigurations();
            return mLayerState;
        }
        return null;
    }

    /* package */ static class Rec {
        public Drawable mDrawable;
        public int mInsetL, mInsetT, mInsetR, mInsetB;
        public int mId;
    }

    /* package */ static class LayerState extends ConstantState {
        int mNum;
        Rec[] mArray;

        int mChangingConfigurations;
        int mChildrenChangingConfigurations;
        
        private boolean mHaveOpacity = false;
        private int mOpacity;

        private boolean mHaveStateful = false;
        private boolean mStateful;

        private boolean mCheckedConstantState;
        private boolean mCanConstantState;

        LayerState(LayerState orig, LayerDrawable owner) {
            if (orig != null) {
                final Rec[] origRec = orig.mArray;
                final int N = orig.mNum;

                mNum = N;
                mArray = new Rec[N];

                mChangingConfigurations = orig.mChangingConfigurations;
                mChildrenChangingConfigurations = orig.mChildrenChangingConfigurations;
                
                for (int i = 0; i < N; i++) {
                    final Rec r = mArray[i] = new Rec();
                    final Rec or = origRec[i];
                    r.mDrawable = or.mDrawable.getConstantState().newDrawable();
                    r.mDrawable.setCallback(owner);
                    r.mInsetL = or.mInsetL;
                    r.mInsetT = or.mInsetT;
                    r.mInsetR = or.mInsetR;
                    r.mInsetB = or.mInsetB;
                    r.mId = or.mId;
                }

                mHaveOpacity = orig.mHaveOpacity;
                mOpacity = orig.mOpacity;
                mHaveStateful = orig.mHaveStateful;
                mStateful = orig.mStateful;
                mCheckedConstantState = mCanConstantState = true;
            } else {
                mNum = 0;
                mArray = null;
            }
        }

        @Override
        public Drawable newDrawable() {
            return new LayerDrawable(this);
        }
        
        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }

        public final int getOpacity() {
            if (mHaveOpacity) {
                return mOpacity;
            }

            final int N = mNum;
            int op = N > 0 ? mArray[0].mDrawable.getOpacity()
                    : PixelFormat.TRANSPARENT;
            for (int i = 1; i < N; i++) {
                op = Drawable.resolveOpacity(op, mArray[i].mDrawable
                        .getOpacity());
            }
            mOpacity = op;
            mHaveOpacity = true;
            return op;
        }
        
        public final boolean isStateful() {
            if (mHaveStateful) {
                return mStateful;
            }
            
            boolean stateful = false;
            final int N = mNum;
            for (int i = 0; i < N; i++) {
                if (mArray[i].mDrawable.isStateful()) {
                    stateful = true;
                    break;
                }
            }
            
            mStateful = stateful;
            mHaveStateful = true;
            return stateful;
        }

        public synchronized boolean canConstantState() {
            if (!mCheckedConstantState && mArray != null) {
                mCanConstantState = true;
                final int N = mNum;
                for (int i=0; i<N; i++) {
                    if (mArray[i].mDrawable.getConstantState() == null) {
                        mCanConstantState = false;
                        break;
                    }
                }
                mCheckedConstantState = true;
            }

            return mCanConstantState;
        }
    }
}

