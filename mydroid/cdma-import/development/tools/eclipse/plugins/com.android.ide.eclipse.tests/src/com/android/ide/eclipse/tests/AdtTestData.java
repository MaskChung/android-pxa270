/*
 * Copyright (C) 2008 The Android Open Source Project
 * 
 * Licensed under the Eclipse Public License, Version 1.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.eclipse.org/org/documents/epl-v10.php
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.ide.eclipse.tests;

import java.io.File;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Helper class for retrieving test data
 * 
 * All tests which need to retrieve test data files should go through this class 
 *
 */
public class AdtTestData {

    /** singleton instance */
    private static AdtTestData sInstance = null;
    private static final Logger sLogger = Logger.getLogger(AdtTestData.class.getName());
    
    /** the absolute file path to the /data directory in this test 
     * environment. 
     */
    private String mOsRootDataPath;
    
   
    private AdtTestData() {
        // can set test_data env variable to override default behavior of 
        // finding data using class loader
        // useful when running in plugin environment, where test data is inside 
        // bundled jar, and must be extracted to temp filesystem location to be 
        // accessed normally
        mOsRootDataPath = System.getProperty("test_data");
        if (mOsRootDataPath == null) {
            sLogger.info("Cannot find test_data directory, init to class loader");
            URL url = this.getClass().getClassLoader().getResource("data");  //$NON-NLS-1$
            mOsRootDataPath = url.getFile();
        }
        if (!mOsRootDataPath.endsWith(File.separator)) {
            sLogger.info("Fixing test_data env variable does not end with path separator");
            mOsRootDataPath = mOsRootDataPath.concat(File.separator);
        }
    }
    
    /** Get the singleton instance of AdtTestData */
    public static AdtTestData getInstance() {
        if (sInstance == null) {
            sInstance = new AdtTestData();
        }
        return sInstance;
    }
    
    /** Returns the absolute file path to a file located in this plugins
     * "data" directory
     * @param osRelativePath - string path to file contained in /data. Must 
     * use path separators appropriate to host OS
     * @return String
     */
    public String getTestFilePath(String osRelativePath) {
        return mOsRootDataPath + osRelativePath;
    }
}
