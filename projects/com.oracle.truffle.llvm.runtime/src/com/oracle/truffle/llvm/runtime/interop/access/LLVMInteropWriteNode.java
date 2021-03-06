/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropAccessNode.AccessLocation;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropWriteNodeGen.GetValueSizeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public abstract class LLVMInteropWriteNode extends LLVMNode {

    public static LLVMInteropWriteNode create() {
        return LLVMInteropWriteNodeGen.create();
    }

    @Child Node write = Message.WRITE.createNode();

    public abstract void execute(LLVMInteropType.Structured type, TruffleObject foreign, long offset, Object value);

    @Specialization(guards = "type != null")
    void doKnownType(LLVMInteropType.Structured type, TruffleObject foreign, long offset, Object value,
                    @Cached("create()") LLVMInteropAccessNode access) {
        AccessLocation location = access.execute(type, foreign, offset);
        write(location, value);
    }

    @Child GetValueSizeNode getSize;

    @Fallback
    void doUnknownType(@SuppressWarnings("unused") LLVMInteropType.Structured type, TruffleObject foreign, long offset, Object value) {
        if (getSize == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSize = insert(GetValueSizeNodeGen.create());
        }

        // type unknown: fall back to "array of unknown value type"
        int elementAccessSize = getSize.execute(value);
        AccessLocation location = new AccessLocation(foreign, Long.divideUnsigned(offset, elementAccessSize), null);
        write(location, value);
    }

    private void write(AccessLocation location, Object value) {
        try {
            ForeignAccess.sendWrite(write, location.base, location.identifier, value);
        } catch (InteropException ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex.raise();
        }
    }

    abstract static class GetValueSizeNode extends LLVMNode {

        protected abstract int execute(Object value);

        @Specialization(guards = "valueClass.isInstance(value)")
        int doCached(@SuppressWarnings("unused") Object value,
                        @Cached("value.getClass()") @SuppressWarnings("unused") Class<?> valueClass,
                        @Cached("doGeneric(value)") int cachedSize) {
            return cachedSize;
        }

        @Specialization(replaces = "doCached")
        int doGeneric(Object value) {
            if (value instanceof Byte || value instanceof Boolean) {
                return 1;
            } else if (value instanceof Short || value instanceof Character) {
                return 2;
            } else if (value instanceof Integer || value instanceof Float) {
                return 4;
            } else {
                return 8;
            }
        }
    }
}
