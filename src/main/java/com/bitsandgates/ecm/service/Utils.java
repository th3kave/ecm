/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.toList;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.bitsandgates.ecm.ProxyFactory;
import com.bitsandgates.ecm.annotation.AfterBranches;
import com.bitsandgates.ecm.annotation.BeforeBranches;
import com.bitsandgates.ecm.annotation.LoopBranch;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.OperationValidationException;
import com.bitsandgates.ecm.domain.Response;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
class Utils {

    static <T> CompletableFuture<List<T>> combineAllFutures(Collection<CompletableFuture<T>> list) {
        return allOf(list.toArray(new CompletableFuture[list.size()]))
                .thenApply(__ -> list.stream().map(CompletableFuture::join).collect(toList()));
    }

    @SneakyThrows
    static Object invoke(Object obj, Method method, Object... args) {
        return method.invoke(obj, args);
    }

    private static void validateBranchMethodSignature(Method method) {
        if (!BranchOutput.class.isAssignableFrom(method.getReturnType())) {
            throw new OperationValidationException(
                    String.format("Invalid [branch] return type [%s] must be assignable to [BranchOutput]",
                            method.getReturnType().getSimpleName()));
        }
        if (method.getParameterCount() != 1) {
            throw new OperationValidationException(
                    String.format("Invalid [branch] parameter count [%d] must be [1]", method.getParameterCount()));
        }
        if (!BranchContext.class.isAssignableFrom(method.getParameters()[0].getType())) {
            throw new OperationValidationException(
                    String.format("Invalid [branch] parameter type [%s] must be assignable to [BranchContext]",
                            method.getParameters()[0].getType().getSimpleName()));
        }
    }

    private static void validateLoopBranchMethodSignature(Method method) {
        if (!BranchOutput.class.isAssignableFrom(method.getReturnType())) {
            throw new OperationValidationException(
                    String.format("Invalid [branch] return type [%s] must be assignable to [BranchOutput]",
                            method.getReturnType().getSimpleName()));
        }
        if (method.getParameterCount() != 2) {
            throw new OperationValidationException(
                    String.format("Invalid [branch] parameter count [%d] must be [1]", method.getParameterCount()));
        }
        if (!BranchContext.class.isAssignableFrom(method.getParameters()[0].getType())) {
            throw new OperationValidationException(
                    String.format("Invalid [branch] parameter type [%s] must be assignable to [BranchContext]",
                            method.getParameters()[0].getType().getSimpleName()));
        }
        if (!int.class.isAssignableFrom(method.getParameters()[1].getType())) {
            throw new OperationValidationException(
                    String.format("Invalid [branch] parameter type [%s] must be assignable to [int]",
                            method.getParameters()[1].getType().getSimpleName()));
        }
    }

    private static void validateBeforeBranchesMethodSignature(Method method) {
        if (!BranchInput.class.isAssignableFrom(method.getReturnType())) {
            throw new OperationValidationException(
                    String.format("Invalid [beforeBranches] return type [%s] must be assignable to [BranchInput]",
                            method.getReturnType().getSimpleName()));
        }
        if (method.getParameterCount() != 1) {
            throw new OperationValidationException(
                    String.format("Invalid [beforeBranches] parameter count [%d] must be [1]", method.getParameterCount()));
        }
        if (!OperationContext.class.isAssignableFrom(method.getParameters()[0].getType())) {
            throw new OperationValidationException(
                    String.format("Invalid [beforeBranches] parameter type [%s] must be assignable to [OperationContext]",
                            method.getParameters()[0].getType().getSimpleName()));
        }
    }

    private static void validateAfterBranchesMethodSignature(Method method) {
        if (!Response.class.isAssignableFrom(method.getReturnType())) {
            throw new OperationValidationException(
                    String.format("Invalid [afterBranches] return type [%s] must be assignable to [Response]",
                            method.getReturnType().getSimpleName()));
        }
        if (method.getParameterCount() != 1) {
            throw new OperationValidationException(
                    String.format("Invalid [afterBranches] parameter count [%d] must be [1]", method.getParameterCount()));
        }
        if (!OperationContext.class.isAssignableFrom(method.getParameters()[0].getType())) {
            throw new OperationValidationException(
                    String.format("Invalid [afterBranches] parameter type [%s] must be assignable to [OperationContext]",
                            method.getParameters()[0].getType().getSimpleName()));
        }
    }

    static Optional<Branch> createBranch(Object obj, Method method, ProxyFactory proxyFactory) {
        com.bitsandgates.ecm.annotation.Branch branch = method.getAnnotation(com.bitsandgates.ecm.annotation.Branch.class);
        if (branch != null) {
            validateBranchMethodSignature(method);
            String branchId = branch.branchId();
            if (branchId.length() == 0) {
                branchId = method.getName();
            }
            List<String> dependencies = asList(branch.dependencies());
            return Optional.of(new Branch(branchId, getObject(proxyFactory, obj), method, dependencies, branch.deterministic()));
        }
        return Optional.empty();
    }

    static Optional<Branch> createLoopBranch(Object obj, Method method, ProxyFactory proxyFactory) {
        LoopBranch branch = method.getAnnotation(LoopBranch.class);
        if (branch != null) {
            validateLoopBranchMethodSignature(method);
            String branchId = branch.branchId();
            if (branchId.length() == 0) {
                branchId = method.getName();
            }
            return Optional.of(new Branch(branchId, getObject(proxyFactory, obj), method, emptyList(), branch.deterministic()));
        }
        return Optional.empty();
    }

    static Function<OperationContext, BranchInput<?>> createBeforeBranches(Object obj, Method method, ProxyFactory proxyFactory) {
        BeforeBranches before = method.getAnnotation(com.bitsandgates.ecm.annotation.BeforeBranches.class);
        if (before != null) {
            validateBeforeBranchesMethodSignature(method);
            return (ctx) -> (BranchInput<?>) invoke(getObject(proxyFactory, obj), method, ctx);
        }
        return null;
    }

    static Function<OperationContext, Response> createAfterBranches(Object obj, Method method, ProxyFactory proxyFactory) {
        AfterBranches after = method.getAnnotation(com.bitsandgates.ecm.annotation.AfterBranches.class);
        if (after != null) {
            validateAfterBranchesMethodSignature(method);
            return (ctx) -> (Response) invoke(getObject(proxyFactory, obj), method, ctx);
        }
        return null;
    }

    private static Object getObject(ProxyFactory proxyFactory, Object obj) {
        return proxyFactory != null ? proxyFactory.proxy(obj) : obj;
    }

    static String traceToSring(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
