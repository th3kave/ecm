/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;

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
    private Map<String, BranchOutputs> branchOutputs = new HashMap<>();

    private BranchOutputs getOutputs(String branchId) {
        BranchOutputs outputs = branchOutputs.get(branchId);
        if (outputs == null) {
            outputs = new BranchOutputs();
            branchOutputs.put(branchId, outputs);
        }
        return outputs;
    }

    boolean hasRetry() {
        return branchOutputs.values().stream().filter(o -> o.isRetry()).findFirst().map(__ -> true).orElse(false);
    }

    void addBranchOutput(BranchOutput<?> output) {
        getOutputs(output.getBranchId()).addOutput(output);
    }

    public List<BranchOutput<?>> getBrancheOutputs() {
        List<BranchOutput<?>> outputs = new ArrayList<>();
        branchOutputs.values().forEach(o -> outputs.addAll(o.getAll()));
        return outputs;
    }

    @SuppressWarnings("unchecked")
    public <T> BranchInput<T> getBranchInput() {
        return (BranchInput<T>) branchInput;
    }

    public Response.ResponseBuilder responseBuilder() {
        return Response.builder().traceId(request.getTraceId()).operationId(request.getOperatonId());
    }

    Response loopBranch(String branchId, Object loopData, Collection<?> collection, int concurrency) {
        return service.loopBranch(Loop.builder()
                .context(new OperationContext(service, request))
                .branchId(branchId)
                .loopData(loopData)
                .collection(collection)
                .concurrency(concurrency)
                .input(branchInput)
                .build());
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
