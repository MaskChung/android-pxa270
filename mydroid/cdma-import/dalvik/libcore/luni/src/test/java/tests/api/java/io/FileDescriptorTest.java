/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package tests.api.java.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

public class FileDescriptorTest extends junit.framework.TestCase {

	private static String platformId = "JDK"
			+ System.getProperty("java.vm.version").replace('.', '-');

	FileOutputStream fos;

	BufferedOutputStream os;

	FileInputStream fis;

	File f;

	/**
	 * @tests java.io.FileDescriptor#FileDescriptor()
	 */
	public void test_Constructor() {
		// Test for method java.io.FileDescriptor()
		FileDescriptor fd = new FileDescriptor();
		assertTrue("Failed to create FileDescriptor",
				fd instanceof FileDescriptor);
	}

	/**
	 * @tests java.io.FileDescriptor#sync()
	 */
       public void test_sync() throws Exception {
		// Test for method void java.io.FileDescriptor.sync()
        f = new File(System.getProperty("user.dir"), "fd" + platformId + ".tst");
        f.delete();
        fos = new FileOutputStream(f.getPath());
        fos.write("Test String".getBytes());
        fis = new FileInputStream(f.getPath());
        FileDescriptor fd = fos.getFD();
        fd.sync();
        int length = "Test String".length();
        assertEquals("Bytes were not written after sync", length, fis
                .available());
        
        // Regression test for Harmony-1494
        fd = fis.getFD();
        fd.sync();
        assertEquals("Bytes were not written after sync", length, fis
                .available());
        
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        fd = raf.getFD(); 
        fd.sync();
        raf.close();
	}

	/**
	 * @tests java.io.FileDescriptor#valid()
	 */
	public void test_valid() {
		// Test for method boolean java.io.FileDescriptor.valid()
		try {
			f = new File(System.getProperty("user.dir"), "fd.tst");
			f.delete();
			os = new BufferedOutputStream(fos = new FileOutputStream(f
					.getPath()), 4096);
			FileDescriptor fd = fos.getFD();
			assertTrue("Valid fd returned false", fd.valid());
			os.close();
			assertTrue("Invalid fd returned true", !fd.valid());
		} catch (Exception e) {
			fail("Exception during test : " + e.getMessage());
		}

	}

	/**
	 * Sets up the fixture, for example, open a network connection. This method
	 * is called before a test is executed.
	 */
	protected void setUp() {
	}

	/**
	 * Tears down the fixture, for example, close a network connection. This
	 * method is called after a test is executed.
	 */
	protected void tearDown() {
		try {
			os.close();
		} catch (Exception e) {
		}
		try {
			fis.close();
		} catch (Exception e) {
		}
		try {
			fos.close();
		} catch (Exception e) {
		}
		try {
			f.delete();
		} catch (Exception e) {
		}
	}

	protected void doneSuite() {
	}
}
