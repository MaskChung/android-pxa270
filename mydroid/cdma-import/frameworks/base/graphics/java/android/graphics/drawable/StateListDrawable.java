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

import java.io.IOException;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.StateSet;

/**
 * 
 * Lets you assign a number of graphic images to a single Drawable and swap out the visible item by a string
 * ID value.
 *
 */
public class StateListDrawable extends DrawableContainer {
    public StateListDrawable()
    {
        this(null);
    }

    /**
     * Add a new image/string ID to the set of images.
     * @param stateSet - An array of resource Ids to associate with the image.
     * Switch to this image by calling setState(). 
     * @param drawable -The image to show.
     */
    public void addState(int[] stateSet, Drawable drawable) {
        if (drawable != null) {
            mStateListState.addStateSet(stateSet, drawable);
            // in case the new state matches our current state...
            onStateChange(getState());
        }
    }

    @Override
    public boolean isStateful() {
        return true;
    }
    
    @Override
    protected boolean onStateChange(int[] stateSet) {
        int idx = mStateListState.indexOfStateSet(stateSet);
        if (idx < 0) {
            idx = mStateListState.indexOfStateSet(StateSet.WILD_CARD);
        }
        if (selectDrawable(idx)) {
            return true;
        }
        return super.onStateChange(stateSet);
    }

    @Override public void inflate(Resources r, XmlPullParser parser,
            AttributeSet attrs)
    throws XmlPullParserException, IOException {
        
        TypedArray a = r.obtainAttributes(attrs,
                com.android.internal.R.styleable.StateListDrawable);

        super.inflateWithAttributes(r, parser, a,
                com.android.internal.R.styleable.StateListDrawable_visible);
        
        mStateListState.setVariablePadding(a.getBoolean(
                com.android.internal.R.styleable.StateListDrawable_variablePadding, false));
        mStateListState.setConstantSize(a.getBoolean(
                com.android.internal.R.styleable.StateListDrawable_constantSize, false));
            
        a.recycle();
        
        int type;

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
            
            int drawableRes = 0;
            
            int i;
            int j = 0;
            final int numAttrs = attrs.getAttributeCount();
            int[] states = new int[numAttrs];
            for (i = 0; i < numAttrs; i++) {
                final int stateResId = attrs.getAttributeNameResource(i);
                if (stateResId == 0) break;
                if (stateResId == com.android.internal.R.attr.drawable) {
                    drawableRes = attrs.getAttributeResourceValue(i, 0);
                } else {
                    states[j++] = attrs.getAttributeBooleanValue(i, false)
                                  ? stateResId
                                  : -stateResId;
                }
            }
            states = StateSet.trimStateSet(states, j);
            
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
            
            mStateListState.addStateSet(states, dr);
        }

        onStateChange(getState());
    }

    StateListState getStateListState() {
        return mStateListState;
    }

    static final class StateListState extends DrawableContainerState
    {
        StateListState(StateListState orig, StateListDrawable owner)
        {
            super(orig, owner);

            if (orig != null) {
                mStateSets = orig.mStateSets;
            } else {
                mStateSets = new int[getChildren().length][];
            }
        }

        int addStateSet(int[] stateSet, Drawable drawable) {
            final int pos = addChild(drawable);
            mStateSets[pos] = stateSet;
            return pos;
        }

        private int indexOfStateSet(int[] stateSet)
        {
            final int[][] stateSets = mStateSets;
            final int N = getChildCount();
            for (int i=0; i<N; i++) {
                if (StateSet.stateSetMatches(stateSets[i], stateSet)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public Drawable newDrawable()
        {
            return new StateListDrawable(this);
        }

        @Override
        public void growArray(int oldSize, int newSize)
        {
            super.growArray(oldSize, newSize);
            final int[][] newStateSets = new int[newSize][];
            System.arraycopy(mStateSets, 0, newStateSets, 0, oldSize);
            mStateSets = newStateSets;
        }

        private int[][]         mStateSets;
    }

    private StateListDrawable(StateListState state)
    {
        StateListState as = new StateListState(state, this);
        mStateListState = as;
        setConstantState(as);
        onStateChange(getState());
    }

    private final StateListState mStateListState;
}

