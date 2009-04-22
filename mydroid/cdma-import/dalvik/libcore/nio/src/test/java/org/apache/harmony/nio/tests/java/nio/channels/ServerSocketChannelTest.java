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

package org.apache.harmony.nio.tests.java.nio.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import junit.framework.TestCase;
import tests.support.Support_PortManager;

/*
 * test for ServerSocketChannel
 */
public class ServerSocketChannelTest extends TestCase {

    private static final int CAPACITY_NORMAL = 200;

    private static final int CAPACITY_64KB = 65536;

    private static final int TIME_UNIT = 200;

    private InetSocketAddress localAddr1;

    private ServerSocketChannel serverChannel;

    private SocketChannel clientChannel;

    protected void setUp() throws Exception {
        super.setUp();
        this.localAddr1 = new InetSocketAddress(
                "127.0.0.1", Support_PortManager 
                        .getNextPort());
        this.serverChannel = ServerSocketChannel.open();
        this.clientChannel = SocketChannel.open();
    }

    protected void tearDown() throws Exception {
        if (null != this.serverChannel) {
            try {
                this.serverChannel.close();
            } catch (Exception e) {
                //ignore
            }

        }
        if (null != this.clientChannel) {
            try {
                this.clientChannel.close();
            } catch (Exception e) {
                //ignore
            }
        }
        super.tearDown();
    }

    // -------------------------------------------------------------------
    // Test for methods in abstract class.
    // -------------------------------------------------------------------

    /*
     * Test method for 'java.nio.channels.ServerSocketChannel.validOps()'
     */
    public void testValidOps() {
        MockServerSocketChannel testMSChnlnull = new MockServerSocketChannel(
                null);
        MockServerSocketChannel testMSChnl = new MockServerSocketChannel(
                SelectorProvider.provider());
        assertEquals(SelectionKey.OP_ACCEPT, this.serverChannel.validOps());
        assertEquals(SelectionKey.OP_ACCEPT, testMSChnl.validOps());
        assertEquals(SelectionKey.OP_ACCEPT, testMSChnlnull.validOps());

    }

    /*
     * Test method for 'java.nio.channels.ServerSocketChannel.open()'
     */
    public void testOpen() {
        MockServerSocketChannel testMSChnl = new MockServerSocketChannel(null);
        MockServerSocketChannel testMSChnlnotnull = new MockServerSocketChannel(
                SelectorProvider.provider());
        assertEquals(SelectionKey.OP_ACCEPT, testMSChnlnotnull.validOps());
        assertNull(testMSChnl.provider());
        assertNotNull(testMSChnlnotnull.provider());
        assertNotNull(this.serverChannel.provider());
        assertEquals(testMSChnlnotnull.provider(), this.serverChannel
                .provider());
    }

    // -------------------------------------------------------------------
    // Test for socket()
    // -------------------------------------------------------------------

    /*
     * Test method for 'com.ibm.io.nio.ServerSocketChannelImpl.socket()'
     */
    public void testSocket_Block_BeforeClose() throws Exception {
        assertTrue(this.serverChannel.isOpen());
        assertTrue(this.serverChannel.isBlocking());
        ServerSocket s1 = this.serverChannel.socket();
        assertFalse(s1.isClosed());
        assertSocketNotAccepted(s1);
        ServerSocket s2 = this.serverChannel.socket();
        // same
        assertSame(s1, s2);

        // socket close makes the channel close
        s1.close();
        assertFalse(this.serverChannel.isOpen());

    }

    public void testSocket_NonBlock_BeforeClose() throws Exception {
        assertTrue(this.serverChannel.isOpen());
        this.serverChannel.configureBlocking(false);
        ServerSocket s1 = this.serverChannel.socket();
        assertFalse(s1.isClosed());
        assertSocketNotAccepted(s1);
        ServerSocket s2 = this.serverChannel.socket();
        // same
        assertSame(s1, s2);

        // socket close makes the channel close
        s1.close();
        assertFalse(this.serverChannel.isOpen());

    }

    public void testSocket_Block_Closed() throws Exception {
        this.serverChannel.close();
        assertFalse(this.serverChannel.isOpen());
        assertTrue(this.serverChannel.isBlocking());
        ServerSocket s1 = this.serverChannel.socket();
        assertTrue(s1.isClosed());
        assertSocketNotAccepted(s1);
        ServerSocket s2 = this.serverChannel.socket();
        // same
        assertSame(s1, s2);
    }

    public void testSocket_NonBlock_Closed() throws Exception {
        this.serverChannel.configureBlocking(false);
        this.serverChannel.close();
        assertFalse(this.serverChannel.isBlocking());
        assertFalse(this.serverChannel.isOpen());
        ServerSocket s1 = this.serverChannel.socket();
        assertTrue(s1.isClosed());
        assertSocketNotAccepted(s1);
        ServerSocket s2 = this.serverChannel.socket();
        // same
        assertSame(s1, s2);
    }

    private void assertSocketNotAccepted(ServerSocket s) throws IOException {
        assertFalse(s.isBound());
        assertNull(s.getInetAddress());
        assertEquals(-1, s.getLocalPort());
        assertNull(s.getLocalSocketAddress());
        assertEquals(0, s.getSoTimeout());
    }

    public void testChannelBasicStatus() {
        ServerSocket gotSocket = this.serverChannel.socket();
        assertFalse(gotSocket.isClosed());
        assertTrue(this.serverChannel.isBlocking());
        assertFalse(this.serverChannel.isRegistered());
        assertEquals(SelectionKey.OP_ACCEPT, this.serverChannel.validOps());
        assertEquals(SelectorProvider.provider(), this.serverChannel.provider());
    }

    // -------------------------------------------------------------------
    // Test for accept()
    // -------------------------------------------------------------------

    /*
     * Test method for 'com.ibm.io.nio.ServerSocketChannelImpl.accept()'
     */

    public void testAccept_Block_NotYetBound() throws IOException {
        assertTrue(this.serverChannel.isOpen());
        assertTrue(this.serverChannel.isBlocking());
        try {
            this.serverChannel.accept();
            fail("Should throw NotYetBoundException"); 
        } catch (NotYetBoundException e) {
            // correct
        }
    }

    public void testAccept_NonBlock_NotYetBound() throws IOException {
        assertTrue(this.serverChannel.isOpen());
        this.serverChannel.configureBlocking(false);
        try {
            this.serverChannel.accept();
            fail("Should throw NotYetBoundException"); 
        } catch (NotYetBoundException e) {
            // correct
        }
    }

    public void testAccept_ClosedChannel() throws Exception {
        this.serverChannel.close();
        assertFalse(this.serverChannel.isOpen());
        try {
            this.serverChannel.accept();
            fail("Should throw ClosedChannelException"); 
        } catch (ClosedChannelException e) {
            // OK.
        }
    }

    public void testAccept_Block_NoConnect() throws IOException {
        assertTrue(this.serverChannel.isBlocking());
        ServerSocket gotSocket = this.serverChannel.socket();
        gotSocket.bind(localAddr1);         
        // blocking mode , will block and wait for ever...
        // so must close the server channel with another thread.
        new Thread() {
            public void run() {
                try {
                    Thread.sleep(TIME_UNIT);
                    ServerSocketChannelTest.this.serverChannel.close();
                } catch (Exception e) {
                    fail("Fail to close the server channel because of"
                            + e.getClass().getName());
                }
            }
        }.start();
        try {
            this.serverChannel.accept();
            fail("Should throw a AsynchronousCloseException");
        } catch (AsynchronousCloseException e) {
            // OK.
        }
    }

    public void testAccept_NonBlock_NoConnect() throws IOException {
        ServerSocket gotSocket = this.serverChannel.socket();
        gotSocket.bind(localAddr1); 
        this.serverChannel.configureBlocking(false);
        // non-blocking mode , will immediately return
        assertNull(this.serverChannel.accept());
    }

    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_read_Blocking_RealData() throws IOException {
        serverChannel.socket().bind(localAddr1);
        ByteBuffer buf = ByteBuffer.allocate(CAPACITY_NORMAL);

        for (int i = 0; i < CAPACITY_NORMAL; i++) {
            buf.put((byte) i);
        }
        clientChannel.connect(localAddr1); 
        Socket serverSocket = serverChannel.accept().socket();
        InputStream in = serverSocket.getInputStream();
        buf.flip();
        clientChannel.write(buf);
        clientChannel.close();
        assertReadResult(in,CAPACITY_NORMAL);
    }
    
    /**
     * Asserts read content. The read content should contain <code>size</code>
     * bytes, and the value should be a sequence from 0 to size-1
     * ([0,1,...size-1]). Otherwise, the method throws Exception.
     * 
     */
    private void assertReadResult(InputStream in, int size) throws IOException{
        byte[] readContent = new byte[size + 1];
        int count = 0;
        int total = 0;
        while ((count = in.read(readContent, total, size + 1 - total)) != -1) {
            total = total + count;
        }
        assertEquals(size, total);
        for (int i = 0; i < size; i++) {
            assertEquals((byte) i, readContent[i]);
        }
    }

    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_read_NonBlocking_RealData() throws Exception {
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(localAddr1);
        ByteBuffer buf = ByteBuffer.allocate(CAPACITY_NORMAL);
        for (int i = 0; i < CAPACITY_NORMAL; i++) {
            buf.put((byte) i);
        }
        buf.flip();
        clientChannel.connect(localAddr1); 
        Socket serverSocket = serverChannel.accept().socket();
        InputStream in = serverSocket.getInputStream();
        clientChannel.write(buf);
        clientChannel.close();
        assertReadResult(in,CAPACITY_NORMAL);
    }
    
    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_write_Blocking_RealData() throws IOException {
        assertTrue(serverChannel.isBlocking());
        ServerSocket serverSocket = serverChannel.socket();
        serverSocket.bind(localAddr1);

        byte[] writeContent = new byte[CAPACITY_NORMAL];
        for (int i = 0; i < writeContent.length; i++) {
            writeContent[i] = (byte) i;
        }       
        clientChannel.connect(localAddr1); 
        Socket socket = serverChannel.accept().socket();
        OutputStream out = socket.getOutputStream();
        out.write(writeContent);
        out.flush();
        socket.close();    
        assertWriteResult(CAPACITY_NORMAL);
    }
    

    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_write_NonBlocking_RealData() throws Exception {
        serverChannel.configureBlocking(false);
        ServerSocket serverSocket = serverChannel.socket();
        serverSocket.bind(localAddr1);

        byte[] writeContent = new byte[CAPACITY_NORMAL];
        for (int i = 0; i < CAPACITY_NORMAL; i++) {
            writeContent[i] = (byte) i;
        }
        clientChannel.connect(localAddr1);
        Socket clientSocket = serverChannel.accept().socket();
        OutputStream out = clientSocket.getOutputStream();
        out.write(writeContent);
        clientSocket.close();  
        assertWriteResult(CAPACITY_NORMAL);
    }
    
    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_read_LByteBuffer_Blocking_ReadWriteRealLargeData()
            throws IOException {
        serverChannel.socket().bind(localAddr1);
        ByteBuffer buf = ByteBuffer.allocate(CAPACITY_64KB);
        for (int i = 0; i < CAPACITY_64KB; i++) {
            buf.put((byte) i);
        }
        buf.flip();
        clientChannel.connect(localAddr1);
        clientChannel.write(buf);
        clientChannel.close();
        Socket socket = serverChannel.accept().socket();
        InputStream in = socket.getInputStream();
        assertReadResult(in,CAPACITY_64KB);
    }

    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_read_LByteBuffer_NonBlocking_ReadWriteRealLargeData()
            throws Exception {
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(localAddr1);
        ByteBuffer buf = ByteBuffer.allocate(CAPACITY_64KB);
        for (int i = 0; i < CAPACITY_64KB; i++) {
            buf.put((byte) i);
        }
        buf.flip();
        clientChannel.connect(localAddr1);
        clientChannel.write(buf);
        clientChannel.close();
        Socket socket = serverChannel.accept().socket();
        InputStream in = socket.getInputStream();
        assertReadResult(in,CAPACITY_64KB);
    }
    
    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_write_LByteBuffer_NonBlocking_ReadWriteRealLargeData()
            throws Exception {
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(localAddr1);
        byte[] writeContent = new byte[CAPACITY_64KB];
        for (int i = 0; i < writeContent.length; i++) {
            writeContent[i] = (byte) i;
        }
        clientChannel.connect(localAddr1); 
        Socket socket = serverChannel.accept().socket();
        OutputStream out = socket.getOutputStream();
        out.write(writeContent);
        socket.close();
        assertWriteResult(CAPACITY_64KB);
    }
    
    /**
     * @tests ServerSocketChannel#accept().socket()
     */
    public void test_write_LByteBuffer_Blocking_ReadWriteRealLargeData()
            throws Exception {
        serverChannel.socket().bind(localAddr1);
        byte[] writeContent = new byte[CAPACITY_64KB];
        for (int i = 0; i < writeContent.length; i++) {
            writeContent[i] = (byte) i;
        }
        clientChannel.connect(localAddr1); 
        Socket socket = serverChannel.accept().socket();
        OutputStream out = socket.getOutputStream();
        out.write(writeContent);
        socket.close();
        assertWriteResult(CAPACITY_64KB);
    }
    
    /**
     * Uses SocketChannel.read(ByteBuffer) to verify write result.
     */
    private void assertWriteResult(int size) throws IOException{
        ByteBuffer buf = ByteBuffer.allocate(size + 1);
        int count = 0;
        int total = 0;
        long beginTime = System.currentTimeMillis();
        while ((count = clientChannel.read(buf)) != -1) {
            total = total + count;
            // 10s timeout to avoid dead loop
            if (System.currentTimeMillis() - beginTime > 10000){
                break;
            }
        }
        assertEquals(total, size);
        buf.flip();
        for (int i = 0; i < count; i++) {
            assertEquals((byte) i, buf.get(i));
        }
    }
    
    /**
     * @tests ServerSocketChannel#socket().getSoTimeout()
     */
    public void test_accept_SOTIMEOUT() throws IOException {
        // regression test for Harmony-707        
        final int SO_TIMEOUT = 10;
        ServerSocketChannel sc = ServerSocketChannel.open();
        try {
            ServerSocket ss = sc.socket();
            ss.bind(localAddr1);
            sc.configureBlocking(false);
            ss.setSoTimeout(SO_TIMEOUT);
            SocketChannel client = sc.accept();
            // non blocking mode, returns null since there are no pending connections.
            assertNull(client);
            int soTimeout = ss.getSoTimeout();
            // Harmony fails here.
            assertEquals(SO_TIMEOUT, soTimeout);
        } finally {
            sc.close();
        }
    }
    
    /**
     * @tests ServerSocket#socket().accept()
     */
    public void test_socket_accept_Blocking_NotBound() throws IOException {
        // regression test for Harmony-748       
        ServerSocket gotSocket = serverChannel.socket();
        serverChannel.configureBlocking(true);
        try {
            gotSocket.accept();
            fail("Should throw an IllegalBlockingModeException");
        } catch (IllegalBlockingModeException e) {
            // expected
        }        
        serverChannel.close();
        try {
            gotSocket.accept();
            fail("Should throw an IllegalBlockingModeException");
        } catch (IllegalBlockingModeException e) {
            // expected
        }     
    }

    /**
     * @tests ServerSocket#socket().accept()
     */
    public void test_socket_accept_Nonblocking_NotBound() throws IOException {
        // regression test for Harmony-748       
        ServerSocket gotSocket = serverChannel.socket();
        serverChannel.configureBlocking(false);
        try {
            gotSocket.accept();
            fail("Should throw an IllegalBlockingModeException");
        } catch (IllegalBlockingModeException e) {
            // expected
        }        
        serverChannel.close();
        try {
            gotSocket.accept();
            fail("Should throw an IllegalBlockingModeException");
        } catch (IllegalBlockingModeException e) {
            // expected
        }     
    }
    
    /**
     * @tests ServerSocket#socket().accept()
     */
    public void test_socket_accept_Nonblocking_Bound() throws IOException {
        // regression test for Harmony-748
        serverChannel.configureBlocking(false);
        ServerSocket gotSocket = serverChannel.socket();
        gotSocket.bind(localAddr1);         
        try {
            gotSocket.accept();
            fail("Should throw an IllegalBlockingModeException");
        } catch (IllegalBlockingModeException e) {
            // expected
        }
        serverChannel.close();
        try {
            gotSocket.accept();
            fail("Should throw a ClosedChannelException");
        } catch (ClosedChannelException e) {
            // expected
        }
    }
    
    /**
     * @tests ServerSocket#socket().accept()
     */
    public void test_socket_accept_Blocking_Bound() throws IOException {
        // regression test for Harmony-748
        serverChannel.configureBlocking(true);
        ServerSocket gotSocket = serverChannel.socket();
        gotSocket.bind(localAddr1);         
        serverChannel.close();
        try {
            gotSocket.accept();
            fail("Should throw a ClosedChannelException");
        } catch (ClosedChannelException e) {
            // expected
        }
    }
}
