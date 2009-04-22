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

package com.android.launcher;

import android.content.ContentValues;
import com.android.internal.provider.Settings;

import java.util.ArrayList;

/**
 * Represents a folder containing shortcuts or apps.
 */
class UserFolderInfo extends FolderInfo {
    /**
     * The application name.
     */
    CharSequence title;

    /**
     * The apps and shortcuts 
     */
    ArrayList<ApplicationInfo> contents = new ArrayList<ApplicationInfo>();
    
    UserFolderInfo() {
        itemType = Settings.Favorites.ITEM_TYPE_USER_FOLDER;
    }
    
    /**
     * Add an app or shortcut
     * 
     * @param item
     */
    public void add(ApplicationInfo item) {
        contents.add(item);
    }
    
    /**
     * Remove an app or shortcut
     * 
     * @param item
     */
    public void remove(ApplicationInfo item) {
        contents.remove(item);
    }
    
    @Override
    void onAddToDatabase(ContentValues values) { 
        super.onAddToDatabase(values);
        values.put(Settings.Favorites.TITLE, title.toString());
    }
}
