/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import static com.bitsandgates.ecm.service.Utils.combineAllFutures;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.bitsandgates.ecm.domain.BranchError;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@Getter
@RequiredArgsConstructor
public class BranchContext {

    private final String branchId;

    private final OperationContext operationContext;

    @Getter(AccessLevel.NONE)
    private final int index;
    
    @Getter(AccessLevel.NONE)
    private final List<CompletableFuture<BranchOutput<?>>> dependencyFutures;

    @Getter(AccessLevel.NONE)
    private Map<String, BranchOutput<?>> dependencies = new HashMap<>();
    
    public String getTraceId() {
        return operationContext.getTraceId();
    }

    public <T> BranchInput<T> getInput() {
        return operationContext.getBranchInput();
    }

    public <T> BranchOutput.BranchOutputBuilder<T> outputBuilder(Class<T> clazz) {
        return BranchOutput.<T>builder().branchId(branchId).index(index);
    }

    public <T> BranchOutput.BranchOutputBuilder<T> outputBuilder(Class<T> clazz, Throwable e, boolean canRetry) {
        return BranchOutput.<T>builder()
                .branchId(branchId)
                .index(index)
                .error(BranchError.builder()
                        .errorMessage(e.getMessage())
                        .errorTrace(Utils.traceToSring(e))
                        .canRetry(canRetry)
                        .build());
    }

    public <T> BranchOutput.BranchOutputBuilder<T> outputBuilderForDependencyError(Class<T> clazz, BranchOutput<?> outputWithError) {
        if (!outputWithError.isError()) {
            throw new IllegalArgumentException(String.format("Output does not contain error [%s]", outputWithError));
        }
        BranchError error = outputWithError.getError();
        return BranchOutput.<T>builder()
                .branchId(branchId)
                .index(index)
                .error(BranchError.builder()
                        .errorMessage(String.format("A branch this branch dependens on [%s] did not complete because of [%s]",
                                outputWithError.getBranchId(), error))
                        .canRetry(error.canRetry())
                        .build());
    }

    @SneakyThrows
    BranchContext waitForDependencies() {
        if (!dependencyFutures.isEmpty()) {
            combineAllFutures(dependencyFutures).get().forEach(output -> dependencies.put(output.getBranchId(), output));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> BranchOutput<T> getDependencyOutput(String branchId) {
        return (BranchOutput<T>) dependencies.get(branchId);
    }

    public Map<String, BranchOutput<?>> getDependencyErrors() {
        return dependencies.values().stream().filter(d -> d.isError()).collect(toMap(BranchOutput::getBranchId, o -> o));
    }

    public Map<String, BranchOutput<?>> getDependencyRetries() {
        return dependencies.values().stream().filter(d -> d.isRetry()).collect(toMap(BranchOutput::getBranchId, o -> o));
    }

    public boolean hasDependencyError() {
        return dependencies.values().stream().filter(d -> d.isError()).findFirst().map(__ -> true).orElse(false);
    }

    public boolean hasDependencyRetry() {
        return dependencies.values().stream().filter(d -> d.isRetry()).findFirst().map(__ -> true).orElse(false);
    }

    public int getRetryCount() {
        return operationContext.getRetryCount();
    }

    public Response loopBranch(String branchId, Collection<?> collection) {
        return loopBranch(branchId, collection, 0);
    }

    public Response loopBranch(String branchId, Collection<?> collection, int concurrency) {
        return operationContext.loopBranch(branchId, collection, concurrency);
    }
}
