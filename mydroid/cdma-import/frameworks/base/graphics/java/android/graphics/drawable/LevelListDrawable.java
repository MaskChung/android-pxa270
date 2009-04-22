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

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/**
 * 
 * A resource that contains a number of alternate images, each assigned a maximum numerical value. 
 * Setting the level value of the object with {@link #setLevel(int)} will load the image with the next 
 * greater or equal value assigned to its max attribute. See <a href="{@docRoot}reference/available-resources.html#levellistdrawable">
 * Level List</a> in the Resources topic to learn how to specify this type as an XML resource. A good example use of 
 * a LevelListDrawable would be a battery level indicator icon, with different images to indicate the current
 * battery level.
 *
 */
public class LevelListDrawable extends DrawableContainer {
    public LevelListDrawable()
    {
        this(null);
    }

    public void addLevel(int low, int high, Drawable drawable) {
        if (drawable != null) {
            mLevelListState.addLevel(low, high, drawable);
            // in case the new state matches our current state...
            onLevelChange(getLevel());
        }
    }
    
    // overrides from Drawable

    @Override
    protected boolean onLevelChange(int level) {
        int idx = mLevelListState.indexOfLevel(level);
        if (selectDrawable(idx)) {
            return true;
        }
        return super.onLevelChange(level);
    }
    
    @Override public void inflate(Resources r, XmlPullParser parser,
            AttributeSet attrs)
    throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs);
        
        int type;

        int low = 0;

        final int innerDepth = parser.getDepth()+1;
        int depth;
        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
               && ((depth=parser.getDepth()) >= innerDepth
                       || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            if (depth > innerDepth || !parser.getName().equals("item")) {
                continue;
            }
            
            TypedArray a = r.obtainAttributes(attrs,
                    com.android.internal.R.styleable.LevelListDrawableItem);
            
            low = a.getInt(
                    com.android.internal.R.styleable.LevelListDrawableItem_minLevel, 0);
            int high = a.getInt(
                    com.android.internal.R.styleable.LevelListDrawableItem_maxLevel, 0);
            int drawableRes = a.getResourceId(
                    com.android.internal.R.styleable.LevelListDrawableItem_drawable, 0);
            
            a.recycle();
            
            if (high < 0) {
                throw new XmlPullParserException(parser.getPositionDescription()
                    + ": <item> tag requires a 'maxLevel' attribute");
            }
            
            Drawable dr;
            if (drawableRes != 0) {
                dr = r.getDrawable(drawableRes);
            } else {
                while ((type=parser.next()) == XmlPullParser.TEXT) {
                }
                if (type != XmlPullParser.START_TAG) {
                    throw new XmlPullParserException(
                            parser.getPositionDescription()
                            + ": <item> tag requires a 'drawable' attribute or "
                            + "child tag defining a drawable");
                }
                dr = Drawable.createFromXmlInner(r, parser, attrs);
            }

            mLevelListState.addLevel(low, high, dr);
            low = high+1;
        }

        onLevelChange(getLevel());
    }

    private final static class LevelListState extends DrawableContainerState
    {
        LevelListState(LevelListState orig, LevelListDrawable owner)
        {
            super(orig, owner);

            if (orig != null) {
                mLows = orig.mLows;
                mHighs = orig.mHighs;
            } else {
                mLows = new int[getChildren().length];
                mHighs = new int[getChildren().length];
            }
        }

        public void addLevel(int low, int high, Drawable drawable)
        {
            int pos = addChild(drawable);
            mLows[pos] = low;
            mHighs[pos] = high;
        }

        public int indexOfLevel(int level)
        {
            final int[] lows = mLows;
            final int[] highs = mHighs;
            final int N = getChildCount();
            for (int i=0; i<N; i++) {
                if (level >= lows[i] && level <= highs[i]) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public Drawable newDrawable()
        {
            return new LevelListDrawable(this);
        }

        @Override
        public void growArray(int oldSize, int newSize)
        {
            super.growArray(oldSize, newSize);
            int[] newInts = new int[newSize];
            System.arraycopy(mLows, 0, newInts, 0, oldSize);
            mLows = newInts;
            newInts = new int[newSize];
            System.arraycopy(mHighs, 0, newInts, 0, oldSize);
            mHighs = newInts;
        }

        private int[]   mLows;
        private int[]   mHighs;
    }

    private LevelListDrawable(LevelListState state)
    {
        LevelListState as = new LevelListState(state, this);
        mLevelListState = as;
        setConstantState(as);
        onLevelChange(getLevel());
    }

    private final LevelListState mLevelListState;
}

