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
package com.oracle.truffle.llvm.nodes.intrinsics.rust;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.func.LLVMDispatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMDispatchNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMLookupDispatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMLookupDispatchNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMLangStart extends LLVMIntrinsic {
    @Specialization(guards = "main.asNative() == cachedMain.asNative()")
    @SuppressWarnings("unused")
    protected long doIntrinsic(StackPointer stackPointer, LLVMNativePointer main, long argc, LLVMPointer argv,
                    @Cached("main") LLVMNativePointer cachedMain,
                    @Cached("getMainDescriptor(cachedMain)") LLVMFunctionDescriptor mainDescriptor,
                    @Cached("getDispatchNode(mainDescriptor)") LLVMDispatchNode dispatchNode) {
        dispatchNode.executeDispatch(mainDescriptor, new Object[]{stackPointer});
        return 0;
    }

    @Specialization
    @SuppressWarnings("unused")
    protected long doGeneric(StackPointer stackPointer, LLVMNativePointer main, long argc, LLVMPointer argv,
                    @Cached("getLookupDispatchNode(main)") LLVMLookupDispatchNode dispatchNode) {
        dispatchNode.executeDispatch(main, new Object[]{stackPointer});
        return 0;
    }

    @Specialization(guards = "main == cachedMain")
    @SuppressWarnings("unused")
    protected long doIntrinsic(StackPointer stackPointer, LLVMFunctionDescriptor main, long argc, LLVMPointer argv,
                    @Cached("main") LLVMFunctionDescriptor cachedMain,
                    @Cached("getDispatchNode(main)") LLVMDispatchNode dispatchNode) {
        dispatchNode.executeDispatch(main, new Object[]{stackPointer});
        return 0;
    }

    @Specialization
    @SuppressWarnings("unused")
    protected long doGeneric(StackPointer stackPointer, LLVMFunctionDescriptor main, long argc, LLVMPointer argv,
                    @Cached("getDispatchNode(main)") LLVMDispatchNode dispatchNode) {
        dispatchNode.executeDispatch(main, new Object[]{stackPointer});
        return 0;
    }

    @TruffleBoundary
    protected LLVMFunctionDescriptor getMainDescriptor(LLVMNativePointer main) {
        return getContextReference().get().getFunctionDescriptor(main);
    }

    protected LLVMDispatchNode getDispatchNode(LLVMFunctionDescriptor mainDescriptor) {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMDispatchNodeGen.create(mainDescriptor.getType());
    }

    protected LLVMLookupDispatchNode getLookupDispatchNode(LLVMNativePointer main) {
        CompilerAsserts.neverPartOfCompilation();
        FunctionType functionType = getContextReference().get().getFunctionDescriptor(main).getType();
        return LLVMLookupDispatchNodeGen.create(functionType);
    }
}
