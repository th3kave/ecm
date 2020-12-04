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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import com.bitsandgates.ecm.ProxyFactory;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.domain.Retry;
import com.bitsandgates.ecm.service.ThrottledExecutorService.Runner;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Operation {

    private static final int MAX_LOOP_CONCURRENCY = 10;

    @Getter
    private final String id;

    private final List<Branch> branches;

    private final Map<String, Branch> loopBranches;

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
        Map<String, Branch> loopBranches = new HashMap<>();
        Function<OperationContext, BranchInput<?>> beforeBranches = null;
        Function<OperationContext, Response> aftertBranches = null;

        for (Method method : clazz.getMethods()) {

            Utils.createBranch(obj, method, proxyFactory).ifPresent(branch -> branches.add(branch));
            Utils.createLoopBranch(obj, method, proxyFactory).ifPresent(branch -> loopBranches.put(branch.getId(), branch));

            if (beforeBranches == null) {
                beforeBranches = Utils.createBeforeBranches(obj, method, proxyFactory);
            }
            if (aftertBranches == null) {
                aftertBranches = Utils.createAfterBranches(obj, method, proxyFactory);
            }
        }
        return new Operation(operationId, branches, loopBranches, beforeBranches, aftertBranches);
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

        ExecutorService executor = context.getService().getExecutorService();

        branches.forEach(branch -> executeBranch(executor, context, branch, results, outputs.get(branch.getId())));

        combineAllFutures(results.values()).get().forEach(output -> context.addBranchOutput(output));

        return getResponse(context);
    }

    @SneakyThrows
    Response loopBranch(Loop loop) {
        OperationContext context = loop.getContext();

        BranchInput<?> input;
        Map<String, BranchOutput<?>> outputs;
        Retry retry = loop.getRetry();
        if (retry != null) {
            input = retry.getBranchInput();
            outputs = getCompletedBranchOutputsForLoop(retry);
        } else {
            input = loop.getInput();
            outputs = emptyMap();
        }

        context.setBranchInput(input);

        Branch branch = loopBranches.get(loop.getBranchId());

        Map<String, CompletableFuture<BranchOutput<?>>> results = initResults(loop.getBranchId(), loop.getCount());

        ThrottledExecutorService executor = context.getService().getThrottledExecutorService();

        Runner runner = executor.newRunner(getConcurrency(loop));

        try {
            Iterator<?> it = loop.getCollection().iterator();
            for (int i = 0; it.hasNext(); i++) {
                executeBranchIteration(runner, context, branch, it.next(), i, results, outputs.get(indexedResultKey(branch.getId(), i)));
            }
        } finally {
            runner.close();
        }
        combineAllFutures(results.values()).get().forEach(output -> context.addBranchOutput(output));

        return getResponse(context);
    }

    void executeBranch(ExecutorService executor, OperationContext context, Branch branch,
            Map<String, CompletableFuture<BranchOutput<?>>> results, BranchOutput<?> output) {
        CompletableFuture<BranchOutput<?>> result = results.get(branch.getId());
        if (output != null && branch.isDeterministic()) {
            result.complete(output);
        } else {
            BranchContext ctx = new BranchContext(branch.getId(), context, 0, extractDependencyResults(branch, results));
            try {
                executor.execute(() -> result.complete(branch.run(ctx.waitForDependencies())));
            } catch (RuntimeException e) {
                result.complete(ctx.outputBuilder(Void.class, e, false).build());
            }
        }
    }

    void executeBranchIteration(Runner runner, OperationContext context, Branch branch, Object element, int index,
            Map<String, CompletableFuture<BranchOutput<?>>> results, BranchOutput<?> output) {
        CompletableFuture<BranchOutput<?>> result = results.get(indexedResultKey(branch.getId(), index));
        if (output != null && branch.isDeterministic()) {
            result.complete(output);
        } else {
            BranchContext ctx = new BranchContext(branch.getId(), context, index, emptyList());
            try {
                runner.run(() -> result.complete(branch.run(ctx, element, index)));
            } catch (InterruptedException e) {
                result.complete(ctx.outputBuilder(Void.class, e, true).build());
            } catch (RuntimeException e) {
                result.complete(ctx.outputBuilder(Void.class, e, false).build());
            }
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

    private static Map<String, BranchOutput<?>> getCompletedBranchOutputsForLoop(Retry retry) {
        return retry.getOutputs().stream().filter(output -> !output.isRetry())
                .collect(toMap(output -> indexedResultKey(output.getBranchId(), output.getIndex()), output -> output));
    }

    private Map<String, CompletableFuture<BranchOutput<?>>> initResults() {
        return branches.stream().collect(toMap(Branch::getId, __ -> new CompletableFuture<>()));
    }

    private static Map<String, CompletableFuture<BranchOutput<?>>> initResults(String branchId, int count) {
        Map<String, CompletableFuture<BranchOutput<?>>> results = new HashMap<>();
        for (int i = 0; i < count; i++) {
            results.put(indexedResultKey(branchId, i), new CompletableFuture<>());
        }
        return results;
    }

    private static String indexedResultKey(String branchId, int index) {
        return branchId + "." + index;
    }

    private static int getConcurrency(Loop loop) {
        int concurrency = loop.getConcurrency();
        if (concurrency == 0 || concurrency > MAX_LOOP_CONCURRENCY) {
            concurrency = MAX_LOOP_CONCURRENCY;
        }
        return concurrency;
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
