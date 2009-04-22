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
import android.view.Gravity;
import android.util.AttributeSet;
import android.util.Log;

import java.io.IOException;

/**
 * A drawable that clips another drawable based on this drawable's current
 * level value.  You can control how much the child drawable gets clipped in width
 * and height based on the level, as well as a gravity to control where it is
 * placed in its overall container.  Most often used to implement things like
 * progress bars.
 */
public class ClipDrawable extends Drawable implements Drawable.Callback {
    private ClipState mClipState;
    private final Rect mTmpRect = new Rect();

    public static final int HORIZONTAL = 1;
    public static final int VERTICAL = 2;
    
    ClipDrawable() {
        this(null);
    }

    /**
     * @param orientation Bitwise-or of {@link #HORIZONTAL} and/or {@link #VERTICAL}
     */
    public ClipDrawable(Drawable drawable, int gravity, int orientation) {
        this(null);

        mClipState.mDrawable = drawable;
        mClipState.mGravity = gravity;
        mClipState.mOrientation = orientation;

        if (drawable != null) {
            drawable.setCallback(this);
        }
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs);

        int type;

        TypedArray a = r.obtainAttributes(attrs, com.android.internal.R.styleable.ClipDrawable);

        int orientation = a.getInt(
                com.android.internal.R.styleable.ClipDrawable_clipOrientation,
                HORIZONTAL);
        int g = a.getInt(com.android.internal.R.styleable.ClipDrawable_gravity, Gravity.LEFT);
        Drawable dr = a.getDrawable(com.android.internal.R.styleable.ClipDrawable_drawable);

        a.recycle();

        final int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            dr = Drawable.createFromXmlInner(r, parser, attrs);
        }

        if (dr == null) {
            throw new IllegalArgumentException("No drawable specified for <clip>");
        }

        mClipState.mDrawable = dr;
        mClipState.mOrientation = orientation;
        mClipState.mGravity = g;
        if (dr != null) {
            dr.setCallback(this);
        }
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
    public int getChangingConfigurations() {
        return super.getChangingConfigurations()
                | mClipState.mChangingConfigurations
                | mClipState.mDrawable.getChangingConfigurations();
    }

    @Override
    public boolean getPadding(Rect padding) {
        // XXX need to adjust padding!
        return mClipState.mDrawable.getPadding(padding);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mClipState.mDrawable.setVisible(visible, restart);
        return super.setVisible(visible, restart);
    }

    @Override
    public void setAlpha(int alpha) {
        mClipState.mDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mClipState.mDrawable.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return mClipState.mDrawable.getOpacity();
    }

    @Override
    public boolean isStateful() {
        return mClipState.mDrawable.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean changed = mClipState.mDrawable.setState(state);
        return changed;
    }

    @Override
    protected boolean onLevelChange(int level) {
        mClipState.mDrawable.setLevel(level);
        invalidateSelf();
        return true;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mClipState.mDrawable.setBounds(bounds);
    }

    @Override
    public void draw(Canvas canvas) {
        
        if (mClipState.mDrawable.getLevel() == 0) {
            return;
        }

        final Rect r = mTmpRect;
        final Rect bounds = getBounds();
        int level = getLevel();
        int w = bounds.width();
        final int iw = 0; //mClipState.mDrawable.getIntrinsicWidth();
        if ((mClipState.mOrientation & HORIZONTAL) != 0) {
            w -= (w - iw) * (10000 - level) / 10000;
        }
        int h = bounds.height();
        final int ih = 0; //mClipState.mDrawable.getIntrinsicHeight();
        if ((mClipState.mOrientation & VERTICAL) != 0) {
            h -= (h - ih) * (10000 - level) / 10000;
        }
        Gravity.apply(mClipState.mGravity, w, h, bounds, r);

        if (w > 0 && h > 0) {
            canvas.save();
            canvas.clipRect(r);
            mClipState.mDrawable.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return mClipState.mDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mClipState.mDrawable.getIntrinsicHeight();
    }

    @Override
    public ConstantState getConstantState() {
        if (mClipState.canConstantState()) {
            mClipState.mChangingConfigurations = super.getChangingConfigurations();
            return mClipState;
        }
        return null;
    }

    final static class ClipState extends ConstantState {
        ClipState(ClipState orig, ClipDrawable owner) {
            if (orig != null) {
                mDrawable = orig.mDrawable.getConstantState().newDrawable();
                mDrawable.setCallback(owner);
                mOrientation = orig.mOrientation;
                mGravity = orig.mGravity;
                mCheckedConstantState = mCanConstantState = true;
            }
        }

        @Override
        public Drawable newDrawable() {
            return new ClipDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }

        boolean canConstantState() {
            if (!mCheckedConstantState) {
                mCanConstantState = mDrawable.getConstantState() != null;
                mCheckedConstantState = true;
            }

            return mCanConstantState;
        }

        Drawable mDrawable;
        int mChangingConfigurations;
        int mOrientation;
        int mGravity;

        private boolean mCheckedConstantState;
        private boolean mCanConstantState;
    }

    private ClipDrawable(ClipState state) {
        mClipState = new ClipState(state, this);
    }
}

