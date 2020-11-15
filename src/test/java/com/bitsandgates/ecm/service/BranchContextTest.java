/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;

import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
public class BranchContextTest {

    @Mock
    private OperationContext operationContext;

    private CompletableFuture<BranchOutput<?>> dependency = new CompletableFuture<>();

    private static final String branchId = "testBranch";

    private static final BranchInput<Object> input = BranchInput.builder().build();

    private static final BranchOutput<?> output = BranchOutput.builder().branchId(branchId).build();

    @SuppressWarnings("unchecked")
    private BranchContext createContext(CompletableFuture<BranchOutput<?>>... futures) {
        return new BranchContext(branchId, operationContext, asList(futures));
    }

    @SuppressWarnings("unchecked")
    private BranchContext givenContextWithDependencies() {
        return createContext(dependency);
    }

    @SuppressWarnings("unchecked")
    private BranchContext givenContextWithNoDependencies() {
        return createContext();
    }

    @Test
    void given_contextWithNoDependencies_when_waitForDependencies_then_notWaitAndDependencyNotExist() {
        BranchContext context = givenContextWithNoDependencies();
        BranchOutput<?> output = context.waitForDependencies().getDependencyOutput(branchId);
        assertThat(output).isNull();
    }

    @Test
    void given_contextWithDependencies_when_waitForDependencies_then_waitAndDependencyExists() {
        BranchContext context = givenContextWithDependencies();
        long waitTime = 500;
        long start = System.currentTimeMillis();
        Executors.newSingleThreadExecutor().execute(() -> waitAndComplete(dependency, output, waitTime));
        BranchOutput<?> out = context.waitForDependencies().getDependencyOutput(branchId);
        assertThat(System.currentTimeMillis() - start).isGreaterThan(waitTime);
        assertThat(out).isEqualTo(output);
    }

    @Test
    void given_context_when_getInput_then_inputReturned() {
        when(operationContext.getBranchInput()).thenReturn(input);
        BranchContext context = givenContextWithNoDependencies();
        BranchInput<String> in = context.getInput();
        assertThat(in).isEqualTo(input);
    }

    @SneakyThrows
    static void waitAndComplete(CompletableFuture<BranchOutput<?>> dependency, BranchOutput<?> output, long millis) {
        Thread.sleep(millis);
        dependency.complete(output);
    }
}
