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

import android.graphics.Paint;
import android.graphics.Canvas;
import android.text.Layout;

public interface LeadingMarginSpan
extends ParagraphStyle
{
    public int getLeadingMargin(boolean first);
    public void drawLeadingMargin(Canvas c, Paint p,
                                  int x, int dir,
                                  int top, int baseline, int bottom,
                                  CharSequence text, int start, int end,
                                  boolean first, Layout layout);

    public static class Standard
    implements LeadingMarginSpan
    {
        public Standard(int first, int rest) {
            mFirst = first;
            mRest = rest;
        }

        public Standard(int every) {
            this(every, every);
        }

        public int getLeadingMargin(boolean first) {
            return first ? mFirst : mRest;
        }

        public void drawLeadingMargin(Canvas c, Paint p,
                                      int x, int dir,
                                      int top, int baseline, int bottom,
                                      CharSequence text, int start, int end,
                                      boolean first, Layout layout) {
            ;
        }

        private int mFirst, mRest;
    }
}
