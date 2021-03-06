/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceScope;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

@TruffleLanguage.Registration(id = "llvm", name = "llvm", version = "6.0.0", mimeType = {Sulong.LLVM_SULONG_TYPE, Sulong.LLVM_BITCODE_MIME_TYPE, Sulong.LLVM_BITCODE_BASE64_MIME_TYPE,
                Sulong.SULONG_LIBRARY_MIME_TYPE, Sulong.LLVM_ELF_SHARED_MIME_TYPE, Sulong.LLVM_ELF_EXEC_MIME_TYPE}, internal = false, interactive = false)
// TODO: remove Sulong.SULONG_LIBRARY_MIME_TYPE after GR-5904 is closed.
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, DebuggerTags.AlwaysHalt.class})
public final class Sulong extends LLVMLanguage {

    private static final List<Configuration> configurations = new ArrayList<>();

    static {
        configurations.add(new BasicConfiguration());
        for (Configuration f : ServiceLoader.load(Configuration.class)) {
            configurations.add(f);
        }
    }

    @TruffleBoundary
    @Override
    public <E> E getCapability(Class<E> type) {
        String config = findLLVMContext().getEnv().getOptions().get(SulongEngineOption.CONFIGURATION);
        E capability = null;
        for (Configuration c : configurations) {
            if (config.equals(c.getConfigurationName())) {
                capability = c.getCapability(type);
            }
        }
        return capability;
    }

    private LLVMContext mainContext = null;

    @Override
    protected LLVMContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        LLVMContext newContext = new LLVMContext(this, env, getContextExtensions(env), getNodeFactory(env), getLanguageHome());
        if (mainContext == null) {
            mainContext = newContext;
        } else {
            LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION.invalidate();
        }
        return newContext;
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        LLVMMemory memory = getCapability(LLVMMemory.class);
        context.dispose(memory);
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        Source source = request.getSource();
        LLVMContext context = findLLVMContext();
        return new Runner(context, getNodeFactory(context.getEnv())).parse(source);
    }

    @Override
    @SuppressWarnings("deprecation") // for compatibility, will be removed in a future release
    protected Object findExportedSymbol(LLVMContext context, String globalName, boolean onlyExplicit) {
        String atname = globalName.startsWith("@") ? globalName : "@" + globalName; // for interop
        if (context.getGlobalScope().functions().contains(atname)) {
            return context.getGlobalScope().functions().get(atname);
        }
        if (context.getGlobalScope().globals().contains(globalName)) {
            return context.getGlobalScope().globals().get(globalName);
        }
        return null;
    }

    @Override
    protected Iterable<Scope> findTopScopes(LLVMContext context) {
        Scope scope = Scope.newBuilder("llvm-global", context.getGlobalScope()).build();
        return Collections.singleton(scope);
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof LLVMInternalTruffleObject;
    }

    @Override
    public LLVMContext findLLVMContext() {
        return getContextReference().get();
    }

    private List<ContextExtension> getContextExtensions(com.oracle.truffle.api.TruffleLanguage.Env env) {
        String config = env.getOptions().get(SulongEngineOption.CONFIGURATION);
        for (Configuration c : configurations) {
            if (config.equals(c.getConfigurationName())) {
                return c.createContextExtensions(env, this);
            }
        }
        throw new IllegalStateException();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        List<OptionDescriptor> optionDescriptors = new ArrayList<>();
        for (Configuration c : configurations) {
            optionDescriptors.addAll(c.getOptionDescriptors());
        }
        return OptionDescriptors.create(optionDescriptors);
    }

    private NodeFactory getNodeFactory(Env env) {
        String config = env.getOptions().get(SulongEngineOption.CONFIGURATION);
        for (Configuration c : configurations) {
            if (config.equals(c.getConfigurationName())) {
                return c.getNodeFactory(findLLVMContext());
            }
        }
        throw new IllegalStateException();
    }

    @Override
    protected Object findMetaObject(LLVMContext context, Object value) {
        if (value instanceof LLVMDebugObject) {
            return ((LLVMDebugObject) value).getType();
        }

        return super.findMetaObject(context, value);
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @Override
    protected void disposeThread(LLVMContext context, Thread thread) {
        super.disposeThread(context, thread);
        context.getThreadingStack().freeStack(getCapability(LLVMMemory.class), thread);
    }

    @Override
    protected SourceSection findSourceLocation(LLVMContext context, Object value) {
        LLVMSourceLocation location = null;
        if (value instanceof LLVMSourceType) {
            location = ((LLVMSourceType) value).getLocation();
        } else if (value instanceof LLVMDebugObject) {
            location = ((LLVMDebugObject) value).getDeclaration();
        }
        if (location != null) {
            return location.getSourceSection();
        }
        return null;
    }

    @Override
    protected Iterable<Scope> findLocalScopes(LLVMContext context, Node node, Frame frame) {
        if (!context.getEnv().getOptions().get(SulongEngineOption.ENABLE_LVI)) {
            return super.findLocalScopes(context, node, frame);
        } else {
            return LLVMSourceScope.create(node, frame, context);
        }
    }
}
