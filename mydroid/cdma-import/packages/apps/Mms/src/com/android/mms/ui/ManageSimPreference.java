/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.mms.ui;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

import java.util.Map;

/**
 * The UI spec. calls for managing SMS messages on the SIM to be
 * access through the MMS preferences activity.  This class implements
 * that UI.
 */
public class ManageSimPreference extends Preference {
    private final Context mContext;

    public ManageSimPreference(Context context, AttributeSet attributes) {
        super(context, attributes);
        mContext = context;
    }

    @Override
    protected void onClick() {
        mContext.startActivity(new Intent(mContext, ManageSimMessages.class));
    }
}
