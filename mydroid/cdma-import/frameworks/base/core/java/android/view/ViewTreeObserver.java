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

package android.view;

import java.util.ArrayList;

/**
 * A view tree observer is used to register listeners that can be notified of global
 * changes in the view tree. Such global events include, but are not limited to,
 * layout of the whole tree, beginning of the drawing pass, touch mode change....
 *
 * A ViewTreeObserver should never be instantiated by applications as it is provided
 * by the views hierarchy. Refer to {@link android.view.View#getViewTreeObserver()}
 * for more information.
 */
public final class ViewTreeObserver {
    private ArrayList<OnGlobalFocusChangeListener> mOnGlobalFocusListeners;
    private ArrayList<OnGlobalLayoutListener> mOnGlobalLayoutListeners;
    private ArrayList<OnPreDrawListener> mOnPreDrawListeners;
    private ArrayList<OnTouchModeChangeListener> mOnTouchModeChangeListeners;

    private boolean mAlive = true;

    /**
     * Interface definition for a callback to be invoked when the focus state within
     * the view tree changes.
     */
    public interface OnGlobalFocusChangeListener {
        /**
         * Callback method to be invoked when the focus changes in the view tree. When
         * the view tree transitions from touch mode to non-touch mode, oldFocus is null.
         * When the view tree transitions from non-touch mode to touch mode, newFocus is
         * null. When focus changes in non-touch mode (without transition from or to
         * touch mode) either oldFocus or newFocus can be null.
         *
         * @param oldFocus The previously focused view, if any.
         * @param newFocus The newly focused View, if any.
         */
        public void onGlobalFocusChanged(View oldFocus, View newFocus);
    }

    /**
     * Interface definition for a callback to be invoked when the global layout state
     * or the visibility of views within the view tree changes.
     */
    public interface OnGlobalLayoutListener {
        /**
         * Callback method to be invoked when the global layout state or the visibility of views
         * within the view tree changes
         */
        public void onGlobalLayout();
    }

    /**
     * Interface definition for a callback to be invoked when the view tree is about to be drawn.
     */
    public interface OnPreDrawListener {
        /**
         * Callback method to be invoked when the view tree is about to be drawn. At this point, all
         * views in the tree have been measured and given a frame. Clients can use this to adjust
         * their scroll bounds or even to request a new layout before drawing occurs.
         *
         * @return Return true to proceed with the current drawing pass, or false to cancel.
         *
         * @see android.view.View#onMeasure
         * @see android.view.View#onLayout
         * @see android.view.View#onDraw
         */
        public boolean onPreDraw();
    }

    /**
     * Interface definition for a callback to be invoked when the touch mode changes.
     */
    public interface OnTouchModeChangeListener {
        /**
         * Callback method to be invoked when the touch mode changes.
         *
         * @param isInTouchMode True if the view hierarchy is now in touch mode, false  otherwise.
         */
        public void onTouchModeChanged(boolean isInTouchMode);
    }

    /**
     * Creates a new ViewTreeObserver. This constructor should not be called
     */
    ViewTreeObserver() {
    }

    /**
     * Merges all the listeners registered on the specified observer with the listeners
     * registered on this object. After this method is invoked, the specified observer
     * will return false in {@link #isAlive()} and should not be used anymore.
     *
     * @param observer The ViewTreeObserver whose listeners must be added to this observer
     */
    void merge(ViewTreeObserver observer) {
        if (observer.mOnGlobalFocusListeners != null) {
            if (mOnGlobalFocusListeners != null) {
                mOnGlobalFocusListeners.addAll(observer.mOnGlobalFocusListeners);
            } else {
                mOnGlobalFocusListeners = observer.mOnGlobalFocusListeners;
            }
        }

        if (observer.mOnGlobalLayoutListeners != null) {
            if (mOnGlobalLayoutListeners != null) {
                mOnGlobalLayoutListeners.addAll(observer.mOnGlobalLayoutListeners);
            } else {
                mOnGlobalLayoutListeners = observer.mOnGlobalLayoutListeners;
            }
        }

        if (observer.mOnPreDrawListeners != null) {
            if (mOnPreDrawListeners != null) {
                mOnPreDrawListeners.addAll(observer.mOnPreDrawListeners);
            } else {
                mOnPreDrawListeners = observer.mOnPreDrawListeners;
            }
        }

        if (observer.mOnTouchModeChangeListeners != null) {
            if (mOnTouchModeChangeListeners != null) {
                mOnTouchModeChangeListeners.addAll(observer.mOnTouchModeChangeListeners);
            } else {
                mOnTouchModeChangeListeners = observer.mOnTouchModeChangeListeners;
            }
        }

        observer.kill();
    }

    /**
     * Register a callback to be invoked when the focus state within the view tree changes.
     *
     * @param listener The callback to add
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     */
    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener listener) {
        checkIsAlive();

        if (mOnGlobalFocusListeners == null) {
            mOnGlobalFocusListeners = new ArrayList<OnGlobalFocusChangeListener>();
        }

        mOnGlobalFocusListeners.add(listener);
    }

    /**
     * Remove a previously installed focus change callback.
     *
     * @param victim The callback to remove
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     *
     * @see #addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener)
     */
    public void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener victim) {
        checkIsAlive();
        if (mOnGlobalFocusListeners == null) {
            return;
        }
        mOnGlobalFocusListeners.remove(victim);
    }

    /**
     * Register a callback to be invoked when the global layout state or the visibility of views
     * within the view tree changes
     *
     * @param listener The callback to add
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     */
    public void addOnGlobalLayoutListener(OnGlobalLayoutListener listener) {
        checkIsAlive();

        if (mOnGlobalLayoutListeners == null) {
            mOnGlobalLayoutListeners = new ArrayList<OnGlobalLayoutListener>();
        }

        mOnGlobalLayoutListeners.add(listener);
    }

    /**
     * Remove a previously installed global layout callback
     *
     * @param victim The callback to remove
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     *
     * @see #addOnGlobalLayoutListener(OnGlobalLayoutListener)
     */
    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener victim) {
        checkIsAlive();
        if (mOnGlobalLayoutListeners == null) {
            return;
        }
        mOnGlobalLayoutListeners.remove(victim);
    }

    /**
     * Register a callback to be invoked when the view tree is about to be drawn
     *
     * @param listener The callback to add
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     */
    public void addOnPreDrawListener(OnPreDrawListener listener) {
        checkIsAlive();

        if (mOnPreDrawListeners == null) {
            mOnPreDrawListeners = new ArrayList<OnPreDrawListener>();
        }

        mOnPreDrawListeners.add(listener);
    }

    /**
     * Remove a previously installed pre-draw callback
     *
     * @param victim The callback to remove
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     *
     * @see #addOnPreDrawListener(OnPreDrawListener)
     */
    public void removeOnPreDrawListener(OnPreDrawListener victim) {
        checkIsAlive();
        if (mOnPreDrawListeners == null) {
            return;
        }
        mOnPreDrawListeners.remove(victim);
    }

    /**
     * Register a callback to be invoked when the invoked when the touch mode changes.
     *
     * @param listener The callback to add
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     */
    public void addOnTouchModeChangeListener(OnTouchModeChangeListener listener) {
        checkIsAlive();

        if (mOnTouchModeChangeListeners == null) {
            mOnTouchModeChangeListeners = new ArrayList<OnTouchModeChangeListener>();
        }

        mOnTouchModeChangeListeners.add(listener);
    }

    /**
     * Remove a previously installed touch mode change callback
     *
     * @param victim The callback to remove
     *
     * @throws IllegalStateException If {@link #isAlive()} returns false
     *
     * @see #addOnTouchModeChangeListener(OnTouchModeChangeListener)
     */
    public void removeOnTouchModeChangeListener(OnTouchModeChangeListener victim) {
        checkIsAlive();
        if (mOnTouchModeChangeListeners == null) {
            return;
        }
        mOnTouchModeChangeListeners.remove(victim);
    }

    private void checkIsAlive() {
        if (!mAlive) {
            throw new IllegalStateException("This ViewTreeObserver is not alive, call "
                    + "getViewTreeObserver() again");
        }
    }

    /**
     * Indicates whether this ViewTreeObserver is alive. When an observer is not alive,
     * any call to a method (except this one) will throw an exception.
     *
     * If an application keeps a long-lived reference to this ViewTreeObserver, it should
     * always check for the result of this method before calling any other method.
     *
     * @return True if this object is alive and be used, false otherwise.
     */
    public boolean isAlive() {
        return mAlive;
    }

    /**
     * Marks this ViewTreeObserver as not alive. After invoking this method, invoking
     * any other method but {@link #isAlive()} and {@link #kill()} will throw an Exception.
     *
     * @hide
     */
    private void kill() {
        mAlive = false;
    }

    /**
     * Notifies registered listeners that focus has changed.
     */
    final void dispatchOnGlobalFocusChange(View oldFocus, View newFocus) {
        final ArrayList<OnGlobalFocusChangeListener> globaFocusListeners = mOnGlobalFocusListeners;
        if (globaFocusListeners != null) {
            final int count = globaFocusListeners.size();
            for (int i = count - 1; i >= 0; i--) {
                globaFocusListeners.get(i).onGlobalFocusChanged(oldFocus, newFocus);
            }
        }
    }

    /**
     * Notifies registered listeners that a global layout happened. This can be called
     * manually if you are forcing a layout on a View or a hierarchy of Views that are
     * not attached to a Window or in the GONE state.
     */
    public final void dispatchOnGlobalLayout() {
        final ArrayList<OnGlobalLayoutListener> globaLayoutListeners = mOnGlobalLayoutListeners;
        if (globaLayoutListeners != null) {
            final int count = globaLayoutListeners.size();
            for (int i = count - 1; i >= 0; i--) {
                globaLayoutListeners.get(i).onGlobalLayout();
            }
        }
    }

    /**
     * Notifies registered listeners that the drawing pass is about to start. If a
     * listener returns true, then the drawing pass is canceled and rescheduled. This can
     * be called manually if you are forcing the drawing on a View or a hierarchy of Views
     * that are not attached to a Window or in the GONE state.
     *
     * @return True if the current draw should be canceled and resceduled, false otherwise.
     */
    public final boolean dispatchOnPreDraw() {
        boolean cancelDraw = false;
        final ArrayList<OnPreDrawListener> preDrawListeners = mOnPreDrawListeners;
        if (preDrawListeners != null) {
            final int count = preDrawListeners.size();
            for (int i = count - 1; i >= 0; i--) {
                cancelDraw |= !preDrawListeners.get(i).onPreDraw();
            }
        }
        return cancelDraw;
    }

    /**
     * Notifies registered listeners that the touch mode has changed.
     *
     * @param inTouchMode True if the touch mode is now enabled, false otherwise.
     */
    final void dispatchOnTouchModeChanged(boolean inTouchMode) {
        final ArrayList<OnTouchModeChangeListener> touchModeListeners = mOnTouchModeChangeListeners;
        if (touchModeListeners != null) {
            final int count = touchModeListeners.size();
            for (int i = count - 1; i >= 0; i--) {
                touchModeListeners.get(i).onTouchModeChanged(inTouchMode);
            }
        }
    }
}
