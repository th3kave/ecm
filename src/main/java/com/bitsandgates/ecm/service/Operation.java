/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import static com.bitsandgates.ecm.service.Utils.combineAllFutures;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.bitsandgates.ecm.ProxyFactory;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.domain.Retry;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Operation {

    @Getter
    private final String id;

    private final List<Branch> branches;

    private final Function<OperationContext, BranchInput<?>> beforeBranches;

    private final Function<OperationContext, Response> afterBranches;
    
    public static void bindToServcie(Service service, Object obj) {
        service.addOperation(create(obj));
    }

    public static void bindToServcie(Service service, Object obj, ProxyFactory proxyFactory) {
        service.addOperation(create(obj, proxyFactory));
    }
    
    static Operation create(Object obj) {
        return create(obj, null);
    }

    static Operation create(Object obj, ProxyFactory proxyFactory) {
        Class<?> clazz = obj.getClass();
        String operationId = clazz.getName();
        List<Branch> branches = new ArrayList<>();
        Function<OperationContext, BranchInput<?>> beforeBranches = null;
        Function<OperationContext, Response> aftertBranches = null;

        for (Method method : clazz.getMethods()) {

            Utils.createBranch(obj, method, proxyFactory).ifPresent(branch -> branches.add(branch));

            if (beforeBranches == null) {
                beforeBranches = Utils.createBeforeBranches(obj, method, proxyFactory);
            }
            if (aftertBranches == null) {
                aftertBranches = Utils.createAfterBranches(obj, method, proxyFactory);
            }
        }
        return new Operation(operationId, branches, beforeBranches, aftertBranches);
    }

    BranchInput<?> defaultBeforeBranches(OperationContext context) {
        return BranchInput.builder().value(context.getRequest().getPayload()).build();
    }

    Response defaultAfterBranches(OperationContext context) {
        return context.responseBuilder().payload(context.getBrancheOutputs()).build();
    }

    private Function<OperationContext, BranchInput<?>> getBeforeBranches() {
        return beforeBranches != null ? beforeBranches : this::defaultBeforeBranches;
    };

    private Function<OperationContext, Response> getAfterBranches() {
        return afterBranches != null ? afterBranches : this::defaultAfterBranches;
    };

    @SneakyThrows
    public Response execute(OperationContext context) {

        Request request = context.getRequest();

        BranchInput<?> input;
        Map<String, BranchOutput<?>> outputs;

        Retry retry = request.getRetry();
        if (retry != null) {
            input = retry.getBranchInput();
            outputs = getCompletedBranchOutputs(retry);
        } else {
            input = getBeforeBranches().apply(context);
            outputs = emptyMap();
        }

        context.setBranchInput(input);

        Map<String, CompletableFuture<BranchOutput<?>>> results = initResults();

        branches.forEach(branch -> executeBranch(context, branch, results, outputs.get(branch.getId())));

        combineAllFutures(results.values()).get().forEach(output -> context.addBranchOutput(output));

        return getResponse(context);
    }

    void executeBranch(OperationContext context, Branch branch, Map<String, CompletableFuture<BranchOutput<?>>> results,
            BranchOutput<?> output) {
        CompletableFuture<BranchOutput<?>> result = results.get(branch.getId());
        if (output != null && branch.isDeterministic()) {
            result.complete(output);
        } else {
            BranchContext ctx = new BranchContext(branch.getId(), context, extractDependencyResults(branch, results));
            context.getService().getExecutorService().execute(() -> result.complete(branch.run(ctx.waitForDependencies())));
        }
    }

    private static List<CompletableFuture<BranchOutput<?>>> extractDependencyResults(Branch branch,
            Map<String, CompletableFuture<BranchOutput<?>>> results) {
        return Optional.ofNullable(branch.getDependencies())
                .map(deps -> deps.stream().map(branchId -> results.get(branchId)).collect(toList()))
                .orElse(emptyList());
    }

    private static Map<String, BranchOutput<?>> getCompletedBranchOutputs(Retry retry) {
        return retry.getOutputs().stream().filter(output -> !output.isRetry()).collect(toMap(BranchOutput::getBranchId, output -> output));
    }

    private Map<String, CompletableFuture<BranchOutput<?>>> initResults() {
        return branches.stream().collect(toMap(Branch::getId, __ -> new CompletableFuture<>()));
    }

    private Response getResponse(OperationContext ctx) {
        if (ctx.hasRetry()) {
            return ctx.responseBuilder()
                    .retry(Retry.builder()
                            .branchInput(ctx.getBranchInput())
                            .outputs(ctx.getBrancheOutputs())
                            .build())
                    .build();
        }
        return getAfterBranches().apply(ctx);
    }
}
