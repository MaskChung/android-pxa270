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

package com.android.ide.eclipse.editors.resources.configurations;

import junit.framework.TestCase;

public class PixelDensityQualifierTest extends TestCase {

    private PixelDensityQualifier pdq;
    private FolderConfiguration config;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pdq = new PixelDensityQualifier();
        config = new FolderConfiguration();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        pdq = null;
        config = null;
    }

    public void testCheckAndSet() {
        assertEquals(true, pdq.checkAndSet("123dpi", config));//$NON-NLS-1$
        assertTrue(config.getPixelDensityQualifier() != null);
        assertEquals(123, config.getPixelDensityQualifier().getValue());
        assertEquals("123dpi", config.getPixelDensityQualifier().toString()); //$NON-NLS-1$
    }

    public void testFailures() {
        assertEquals(false, pdq.checkAndSet("", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("dpi", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("123DPI", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("123", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("sdfdpi", config));//$NON-NLS-1$
    }

}
