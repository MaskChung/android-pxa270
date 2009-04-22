/**
 * Copyright (c) 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app;

import android.os.ParcelFileDescriptor;
import android.app.IWallpaperServiceCallback;

/** @hide */
interface IWallpaperService {

    /**
     * Set the wallpaper.
     */
    ParcelFileDescriptor setWallpaper();
    
    /**
     * Get the wallpaper.
     */
    ParcelFileDescriptor getWallpaper(IWallpaperServiceCallback cb);
    
    /**
     * Clear the wallpaper.
     */
    void clearWallpaper();

    /**
     * Sets the dimension hint for the wallpaper. These hints indicate the desired
     * minimum width and height for the wallpaper.
     */
    void setDimensionHints(in int width, in int height);

    /**
     * Returns the desired minimum width for the wallpaper.
     */
    int getWidthHint();

    /**
     * Returns the desired minimum height for the wallpaper.
     */
    int getHeightHint();
}
