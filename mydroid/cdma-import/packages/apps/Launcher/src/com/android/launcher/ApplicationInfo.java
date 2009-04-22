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

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.android.internal.provider.Settings;

/**
 * Represents a launchable application. An application is made of a name (or title),
 * an intent and an icon.
 */
class ApplicationInfo extends ItemInfo {

    /**
     * The application name.
     */
    CharSequence title;

    /**
     * The intent used to start the application.
     */
    Intent intent;

    /**
     * The application icon.
     */
    Drawable icon;

    /**
     * When set to true, indicates that the icon has been resized.
     */
    boolean filtered;

    /**
     * Indicates whether the icon comes from an application's resource (if false)
     * or from a custom Bitmap (if true.)
     */
    boolean customIcon;

    /**
     * If isShortcut=true and customIcon=false, this contains a reference to the
     * shortcut icon as an application's resource.
     */
    Intent.ShortcutIconResource iconResource;

    ApplicationInfo() {
        itemType = Settings.Favorites.ITEM_TYPE_SHORTCUT;
    }
    
    public ApplicationInfo(ApplicationInfo info) {
        super(info);
        title = info.title.toString();
        intent = new Intent(info.intent);
        if (info.iconResource != null) {
            iconResource = new Intent.ShortcutIconResource();
            iconResource.packageName = info.iconResource.packageName;
            iconResource.resourceName = info.iconResource.resourceName;
        }
        icon = info.icon;
        filtered = info.filtered;
        customIcon = info.customIcon;
    }

    /**
     * Creates the application intent based on a component name and various launch flags.
     * Sets {@link #itemType} to {@link Settings.Favorites#ITEM_TYPE_APPLICATION}.
     *
     * @param className the class name of the component representing the intent
     * @param launchFlags the launch flags
     */
    final void setActivity(ComponentName className, int launchFlags) {
        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(className);
        intent.setFlags(launchFlags);
        itemType = Settings.Favorites.ITEM_TYPE_APPLICATION;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);

        String titleStr = title != null ? title.toString() : null;
        values.put(Settings.Favorites.TITLE, titleStr);

        String uri = intent != null ? intent.toURI() : null;
        values.put(Settings.Favorites.INTENT, uri);

        if (customIcon) {
            values.put(Settings.Favorites.ICON_TYPE, Settings.Favorites.ICON_TYPE_BITMAP);
            Bitmap bitmap = ((BitmapDrawable) icon).getBitmap();
            writeBitmap(values, bitmap);
        } else {
            values.put(Settings.Favorites.ICON_TYPE, Settings.Favorites.ICON_TYPE_RESOURCE);
            if (iconResource != null) {
                values.put(Settings.Favorites.ICON_PACKAGE, iconResource.packageName);
                values.put(Settings.Favorites.ICON_RESOURCE, iconResource.resourceName);
            }
        }
    }

    @Override
    public String toString() {
        return title.toString();
    }
}
