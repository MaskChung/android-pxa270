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

package java.net;

import java.io.IOException;
import java.util.Enumeration;

import org.apache.harmony.luni.net.SocketImplProvider;
import org.apache.harmony.luni.util.Msg;

/**
 * This class models a multicast socket for sending & receiving datagram packets
 * to a multicast group.
 * 
 * @see DatagramSocket
 */
public class MulticastSocket extends DatagramSocket {

    final static int SO_REUSEPORT = 512;

    private InetAddress interfaceSet;

    /**
     * Constructs a multicast socket, bound to any available port on the
     * localhost.
     * 
     * @throws IOException
     *             if a problem occurs creating or binding the socket
     */
    public MulticastSocket() throws IOException {
        super();
        setReuseAddress(true);
    }

    /**
     * Returns a multicast socket, bound to the nominated port on the localhost.
     * 
     * @param aPort
     *            the port to bind on the localhost
     * 
     * @throws IOException
     *             if a problem occurs creating or binding the socket
     */
    public MulticastSocket(int aPort) throws IOException {
        super(aPort);
        setReuseAddress(true);
    }

    /**
     * Answer the network address used by the socket. This is useful on
     * multi-homed machines.
     * 
     * @return java.net.InetAddress the network address
     * @exception java.net.SocketException
     *                The exception thrown while getting the address
     */
    public InetAddress getInterface() throws SocketException {
        checkClosedAndBind(false);
        if (interfaceSet == null) {
            InetAddress ipvXaddress = (InetAddress) impl
                    .getOption(SocketOptions.IP_MULTICAST_IF);
            if (ipvXaddress.isAnyLocalAddress()) {
                // the address was not set at the IPV4 level so check the IPV6
                // level
                NetworkInterface theInterface = getNetworkInterface();
                if (theInterface != null) {
                    Enumeration<InetAddress> addresses = theInterface
                            .getInetAddresses();
                    if (addresses != null) {
                        while (addresses.hasMoreElements()) {
                            InetAddress nextAddress = addresses.nextElement();
                            if (nextAddress instanceof Inet6Address) {
                                return nextAddress;
                            }
                        }
                    }
                }
            }
            return ipvXaddress;
        }
        return interfaceSet;
    }

    /**
     * Answer the network interface used by the socket. This is useful on
     * multi-homed machines.
     * 
     * @return java.net.NetworkInterface the network address
     * @exception java.net.SocketException
     *                The exception thrown while getting the address
     * 
     * @since 1.4
     */
    public NetworkInterface getNetworkInterface() throws SocketException {
        checkClosedAndBind(false);

        // check if it is set at the IPV6 level. If so then use that. Otherwise
        // do it at the IPV4 level
        Integer theIndex = Integer.valueOf(0);
        try {
            theIndex = (Integer) impl.getOption(SocketOptions.IP_MULTICAST_IF2);
        } catch (SocketException e) {
            // we may get an exception if IPV6 is not enabled.
        }

        if (theIndex.intValue() != 0) {
            Enumeration<NetworkInterface> theInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (theInterfaces.hasMoreElements()) {
                NetworkInterface nextInterface = theInterfaces.nextElement();
                if (nextInterface.getIndex() == theIndex.intValue()) {
                    return nextInterface;
                }
            }
        }

        // ok it was not set at the IPV6 level so try at the IPV4 level
        InetAddress theAddress = (InetAddress) impl
                .getOption(SocketOptions.IP_MULTICAST_IF);
        if (theAddress != null) {
            if (!theAddress.isAnyLocalAddress()) {
                return NetworkInterface.getByInetAddress(theAddress);
            }

            // not set as we got the any address so return a dummy network
            // interface with only the any address. We do this to be
            // compatible
            InetAddress theAddresses[] = new InetAddress[1];
            if ((Socket.preferIPv4Stack() == false)
                    && (InetAddress.preferIPv6Addresses() == true)) {
                theAddresses[0] = Inet6Address.ANY;
            } else {
                theAddresses[0] = InetAddress.ANY;
            }
            return new NetworkInterface(null, null, theAddresses,
                    NetworkInterface.UNSET_INTERFACE_INDEX);
        }

        // ok not set at all so return null
        return null;
    }

    /**
     * Answer the time-to-live (TTL) for multicast packets sent on this socket.
     * 
     * @return java.net.InetAddress
     * @exception IOException
     *                The exception description.
     */
    public int getTimeToLive() throws IOException {
        checkClosedAndBind(false);
        return impl.getTimeToLive();
    }

    /**
     * Answer the time-to-live (TTL) for multicast packets sent on this socket.
     * 
     * @return java.net.InetAddress
     * @exception IOException
     *                The exception description.
     * @deprecated Replaced by {@link #getTimeToLive}
     * @see #getTimeToLive()
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public byte getTTL() throws IOException {
        checkClosedAndBind(false);
        return impl.getTTL();
    }

    @Override
    boolean isMulticastSocket() {
        return true;
    }

    /**
     * Add this socket to the multicast group. A socket must joint a group
     * before data may be received. A socket may be a member of multiple groups
     * but may join any group once.
     * 
     * @param groupAddr
     *            the multicast group to be joined
     * @exception IOException
     *                may be thrown while joining a group
     */
    public void joinGroup(InetAddress groupAddr) throws IOException {
        checkClosedAndBind(false);
        if (!groupAddr.isMulticastAddress()) {
            throw new IOException(Msg.getString("K0039")); //$NON-NLS-1$
        }
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(groupAddr);
        }
        impl.join(groupAddr);
    }

    /**
     * Add this socket to the multicast group. A socket must join a group before
     * data may be received. A socket may be a member of multiple groups but may
     * join any group once.
     * 
     * @param groupAddress
     *            the multicast group to be joined
     * @param netInterface
     *            the network interface on which the addresses should be dropped
     * @exception IOException
     *                will be thrown if address is not a multicast address
     * @exception java.lang.SecurityException
     *                will be thrown if caller is not authorized to join group
     * @exception java.lang.IllegalArgumentException
     *                will be through if groupAddr is null
     * 
     * @since 1.4
     */
    public void joinGroup(SocketAddress groupAddress,
            NetworkInterface netInterface) throws IOException {
        checkClosedAndBind(false);
        if (null == groupAddress) {
            throw new IllegalArgumentException(Msg.getString("K0318")); //$NON-NLS-1$
        }

        if ((netInterface != null) && (netInterface.getFirstAddress() == null)) {
            // this is ok if we could set it at the
            throw new SocketException(Msg.getString("K0335")); //$NON-NLS-1$
        }

        if (!(groupAddress instanceof InetSocketAddress)) {
            throw new IllegalArgumentException(Msg.getString(
                    "K0316", groupAddress.getClass())); //$NON-NLS-1$
        }

        InetAddress groupAddr = ((InetSocketAddress) groupAddress).getAddress();

        if (groupAddr == null) {
            throw new SocketException(Msg.getString("K0331")); //$NON-NLS-1$
        }

        if (!groupAddr.isMulticastAddress()) {
            throw new IOException(Msg.getString("K0039")); //$NON-NLS-1$
        }

        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(groupAddr);
        }
        impl.joinGroup(groupAddress, netInterface);
    }

    /**
     * Remove the socket from the multicast group.
     * 
     * @param groupAddr
     *            the multicast group to be left
     * @exception IOException
     *                will be thrown if address is not a multicast address
     * @exception java.lang.SecurityException
     *                will be thrown if caller is not authorized to join group
     * @exception java.lang.IllegalArgumentException
     *                will be through if groupAddr is null
     */
    public void leaveGroup(InetAddress groupAddr) throws IOException {
        checkClosedAndBind(false);
        if (!groupAddr.isMulticastAddress()) {
            throw new IOException(Msg.getString("K003a")); //$NON-NLS-1$
        }
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(groupAddr);
        }
        impl.leave(groupAddr);
    }

    /**
     * Remove the socket from the multicast group.
     * 
     * @param groupAddress
     *            the multicast group to be left
     * @param netInterface
     *            the network interface on which the addresses should be dropped
     * @exception IOException
     *                will be thrown if address is not a multicast address
     * @exception java.lang.SecurityException
     *                will be thrown if caller is not authorized to join group
     * @exception java.lang.IllegalArgumentException
     *                will be through if groupAddr is null
     * 
     * @since 1.4
     */
    public void leaveGroup(SocketAddress groupAddress,
            NetworkInterface netInterface) throws IOException {
        checkClosedAndBind(false);
        if (null == groupAddress) {
            throw new IllegalArgumentException(Msg.getString("K0318")); //$NON-NLS-1$
        }

        if ((netInterface != null) && (netInterface.getFirstAddress() == null)) {
            // this is ok if we could set it at the
            throw new SocketException(Msg.getString("K0335")); //$NON-NLS-1$
        }

        if (!(groupAddress instanceof InetSocketAddress)) {
            throw new IllegalArgumentException(Msg.getString(
                    "K0316", groupAddress.getClass())); //$NON-NLS-1$
        }

        InetAddress groupAddr = ((InetSocketAddress) groupAddress).getAddress();

        if (groupAddr == null) {
            throw new SocketException(Msg.getString("K0331")); //$NON-NLS-1$
        }

        if (!groupAddr.isMulticastAddress()) {
            throw new IOException(Msg.getString("K003a")); //$NON-NLS-1$
        }
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(groupAddr);
        }
        impl.leaveGroup(groupAddress, netInterface);
    }

    /**
     * Send the packet on this socket. The packet must satisfy the security
     * policy before it may be sent.
     * 
     * @param pack
     *            the DatagramPacket to send
     * @param ttl
     *            the TTL setting for this transmission, overriding the socket
     *            default
     * 
     * @exception IOException
     *                If a send error occurs.
     * 
     * @deprecated use {@link #setTimeToLive}
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public void send(DatagramPacket pack, byte ttl) throws IOException {
        checkClosedAndBind(false);
        InetAddress packAddr = pack.getAddress();
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            if (packAddr.isMulticastAddress()) {
                security.checkMulticast(packAddr, ttl);
            } else {
                security.checkConnect(packAddr.getHostName(), pack.getPort());
            }
        }
        int currTTL = getTimeToLive();
        if (packAddr.isMulticastAddress() && (byte) currTTL != ttl) {
            try {
                setTimeToLive(ttl & 0xff);
                impl.send(pack);
            } finally {
                setTimeToLive(currTTL);
            }
        } else {
            impl.send(pack);
        }
    }

    /**
     * Set the network address used by the socket. This is useful on multi-homed
     * machines.
     * 
     * @param addr
     *            java.net.InetAddress the interface network address
     * @exception java.net.SocketException
     *                the exception may be thrown while setting the address
     */
    public void setInterface(InetAddress addr) throws SocketException {
        checkClosedAndBind(false);
        if (addr == null) {
            throw new NullPointerException();
        }
        if (addr.isAnyLocalAddress()) {
            impl.setOption(SocketOptions.IP_MULTICAST_IF, InetAddress.ANY);
        } else if (addr instanceof Inet4Address) {
            impl.setOption(SocketOptions.IP_MULTICAST_IF, addr);
            // keep the address used to do the set as we must return the same
            // value and for IPv6 we may not be able to get it back uniquely
            interfaceSet = addr;
        }

        /*
         * now we should also make sure this works for IPV6 get the network
         * interface for the address and set the interface using its index
         * however if IPV6 is not enabled then we may get an exception. if IPV6
         * is not enabled
         */
        NetworkInterface theInterface = NetworkInterface.getByInetAddress(addr);
        if ((theInterface != null) && (theInterface.getIndex() != 0)) {
            try {
                impl.setOption(SocketOptions.IP_MULTICAST_IF2, Integer
                        .valueOf(theInterface.getIndex()));
            } catch (SocketException e) {
                // Ignored
            }
        } else if (addr.isAnyLocalAddress()) {
            try {
                impl.setOption(SocketOptions.IP_MULTICAST_IF2, Integer
                        .valueOf(0));
            } catch (SocketException e) {
                // Ignored
            }
        } else if (addr instanceof Inet6Address) {
            throw new SocketException(Msg.getString("K0338")); //$NON-NLS-1$
        }
    }

    /**
     * Set the network interface used by the socket. This is useful on
     * multi-homed machines.
     * 
     * @param netInterface
     *            NetworkInterface the interface to be used
     * @exception java.net.SocketException
     *                the exception may be thrown while setting the address
     * 
     * @since 1.4
     */
    public void setNetworkInterface(NetworkInterface netInterface)
            throws SocketException {

        checkClosedAndBind(false);

        if (netInterface == null) {
            // throw a socket exception indicating that we do not support this
            throw new SocketException(Msg.getString("K0334")); //$NON-NLS-1$
        }

        InetAddress firstAddress = netInterface.getFirstAddress();
        if (firstAddress == null) {
            // this is ok if we could set it at the
            throw new SocketException(Msg.getString("K0335")); //$NON-NLS-1$
        }

        if (netInterface.getIndex() == NetworkInterface.UNSET_INTERFACE_INDEX) {
            // set the address using IP_MULTICAST_IF to make sure this
            // works for both IPV4 and IPV6
            impl.setOption(SocketOptions.IP_MULTICAST_IF, InetAddress.ANY);

            try {
                // we have the index so now we pass set the interface
                // using IP_MULTICAST_IF2. This is what is used to set
                // the interface on systems which support IPV6
                impl.setOption(SocketOptions.IP_MULTICAST_IF2, Integer
                        .valueOf(NetworkInterface.NO_INTERFACE_INDEX));
            } catch (SocketException e) {
                // for now just do this, -- could be narrowed?
            }
        }

        /*
         * Now try to set using IPV4 way. However, if interface passed in has no
         * IP addresses associated with it then we cannot do it. first we have
         * to make sure there is an IPV4 address that we can use to call set
         * interface otherwise we will not set it
         */
        Enumeration<InetAddress> theAddresses = netInterface.getInetAddresses();
        boolean found = false;
        firstAddress = null;
        while ((theAddresses.hasMoreElements()) && (found != true)) {
            InetAddress theAddress = theAddresses.nextElement();
            if (theAddress instanceof Inet4Address) {
                firstAddress = theAddress;
                found = true;
            }
        }
        if (netInterface.getIndex() == NetworkInterface.NO_INTERFACE_INDEX) {
            // the system does not support IPV6 and does not provide
            // indexes for the network interfaces. Just pass in the
            // first address for the network interface
            if (firstAddress != null) {
                impl.setOption(SocketOptions.IP_MULTICAST_IF, firstAddress);
            } else {
                /*
                 * we should never get here as there should not be any network
                 * interfaces which have no IPV4 address and which does not have
                 * the network interface index not set correctly
                 */
                throw new SocketException(Msg.getString("K0335")); //$NON-NLS-1$
            }
        } else {
            // set the address using IP_MULTICAST_IF to make sure this
            // works for both IPV4 and IPV6
            if (firstAddress != null) {
                impl.setOption(SocketOptions.IP_MULTICAST_IF, firstAddress);
            }

            try {
                // we have the index so now we pass set the interface
                // using IP_MULTICAST_IF2. This is what is used to set
                // the interface on systems which support IPV6
                impl.setOption(SocketOptions.IP_MULTICAST_IF2, Integer
                        .valueOf(netInterface.getIndex()));
            } catch (SocketException e) {
                // for now just do this -- could be narrowed?
            }
        }

        interfaceSet = null;
    }

    /**
     * Set the time-to-live (TTL) for multicast packets sent on this socket.
     * 
     * @param ttl
     *            the time-to-live, 0<=ttl<= 255
     * @exception IOException
     *                The exception thrown while setting the TTL
     */
    public void setTimeToLive(int ttl) throws IOException {
        checkClosedAndBind(false);
        if (ttl < 0 || ttl > 255) {
            throw new IllegalArgumentException(Msg.getString("K003c")); //$NON-NLS-1$
        }
        impl.setTimeToLive(ttl);
    }

    /**
     * Set the time-to-live (TTL) for multicast packets sent on this socket.
     * 
     * @param ttl
     *            the time-to-live, 0<ttl<= 255
     * @exception IOException
     *                The exception thrown while setting the TTL
     * @deprecated Replaced by {@link #setTimeToLive}
     * @see #setTimeToLive(int)
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public void setTTL(byte ttl) throws IOException {
        checkClosedAndBind(false);
        impl.setTTL(ttl);
    }

    @Override
    synchronized void createSocket(int aPort, InetAddress addr)
            throws SocketException {
        impl = factory != null ? factory.createDatagramSocketImpl()
                : SocketImplProvider.getMulticastSocketImpl();
        impl.create();
        try {
            // the required default options are now set in the VM where they
            // should be
            impl.bind(aPort, addr);
            isBound = true;
        } catch (SocketException e) {
            close();
            throw e;
        }
    }

    /**
     * Constructs a MulticastSocket bound to the host/port specified by the
     * SocketAddress, or an unbound DatagramSocket if the SocketAddress is null.
     * 
     * @param localAddr
     *            the local machine address and port to bind to
     * 
     * @throws IllegalArgumentException
     *             if the SocketAddress is not supported
     * @throws IOException
     *             if a problem occurs creating or binding the socket
     * 
     * @since 1.4
     */
    public MulticastSocket(SocketAddress localAddr) throws IOException {
        super(localAddr);
        setReuseAddress(true);
    }

    /**
     * Get the state of the IP_MULTICAST_LOOP socket option.
     * 
     * @return <code>true</code> if the IP_MULTICAST_LOOP is enabled,
     *         <code>false</code> otherwise.
     * 
     * @throws SocketException
     *             if the socket is closed or the option is invalid.
     * 
     * @since 1.4
     */
    public boolean getLoopbackMode() throws SocketException {
        checkClosedAndBind(false);
        return !((Boolean) impl.getOption(SocketOptions.IP_MULTICAST_LOOP))
                .booleanValue();
    }

    /**
     * Set the IP_MULTICAST_LOOP socket option.
     * 
     * @param loop
     *            the socket IP_MULTICAST_LOOP option setting
     * 
     * @throws SocketException
     *             if the socket is closed or the option is invalid.
     * 
     * @since 1.4
     */
    public void setLoopbackMode(boolean loop) throws SocketException {
        checkClosedAndBind(false);
        impl.setOption(SocketOptions.IP_MULTICAST_LOOP, loop ? Boolean.FALSE
                : Boolean.TRUE);
    }
}
