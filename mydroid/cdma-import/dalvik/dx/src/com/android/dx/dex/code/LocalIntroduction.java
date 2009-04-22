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

package com.android.dx.dex.code;

import com.android.dx.rop.code.RegisterSpec;
import com.android.dx.rop.code.RegisterSpecList;
import com.android.dx.rop.code.SourcePosition;

/**
 * Pseudo-instruction which is used to introduce a new local variable. That
 * is, an instance of this class in an instruction stream indicates that
 * starting with the subsequent instruction, the indicated variable
 * is set.
 */
public final class LocalIntroduction extends ZeroSizeInsn {
    /**
     * non-null; register spec representing the local variable introduced
     * by this instance 
     */
    private final RegisterSpec local;

    /**
     * Returns the local variable listing string for a single register spec.
     * 
     * @param spec non-null; the spec to convert
     * @return non-null; the string form
     */
    public static String localString(RegisterSpec spec) {
        return spec.regString() + ' ' + spec.getLocalItem().toString() + ": " +
            spec.getTypeBearer().toHuman();
    }

    /**
     * Constructs an instance. The output address of this instance is initially
     * unknown (<code>-1</code>).
     * 
     * @param position non-null; source position
     * @param local non-null; register spec representing the local
     * variable introduced by this instance
     */
    public LocalIntroduction(SourcePosition position, RegisterSpec local) {
        super(position);

        if (local == null) {
            throw new NullPointerException("local == null");
        }

        this.local = local;
    }

    /** {@inheritDoc} */
    @Override
    public DalvInsn withRegisterOffset(int delta) {
        return new LocalIntroduction(getPosition(), local.withOffset(delta));
    }

    /** {@inheritDoc} */
    @Override
    public DalvInsn withRegisters(RegisterSpecList registers) {
        return new LocalIntroduction(getPosition(), local);
    }

    /**
     * Gets the register spec representing the local variable introduced
     * by this instance.
     * 
     * @return non-null; the register spec
     */
    public RegisterSpec getLocal() {
        return local;
    }

    /** {@inheritDoc} */
    @Override
    protected String argString() {
        return local.toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String listingString0(boolean noteIndices) {
        return "local-intro " + localString(local);
    }
}
