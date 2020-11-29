/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Service {

    @Getter(AccessLevel.PACKAGE)
    private final ExecutorService executorService;

    @Getter(AccessLevel.PACKAGE)
    private final ThrottledExecutorService throttledExecutorService;

    private final int maxTries;

    // Executors.newCachedThreadPool() recommended
    public Service(ExecutorService executorService, int maxTries) {
        this.executorService = executorService;
        this.throttledExecutorService = new ThrottledExecutorService(executorService);
        this.maxTries = maxTries;
    }

    private Map<String, Operation> operations = new HashMap<>();

    void addOperation(Operation operation) {
        if (operations.containsKey(operation.getId())) {
            throw new IllegalArgumentException(String.format("Duplicate operationId [%s]", operation.getId()));
        }
        operations.put(operation.getId(), operation);
    }

    public Response process(Request request) {
        Operation operation = Optional.ofNullable(operations.get(request.getOperatonId())).orElseThrow(IllegalArgumentException::new);
        Response response = null;
        for (int i = 0; i < maxTries; i++) {
            response = operation.execute(new OperationContext(this, request));
            if (response.getRetry() == null) {
                return response;
            }
            request = request.toBuilder().retry(response.getRetry()).retryCount(i + 1).build();
        }
        if (response.getRetry() != null) {
            onErrorAfterRetries(request, response, maxTries);
        }
        return response;
    }

    Response loopBranch(Loop loop) {
        Operation operation = Optional.ofNullable(operations.get(loop.getOperationId())).orElseThrow(IllegalArgumentException::new);
        Response response = null;
        for (int i = 0; i < maxTries; i++) {
            response = operation.loopBranch(loop);
            if (response.getRetry() == null) {
                return response;
            }
            loop = loop.toBuilder().retry(response.getRetry()).retryCount(i + 1).build();
        }
        return response;
    }

    @SneakyThrows
    protected void onErrorAfterRetries(Request request, Response response, int numTries) {
        log.error(String.format("Operation [%s] failed to complete after [%d] tries.\nRequet:\n%s\nResponse:\n%s",
                request.getOperatonId(), numTries, request, response));
    }
}
