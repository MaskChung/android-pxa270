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

package android.text.method;

import android.text.Layout;
import android.text.Spannable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

public class Touch {
    private Touch() { }

    /**
     * Scrolls the specified widget to the specified coordinates, except
     * constrains the X scrolling position to the horizontal regions of
     * the text that will be visible after scrolling to the specified
     * Y position.
     */
    public static void scrollTo(TextView widget, Layout layout, int x, int y) {
        int padding = widget.getTotalPaddingTop() +
                      widget.getTotalPaddingBottom();
        int top = layout.getLineForVertical(y);
        int bottom = layout.getLineForVertical(y + widget.getHeight() -
                                               padding);

        int left = Integer.MAX_VALUE;
        int right = 0;

        for (int i = top; i <= bottom; i++) {
            left = (int) Math.min(left, layout.getLineLeft(i));
            right = (int) Math.max(right, layout.getLineRight(i));
        }

        padding = widget.getTotalPaddingLeft() + widget.getTotalPaddingRight();
        x = Math.min(x, right - (widget.getWidth() - padding));
        x = Math.max(x, left);

        widget.scrollTo(x, y);
    }

    /**
     * Handles touch events for dragging.  You may want to do other actions
     * like moving the cursor on touch as well.
     */
    public static boolean onTouchEvent(TextView widget, Spannable buffer,
                                       MotionEvent event) {
        DragState[] ds;

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            buffer.setSpan(new DragState(event.getX(), event.getY()),
                           0, 0, Spannable.SPAN_MARK_MARK);
            return true;

        case MotionEvent.ACTION_UP:
            ds = buffer.getSpans(0, buffer.length(), DragState.class);

            for (int i = 0; i < ds.length; i++) {
                buffer.removeSpan(ds[i]);
            }

            if (ds.length > 0 && ds[0].mUsed) {
                return true;
            } else {
                return false;
            }

        case MotionEvent.ACTION_MOVE:
            ds = buffer.getSpans(0, buffer.length(), DragState.class);

            if (ds.length > 0) {
                if (ds[0].mFarEnough == false) {
                    int slop = ViewConfiguration.getTouchSlop();

                    if (Math.abs(event.getX() - ds[0].mX) >= slop ||
                        Math.abs(event.getY() - ds[0].mY) >= slop) {
                        ds[0].mFarEnough = true;
                    }
                }

                if (ds[0].mFarEnough) {
                    ds[0].mUsed = true;

                    float dx = ds[0].mX - event.getX();
                    float dy = ds[0].mY - event.getY();

                    ds[0].mX = event.getX();
                    ds[0].mY = event.getY();

                    int nx = widget.getScrollX() + (int) dx;
                    int ny = widget.getScrollY() + (int) dy;

                    int padding = widget.getTotalPaddingTop() +
                                  widget.getTotalPaddingBottom();
                    Layout layout = widget.getLayout();

                    ny = Math.min(ny, layout.getHeight() - (widget.getHeight() -
                                                            padding));
                    ny = Math.max(ny, 0);
        
                    scrollTo(widget, layout, nx, ny);
                    widget.cancelLongPress();
                    return true;
                }
            }
        }

        return false;
    }

    private static class DragState {
        public float mX;
        public float mY;
        public boolean mFarEnough;
        public boolean mUsed;

        public DragState(float x, float y) {
            mX = x;
            mY = y;
        }
    }
}
