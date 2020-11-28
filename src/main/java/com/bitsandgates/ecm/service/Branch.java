/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.NonRecoverableBranchException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class Branch {

    @Getter
    private final String id;

    private final Object impl;

    private final Method exe;

    @Getter
    private final List<String> dependencies;

    @Getter
    private final boolean deterministic;

    @SneakyThrows
    public BranchOutput<?> run(BranchContext context) {
        try {
            return (BranchOutput<?>) exe.invoke(impl, context);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            boolean canRetry = t instanceof NonRecoverableBranchException == false;
            return context.outputBuilder(Void.class, t, canRetry).build();
        }
    }

    @SneakyThrows
    public BranchOutput<?> run(BranchContext context, int index) {
        try {
            return (BranchOutput<?>) exe.invoke(impl, context, index);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            boolean canRetry = t instanceof NonRecoverableBranchException == false;
            return context.outputBuilder(Void.class, t, canRetry).build();
        }
    }
}
