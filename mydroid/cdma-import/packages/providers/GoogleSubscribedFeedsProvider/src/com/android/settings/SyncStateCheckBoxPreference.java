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

package com.android.settings;

import com.android.providers.subscribedfeeds.R;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

public class SyncStateCheckBoxPreference extends CheckBoxPreference {

    private boolean mIsActive = false;
    private boolean mIsPending = false;
    private boolean mFailed = false;

    public SyncStateCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_widget_sync_toggle);
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        ImageView syncActiveView = (ImageView) view.findViewById(R.id.sync_active);
        View syncPendingView = view.findViewById(R.id.sync_pending);
        View syncFailedView = view.findViewById(R.id.sync_failed);

        syncActiveView.setVisibility(mIsActive ? View.VISIBLE : View.GONE);
        AnimationDrawable anim = (AnimationDrawable) syncActiveView.getDrawable();
        boolean showError;
        boolean showPending;
        if (mIsActive) {
            anim.start();
            showPending = false;
            showError = false;
        } else {
            anim.stop();
            if (mIsPending) {
                showPending = true;
                showError = false;
            } else {
                showPending = false;
                showError = mFailed;
            }
        }

        syncFailedView.setVisibility(showError ? View.VISIBLE : View.GONE);
        syncPendingView.setVisibility((showPending && !mIsActive) ? View.VISIBLE : View.GONE);
    }

    /**
     * Set whether the sync is active.
     * @param isActive whether or not the sync is active
     */
    public void setActive(boolean isActive) {
        mIsActive = isActive;
        notifyChanged();
    }

    /**
     * Set whether a sync is pending.
     * @param isPending whether or not the sync is pending
     */
    public void setPending(boolean isPending) {
        mIsPending = isPending;
        notifyChanged();
    }

    /**
     * Set whether the corresponding sync failed.
     * @param failed whether or not the sync failed
     */
    public void setFailed(boolean failed) {
        mFailed = failed;
        notifyChanged();
    }
}
