/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.editors.resources.manager.files;

import org.eclipse.core.resources.IFile;

import java.io.File;

/**
 * Base representation of a file system resource.<p/>
 * This somewhat limited interface is designed to let classes use file-system resources, without
 * having the manually handle  {@link IFile} and/or {@link File} manually.
 */
public interface IAbstractResource {

    /**
     * Returns the name of the resource.
     */
    String getName();
}
