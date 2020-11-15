/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.domain.Retry;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class OperationContext {

    private final Service service;

    private final Request request;

    @Setter(AccessLevel.PACKAGE)
    private BranchInput<?> branchInput;

    @Getter(AccessLevel.NONE)
    private Map<String, BranchOutput<?>> branchOutputs = new HashMap<>();

    boolean hasError() {
        return branchOutputs.values().stream().filter(o -> o.isError()).findFirst().map(__ -> true).orElse(false);
    }

    boolean hasRetry() {
        return branchOutputs.values().stream().filter(o -> o.isRetry()).findFirst().map(__ -> true).orElse(false);
    }

    Retry getRetry() {
        return request.getRetry();
    }

    void addBranchOutput(BranchOutput<?> output) {
        branchOutputs.put(output.getBranchId(), output);
    }

    @SuppressWarnings("unchecked")
    public <T> BranchOutput<T> getBranchOutput(String branchId) {
        return (BranchOutput<T>) branchOutputs.get(branchId);
    }

    public List<BranchOutput<?>> getBrancheOutputs() {
        return new ArrayList<>(branchOutputs.values());
    }

    @SuppressWarnings("unchecked")
    public <T> BranchInput<T> getBranchInput() {
        return (BranchInput<T>) branchInput;
    }

    public Response.ResponseBuilder responseBuilder() {
        return Response.builder().traceId(request.getTraceId()).operationId(request.getOperatonId());
    }
    
    public String getTraceId() {
        return request.getTraceId();
    }
    
    public String getOperationId() {
        return request.getOperatonId();
    }

    public int getRetryCount() {
        return request.getRetryCount();
    }
}
