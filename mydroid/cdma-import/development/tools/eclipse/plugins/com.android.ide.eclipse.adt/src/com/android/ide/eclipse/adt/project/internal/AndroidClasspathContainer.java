/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.project.internal;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;

/**
 * Classpath container for the Android projects.
 */
class AndroidClasspathContainer implements IClasspathContainer {
    
    private IClasspathEntry[] mClasspathEntry;
    private IPath mContainerPath;
    
    /**
     * Constructs the container with the {@link IClasspathEntry} representing the android
     * framework jar file and the container id
     * @param entry the entry representing the android framework.
     * @param path the path containing the classpath container id.
     */
    AndroidClasspathContainer(IClasspathEntry entry, IPath path) {
        mClasspathEntry = new IClasspathEntry[] { entry };
        mContainerPath = path;
    }
    
    public IClasspathEntry[] getClasspathEntries() {
        return mClasspathEntry;
    }

    public String getDescription() {
        return "Android Library";
    }

    public int getKind() {
        return IClasspathContainer.K_DEFAULT_SYSTEM;
    }

    public IPath getPath() {
        return mContainerPath;
    }
}
