/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteObjectNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMGlobalVariableStoreNode extends LLVMExpressionNode {

    protected final LLVMGlobal descriptor;
    private final LLVMSourceLocation source;

    public LLVMGlobalVariableStoreNode(LLVMGlobal descriptor, LLVMSourceLocation source) {
        this.descriptor = descriptor;
        this.source = source;
    }

    @Specialization
    protected Object doNative(LLVMVirtualAllocationAddress value,
                    @Cached("create()") WriteObjectNode globalAccess) {
        globalAccess.execute(descriptor, value);
        return null;
    }

    @Specialization
    protected Object doNative(LLVMPointer value,
                    @Cached("create()") WriteObjectNode globalAccess) {
        globalAccess.execute(descriptor, value);
        return null;
    }

    @Specialization
    protected Object doNative(LLVMFunctionDescriptor value,
                    @Cached("create()") WriteObjectNode globalAccess) {
        globalAccess.execute(descriptor, value);
        return null;
    }

    @Specialization
    protected Object doNative(LLVMGlobal value,
                    @Cached("create()") WriteObjectNode globalAccess) {
        globalAccess.execute(descriptor, value);
        return null;
    }

    @Specialization
    protected Object doLLVMBoxedPrimitive(LLVMBoxedPrimitive value,
                    @Cached("create()") WriteObjectNode globalAccess) {
        globalAccess.execute(descriptor, value);
        return null;
    }

    @Override
    public LLVMSourceLocation getSourceLocation() {
        return source;
    }
}
