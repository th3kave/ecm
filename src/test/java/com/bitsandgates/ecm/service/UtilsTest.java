/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.bitsandgates.ecm.annotation.AfterBranches;
import com.bitsandgates.ecm.annotation.BeforeBranches;
import com.bitsandgates.ecm.domain.BranchError;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.domain.Retry;

public class UtilsTest {

    @Test
    void given_validBeforeBranchesMethod_when_createBeforeBranches_then_created() throws Exception {
        Object obj = Utils.createBeforeBranches(this, getClass().getMethod("validBeforeBranches", OperationContext.class), null);
        assertThat(obj).isNotNull();
    }

    @Test
    void given_validBeforeBranchesMethodWithExtendedInOut_when_createBeforeBranches_then_created() throws Exception {
        Object obj = Utils.createBeforeBranches(this, getClass().getMethod("validBeforeBranchesExtendedInOut", ExtendedOperationContext.class), null);
        assertThat(obj).isNotNull();
    }

    @Test
    void given_validAfterBranchesMethod_when_createAfterBranches_then_created() throws Exception {
        Object obj = Utils.createAfterBranches(this, getClass().getMethod("validAfterBranches", OperationContext.class), null);
        assertThat(obj).isNotNull();
    }

    @Test
    void given_validAfterBranchesMethodWithExtendedInOut_when_createAfterBranches_then_created() throws Exception {
        Object obj = Utils.createAfterBranches(this, getClass().getMethod("validAfterBranchesExtendedInOut", ExtendedOperationContext.class), null);
        assertThat(obj).isNotNull();
    }

    @Test
    void given_validBranchMethod_when_createBranch_then_created() throws Exception {
        Optional<Branch> branch = Utils.createBranch(this, getClass().getMethod("validBranch", BranchContext.class), null);
        assertThat(branch.isPresent()).isTrue();
    }

    @Test
    void given_validBranchMethodWithExtendedInOut_when_createBranch_then_created() throws Exception {
        Optional<Branch> branch = Utils.createBranch(this, getClass().getMethod("validBranchExtendedInOut", ExtendedBranchContext.class), null);
        assertThat(branch.isPresent()).isTrue();
    }

    // Test methods
    @BeforeBranches
    public BranchInput<?> validBeforeBranches(OperationContext context) {
        return null;
    }

    @BeforeBranches
    public ExtendedBranchInput<?> validBeforeBranchesExtendedInOut(ExtendedOperationContext context) {
        return null;
    }

    @AfterBranches
    public Response validAfterBranches(OperationContext context) {
        return null;
    }

    @AfterBranches
    public ExtendedResponse validAfterBranchesExtendedInOut(ExtendedOperationContext context) {
        return null;
    }

    @com.bitsandgates.ecm.annotation.Branch
    public BranchOutput<?> validBranch(BranchContext context) {
        return null;
    }

    @com.bitsandgates.ecm.annotation.Branch
    public ExtendedBranchOutput<?> validBranchExtendedInOut(ExtendedBranchContext context) {
        return null;
    }

    // Extended types
    public static class ExtendedOperationContext extends OperationContext {

        ExtendedOperationContext(Service service, Request request) {
            super(service, request);
        }
    }

    public static class ExtendedBranchContext extends BranchContext {

        public ExtendedBranchContext(String branchId, OperationContext operationContext,
                List<CompletableFuture<BranchOutput<?>>> dependencyFutures) {
            super(branchId, operationContext, dependencyFutures);
        }
    }

    public static class ExtendedBranchInput<T> extends BranchInput<T> {

        ExtendedBranchInput(T value) {
            super(value);
        }
    }

    public static class ExtendedBranchOutput<T> extends BranchOutput<T> {

        ExtendedBranchOutput(String branchId, BranchError error, T result) {
            super(branchId, error, result);
        }
    }

    public static class ExtendedResponse extends Response {

        protected ExtendedResponse(String traceId, String operationId, Retry retry, Object payload) {
            super(traceId, operationId, retry, payload);
        }
    }
}
