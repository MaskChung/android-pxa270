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

/**
 * @author Alexander Y. Kleymenov
 * @version $Revision$
 */

package org.apache.harmony.xnet.provider.jsse;

import java.security.AccessController;
import java.security.Provider;

/**
 * JSSE Provider implementation.
 * 
 * This implementation is based on TLS v 1.0 and SSL v3 protocol specifications.
 * 
 * <ul>
 * <li><a href="http://www.ietf.org/rfc/rfc2246.txt">TLS v 1.0 Protocol
 * specification</a></li>
 * <li><a href="http://wp.netscape.com/eng/ssl3">SSL v3 Protocol
 * specification</a></li>
 * </ul>
 * 
 * Provider implementation supports the following  cipher suites:
 *     TLS_NULL_WITH_NULL_NULL
 *     TLS_RSA_WITH_NULL_MD5
 *     TLS_RSA_WITH_NULL_SHA
 *     TLS_RSA_EXPORT_WITH_RC4_40_MD5
 *     TLS_RSA_WITH_RC4_128_MD5
 *     TLS_RSA_WITH_RC4_128_SHA
 *     TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5
 *     TLS_RSA_WITH_IDEA_CBC_SHA
 *     TLS_RSA_EXPORT_WITH_DES40_CBC_SHA
 *     TLS_RSA_WITH_DES_CBC_SHA
 *     TLS_RSA_WITH_3DES_EDE_CBC_SHA
 *     TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA
 *     TLS_DH_DSS_WITH_DES_CBC_SHA
 *     TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA
 *     TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA
 *     TLS_DH_RSA_WITH_DES_CBC_SHA
 *     TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA
 *     TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA
 *     TLS_DHE_DSS_WITH_DES_CBC_SHA
 *     TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA
 *     TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA
 *     TLS_DHE_RSA_WITH_DES_CBC_SHA
 *     TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA
 *     TLS_DH_anon_EXPORT_WITH_RC4_40_MD5
 *     TLS_DH_anon_WITH_RC4_128_MD5
 *     TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA
 *     TLS_DH_anon_WITH_DES_CBC_SHA
 *     TLS_DH_anon_WITH_3DES_EDE_CBC_SHA
 * 
 * The real set of availible cipher suites depends on set of availible 
 * crypto algorithms. These algorithms must be provided by some crypto
 * provider.
 * 
 * The following cipher algorithms are used by different cipher suites:
 *     IDEA/CBC/NoPadding
 *     RC2/CBC/NoPadding
 *     RC4
 *     DES/CBC/NoPadding
 *     DES/CBC/NoPadding
 *     DESede/CBC/NoPadding
 *  
 * Also the current JSSE provider implementation uses the following 
 * crypto algorithms:
 * 
 * Algorithms that MUST be provided by crypto provider:
 *     Mac    HmacMD5
 *     Mac    HmacSHA1
 *     MessageDigest    MD5
 *     MessageDigest    SHA-1
 *     CertificateFactory    X509
 * 
 * The cipher suites with RSA key exchange may also require:
 *     Cipher    RSA
 *     KeyPairGenerator    RSA
 *     KeyFactory    RSA
 * 
 * The cipher suites with DH key exchange may also require:
 *     Signature    NONEwithDSA
 *     KeyPairGenerator    DiffieHellman or DH
 *     KeyFactory    DiffieHellman or DH
 *     KeyAgreement    DiffieHellman or DH
 *     KeyPairGenerator    DiffieHellman or DH
 * 
 * Trust manager implementation requires:
 *     CertPathValidator    PKIX
 *     CertificateFactory    X509
 * 
 */
public final class JSSEProvider extends Provider {

    public JSSEProvider() {
        super("HarmonyJSSE", 1.0, "Harmony JSSE Provider");
        AccessController.doPrivileged(new java.security.PrivilegedAction<Void>() {
            public Void run() {
                put("SSLContext.TLS",
                        "org.apache.harmony.xnet.provider.jsse.SSLContextImpl");
                put("Alg.Alias.SSLContext.TLSv1", "TLS");
                put("KeyManagerFactory.X509",
                        "org.apache.harmony.xnet.provider.jsse.KeyManagerFactoryImpl");
                put("TrustManagerFactory.X509",
                        "org.apache.harmony.xnet.provider.jsse.TrustManagerFactoryImpl");
                return null;
            }
        });
    }
}

