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

package javax.crypto.spec;

import java.security.spec.AlgorithmParameterSpec;

import org.apache.harmony.crypto.internal.nls.Messages;

/**
 * @com.intel.drl.spec_ref
 */
public class PBEParameterSpec implements AlgorithmParameterSpec {

    private final byte[] salt;
    private final int iterationCount;

    /**
     * @com.intel.drl.spec_ref
     */
    public PBEParameterSpec(byte[] salt, int iterationCount) {
        if (salt == null) {
            throw new NullPointerException(Messages.getString("crypto.3B")); //$NON-NLS-1$
        }
        this.salt = new byte[salt.length];
        System.arraycopy(salt, 0, this.salt, 0, salt.length);
        this.iterationCount = iterationCount;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public byte[] getSalt() {
        byte[] result = new byte[salt.length];
        System.arraycopy(salt, 0, result, 0, salt.length);
        return result;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public int getIterationCount() {
        return iterationCount;
    }
}

