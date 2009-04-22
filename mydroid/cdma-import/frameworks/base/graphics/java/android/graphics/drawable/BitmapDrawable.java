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

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.BitmapShader;
import android.util.AttributeSet;
import android.view.Gravity;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class BitmapDrawable extends Drawable {

    private static final int DEFAULT_PAINT_FLAGS = Paint.FILTER_BITMAP_FLAG;
    private BitmapState mBitmapState;
    private Bitmap mBitmap;
    private final Rect mDstRect = new Rect();   // Gravity.apply() sets this

    private boolean mApplyGravity;
    private boolean mRebuildShader;

    public BitmapDrawable() {
        mBitmapState = new BitmapState(null);
    }

    public BitmapDrawable(Bitmap bitmap) {
        this(new BitmapState(bitmap));
    }

    public BitmapDrawable(String filepath) {
        this(new BitmapState(BitmapFactory.decodeFile(filepath)));
        if (mBitmap == null) {
            android.util.Log.w("BitmapDrawable", "BitmapDrawable cannot decode " + filepath);
        }
    }

    public BitmapDrawable(java.io.InputStream is) {
        this(new BitmapState(BitmapFactory.decodeStream(is)));
        if (mBitmap == null) {
            android.util.Log.w("BitmapDrawable", "BitmapDrawable cannot decode " + is);
        }
    }

    public final Paint getPaint() {
        return mBitmapState.mPaint;
    }
    
    public final Bitmap getBitmap() {
        return mBitmap;
    }
    
    /** Get the gravity used to position/stretch the bitmap within its bounds.
        See android.view.Gravity
     * @return the gravity applied to the bitmap
     */
    public int getGravity() {
        return mBitmapState.mGravity;
    }
    
    /** Set the gravity used to position/stretch the bitmap within its bounds.
        See android.view.Gravity
     * @param gravity the gravity
     */
    public void setGravity(int gravity) {
        mBitmapState.mGravity = gravity;
        mApplyGravity = true;
    }

    public void setAntiAlias(boolean aa) {
        mBitmapState.mPaint.setAntiAlias(aa);
    }
    
    @Override
    public void setFilterBitmap(boolean filter) {
        mBitmapState.mPaint.setFilterBitmap(filter);
    }

    @Override
    public void setDither(boolean dither) {
        mBitmapState.mPaint.setDither(dither);
    }

    public Shader.TileMode getTileModeX() {
        return mBitmapState.mTileModeX;
    }

    public Shader.TileMode getTileModeY() {
        return mBitmapState.mTileModeY;
    }

    public void setTileModeX(Shader.TileMode mode) {
        setTileModeXY(mode, mBitmapState.mTileModeY);
    }

    public final void setTileModeY(Shader.TileMode mode) {
        setTileModeXY(mBitmapState.mTileModeX, mode);
    }

    public void setTileModeXY(Shader.TileMode xmode, Shader.TileMode ymode) {
        final BitmapState state = mBitmapState;
        if (state.mTileModeX != xmode || state.mTileModeY != ymode) {
            state.mTileModeX = xmode;
            state.mTileModeY = ymode;
            mRebuildShader = true;
        }
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mBitmapState.mChangingConfigurations;
    }
    
    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mApplyGravity = true;
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap bitmap = mBitmap;
        if (bitmap != null) {
            final BitmapState state = mBitmapState;
            if (mRebuildShader) {
                Shader.TileMode tmx = state.mTileModeX;
                Shader.TileMode tmy = state.mTileModeY;

                if (tmx == null && tmy == null) {
                    state.mPaint.setShader(null);
                } else {
                    Shader s = new BitmapShader(bitmap,
                            tmx == null ? Shader.TileMode.CLAMP : tmx,
                            tmy == null ? Shader.TileMode.CLAMP : tmy);
                    state.mPaint.setShader(s);
                }
                mRebuildShader = false;
                copyBounds(mDstRect);
            }

            Shader shader = state.mPaint.getShader();
            if (shader == null) {
                if (mApplyGravity) {
                    Gravity.apply(state.mGravity, bitmap.getWidth(), bitmap.getHeight(),
                            getBounds(), mDstRect);
                    mApplyGravity = false;
                }
                canvas.drawBitmap(bitmap, null, mDstRect, state.mPaint);
            } else {
                if (mApplyGravity) {
                    mDstRect.set(getBounds());
                    mApplyGravity = false;
                }
                canvas.drawRect(mDstRect, state.mPaint);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mBitmapState.mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mBitmapState.mPaint.setColorFilter(cf);
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs);

        TypedArray a = r.obtainAttributes(attrs, com.android.internal.R.styleable.BitmapDrawable);

        final int id = a.getResourceId(com.android.internal.R.styleable.BitmapDrawable_src, 0);
        if (id == 0) {
            throw new XmlPullParserException(parser.getPositionDescription() +
                    ": <bitmap> requires a valid src attribute");
        }
        final Bitmap bitmap = BitmapFactory.decodeResource(r, id);
        if (bitmap == null) {
            throw new XmlPullParserException(parser.getPositionDescription() +
                    ": <bitmap> requires a valid src attribute");
        }
        mBitmapState.mBitmap = mBitmap = bitmap;

        final Paint paint = mBitmapState.mPaint;
        paint.setAntiAlias(a.getBoolean(com.android.internal.R.styleable.BitmapDrawable_antialias,
                paint.isAntiAlias()));
        paint.setFilterBitmap(a.getBoolean(com.android.internal.R.styleable.BitmapDrawable_filter,
                paint.isFilterBitmap()));
        paint.setDither(a.getBoolean(com.android.internal.R.styleable.BitmapDrawable_dither,
                paint.isDither()));
        setGravity(a.getInt(com.android.internal.R.styleable.BitmapDrawable_gravity, Gravity.FILL));
        int tileMode = a.getInt(com.android.internal.R.styleable.BitmapDrawable_tileMode, -1);
        if (tileMode != -1) {
            switch (tileMode) {
                case 0:
                    setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    break;
                case 1:
                    setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                    break;
                case 2:
                    setTileModeXY(Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
                    break;
            }
        }

        a.recycle();
    }

    @Override
    public int getIntrinsicWidth() {
        Bitmap bitmap = mBitmap;
        return bitmap != null ? bitmap.getWidth() : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        Bitmap bitmap = mBitmap;
        return bitmap != null ? bitmap.getHeight() : -1;
    }

    @Override
    public int getOpacity() {
        if (mBitmapState.mGravity != Gravity.FILL) {
            return PixelFormat.TRANSLUCENT;
        }
        Bitmap bm = mBitmap;
        return (bm == null || bm.hasAlpha() || mBitmapState.mPaint.getAlpha() < 255) ?
                PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    @Override
    public final ConstantState getConstantState() {
        mBitmapState.mChangingConfigurations = super.getChangingConfigurations();
        return mBitmapState;
    }

    final static class BitmapState extends ConstantState {
        Bitmap mBitmap;
        int mChangingConfigurations;
        int mGravity = Gravity.FILL;
        Paint mPaint = new Paint(DEFAULT_PAINT_FLAGS);
        Shader.TileMode mTileModeX;
        Shader.TileMode mTileModeY;

        BitmapState(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        @Override
        public Drawable newDrawable() {
            return new BitmapDrawable(this);
        }
        
        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }
    }

    private BitmapDrawable(BitmapState state) {
        mBitmapState = state;
        mBitmap = state.mBitmap;
    }
}
