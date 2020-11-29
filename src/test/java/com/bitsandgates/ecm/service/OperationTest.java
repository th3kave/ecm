/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bitsandgates.ecm.annotation.AfterBranches;
import com.bitsandgates.ecm.annotation.BeforeBranches;
import com.bitsandgates.ecm.annotation.Branch;
import com.bitsandgates.ecm.annotation.LoopBranch;
import com.bitsandgates.ecm.domain.BranchError;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;

@ExtendWith(MockitoExtension.class)
public class OperationTest {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private static final ThrottledExecutorService throttledExecutorService = new ThrottledExecutorService(executorService);

    @Mock
    private Service service;

    @Spy
    private Op op = new Op();

    @Spy
    private OpWithBeforeAfter opWithBeforeAfter = new OpWithBeforeAfter();

    @Spy
    private OpWithDeps opWithDeps = new OpWithDeps();

    @Spy
    private OpWithError opWithError = new OpWithError();

    @Spy
    private OpWithRetryError opWithRetryError = new OpWithRetryError();

    @Spy
    private OpWithNonDeterministicBranch opWithNonDeterministicBranch = new OpWithNonDeterministicBranch();

    @Spy
    private OpWithLoop opWithLoop = new OpWithLoop();

    private static final String traceId = "traceId";

    private static final BranchInput<?> input = BranchInput.builder().build();

    private OperationContext createContext(String operationId) {
        Request request = Request.builder().traceId(traceId).operatonId(operationId).build();
        return createContext(operationId, request);
    }

    private OperationContext createContext(String operationId, Request request) {
        return new OperationContext(service, request);
    }

    @Test
    void given_operation_when_executeRequest_then_branchesCalled() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = Operation.create(op);
        OperationContext context = createContext(Op.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        verify(op, times(1)).branch1(any(BranchContext.class));
        verify(op, times(1)).branch2(any(BranchContext.class));
    }

    @Test
    void given_operationWithError_when_executeRequest_then_responseHasRetry() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = Operation.create(opWithRetryError);
        OperationContext context = createContext(OpWithRetryError.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        assertNotNull(response.getRetry());
    }

    @Test
    void given_operationWithDependencies_when_executeRequest_then_dependentReceivesDependencies() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = Operation.create(opWithDeps);
        OperationContext context = createContext(OpWithDeps.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        verify(opWithDeps, times(1)).dependent(any(BranchContext.class));
        @SuppressWarnings("unchecked")
        BranchOutput<?> output = getBranchOutput((List<BranchOutput<?>>) response.getPayload(), "dependent");
        assertNotNull(output);
        assertThat(output.getResult()).isInstanceOf(List.class);
        assertThat((List<?>) output.getResult()).hasSize(2);
    }

    @Test
    void given_operationWithBeforeAfterMethods_when_executeRequest_then_beforeAfterCalled() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = Operation.create(opWithBeforeAfter);
        OperationContext context = createContext(OpWithBeforeAfter.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        verify(opWithBeforeAfter, times(1)).before(eq(context));
        verify(opWithBeforeAfter, times(1)).after(eq(context));
        assertThat(context.getBrancheOutputs().size()).isEqualTo(2);
    }

    @Test
    void given_operationWithNoBeforeAfterMethods_when_executeRequest_then_defaultBeforeAfterCalled() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = spy(Operation.create(op));
        OperationContext context = createContext(Op.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        verify(operation, times(1)).defaultBeforeBranches(eq(context));
        verify(operation, times(1)).defaultAfterBranches(eq(context));
        assertThat(context.getBrancheOutputs().size()).isEqualTo(2);
    }

    @Test
    void given_requestWithRetry_when_executeRequest_then_onlyFailedBranchesExecuted() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = Operation.create(opWithRetryError);
        OperationContext context = createContext(OpWithRetryError.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        assertNotNull(response.getRetry());

        Request request = Request.builder().retry(response.getRetry()).build();
        context = createContext(OpWithRetryError.class.getName(), request);
        response = operation.execute(context);

        verify(opWithRetryError, times(1)).branch1(any(BranchContext.class));
        verify(opWithRetryError, times(1)).branch2(any(BranchContext.class));
        verify(opWithRetryError, times(2)).fail(any(BranchContext.class));
    }

    @Test
    void given_requestWithErrorNoRetry_when_executeRequest_then_onlyFailedRetryBranchesExecuted() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = Operation.create(opWithError);
        OperationContext context = createContext(OpWithError.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        assertNotNull(response.getRetry());

        Request request = Request.builder().retry(response.getRetry()).build();
        context = createContext(OpWithRetryError.class.getName(), request);
        response = operation.execute(context);

        verify(opWithError, times(1)).branch1(any(BranchContext.class));
        verify(opWithError, times(1)).branch2(any(BranchContext.class));
        verify(opWithError, times(2)).failWithRetry(any(BranchContext.class));
        verify(opWithError, times(1)).failWithoutRetry(any(BranchContext.class));
    }

    @Test
    void given_requestWithRetry_when_executeRequest_then_onlyFailedBranchesAndNonDeterministicExecuted() {
        when(service.getExecutorService()).thenReturn(executorService);

        Operation operation = Operation.create(opWithNonDeterministicBranch);
        OperationContext context = createContext(OpWithNonDeterministicBranch.class.getName());
        Response response = operation.execute(context);

        assertNotNull(response);
        assertNotNull(response.getRetry());

        Request request = Request.builder().retry(response.getRetry()).build();
        context = createContext(OpWithRetryError.class.getName(), request);
        response = operation.execute(context);

        verify(opWithNonDeterministicBranch, times(2)).branch1(any(BranchContext.class));
        verify(opWithNonDeterministicBranch, times(1)).branch2(any(BranchContext.class));
        verify(opWithNonDeterministicBranch, times(2)).fail(any(BranchContext.class));
    }

    @Test
    void given_requestWithLoop_when_executeRequest_then_branchLooped() {
        when(service.getThrottledExecutorService()).thenReturn(throttledExecutorService);
        Operation operation = Operation.create(opWithLoop);
        OperationContext context = createContext(OpWithLoop.class.getName());

        int count = 1000;

        Loop loop = Loop.builder()
                .branchId("branch3")
                .concurrency(5)
                .count(count)
                .context(context)
                .build();
        Response response = operation.loopBranch(loop);

        assertNotNull(response);

        verify(opWithLoop, times(count)).branch3(any(BranchContext.class));
    }

    @Test
    void given_requestWithLoopAndRetry_when_executeRequest_then_onlyFailedBranchesAndNonDeterministicExecuted() throws InterruptedException {
        when(service.getThrottledExecutorService()).thenReturn(throttledExecutorService);
        Operation operation = Operation.create(opWithLoop);
        OperationContext context = createContext(OpWithLoop.class.getName());

        int count = 10;

        Loop loop = Loop.builder()
                .branchId("branch4")
                .concurrency(5)
                .count(count)
                .context(context)
                .build();
        Response response = operation.loopBranch(loop);

        assertNotNull(response);
        assertNotNull(response.getRetry());

        loop = loop.toBuilder().retry(response.getRetry()).build();
        response = operation.loopBranch(loop);

        verify(opWithLoop, times(count + 1)).branch4(any(BranchContext.class));
    }

    static BranchOutput<?> getBranchOutput(List<BranchOutput<?>> outputs, String branchId) {
        for (BranchOutput<?> output : outputs) {
            if (output.getBranchId().equals(branchId)) {
                return output;
            }
        }
        return null;
    }

    static class Op {

        @Branch
        public BranchOutput<?> branch1(BranchContext context) {
            return context.outputBuilder(Object.class).build();
        }

        @Branch
        public BranchOutput<?> branch2(BranchContext context) {
            return context.outputBuilder(Object.class).build();
        }
    }

    static class OpWithBeforeAfter extends Op {

        @BeforeBranches
        public BranchInput<?> before(OperationContext context) {
            return input;
        }

        @AfterBranches
        public Response after(OperationContext context) {
            return context.responseBuilder().payload(context.getBrancheOutputs()).build();
        }
    }

    static class OpWithRetryError extends Op {

        @Branch
        public BranchOutput<?> fail(BranchContext context) {
            return context.outputBuilder(Object.class).error(BranchError.builder().canRetry(true).build()).build();
        }
    }

    static class OpWithError extends Op {

        @Branch
        public BranchOutput<?> failWithRetry(BranchContext context) {
            return context.outputBuilder(Object.class).error(BranchError.builder().canRetry(true).build()).build();
        }

        @Branch
        public BranchOutput<?> failWithoutRetry(BranchContext context) {
            return context.outputBuilder(Object.class).error(BranchError.builder().canRetry(false).build()).build();
        }
    }

    static class OpWithDeps extends Op {

        @Branch(dependencies = { "branch1", "branch2" })
        public BranchOutput<?> dependent(BranchContext context) {
            List<BranchOutput<?>> dependencyResults = new ArrayList<>();
            dependencyResults.add(context.getDependencyOutput("branch1"));
            dependencyResults.add(context.getDependencyOutput("branch2"));
            return context.outputBuilder(Object.class).result(dependencyResults).build();
        }
    }

    static class OpWithNonDeterministicBranch extends OpWithRetryError {

        @Branch(deterministic = false)
        public BranchOutput<?> branch1(BranchContext context) {
            return context.outputBuilder(Object.class).build();
        }
    }

    static class OpWithLoop extends Op {

        @LoopBranch
        public BranchOutput<?> branch3(BranchContext context) {
            return context.outputBuilder(Void.class).build();
        }

        @LoopBranch
        public BranchOutput<?> branch4(BranchContext context) {
            int index = context.getIndex();
            if (context.getRetryCount() == 0 && index == 0) {
                throw new RuntimeException();
            }
            return context.outputBuilder(Void.class).build();
        }
    }
}
