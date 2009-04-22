/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.test;

import android.test.PerformanceTestCase;
import junit.framework.TestCase;

/**
 * {@hide} Not needed for SDK.
 */
public abstract class PerformanceTestBase extends TestCase implements PerformanceTestCase {

    public int startPerformance(PerformanceTestCase.Intermediates intermediates) {
        return 0;
    }

    public boolean isPerformanceOnly() {
        return true;
    }

    /*
     * Temporary hack to get some things working again.
     */
    public void testRun() {
        throw new RuntimeException("test implementation not provided");
    }
}

