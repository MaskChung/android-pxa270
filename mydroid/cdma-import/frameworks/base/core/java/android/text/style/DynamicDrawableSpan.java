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

package android.text.style;

import java.lang.ref.WeakReference;

import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 *
 */
public abstract class DynamicDrawableSpan
extends ReplacementSpan
{
    /**
     * Your subclass must implement this method to provide the bitmap   
     * to be drawn.  The dimensions of the bitmap must be the same
     * from each call to the next.
     */
    public abstract Drawable getDrawable();

    public int getSize(Paint paint, CharSequence text,
                         int start, int end,
                         Paint.FontMetricsInt fm) {
        Drawable b = getCachedDrawable();

        if (fm != null) {
            fm.ascent = -b.getIntrinsicHeight();
            fm.descent = 0;

            fm.top = fm.ascent;
            fm.bottom = 0;
        }

        return b.getIntrinsicWidth();
    }

    public void draw(Canvas canvas, CharSequence text,
                     int start, int end, float x, 
                     int top, int y, int bottom, Paint paint) {
        Drawable b = getCachedDrawable();
        canvas.save();
        
        canvas.translate(x, bottom-b.getIntrinsicHeight());;
        b.draw(canvas);
        canvas.restore();
    }

    private Drawable getCachedDrawable() {
        WeakReference wr = mDrawableRef;
        Drawable b = null;

        if (wr != null)
            b = (Drawable) wr.get();

        if (b == null) {
            b = getDrawable();
            mDrawableRef = new WeakReference(b);
        }

        return b;
    }

    private WeakReference mDrawableRef;
}

