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
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Service {

    @Getter(AccessLevel.PACKAGE)
    private final ExecutorService executorService;

    private final int maxTries;

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
                break;
            }
            request = request.toBuilder().retry(response.getRetry()).retryCount(i + 1).build();
        }
        if (response.getRetry() != null) {
            onErrorAfterRetries(request, response, maxTries);
        }
        return response;
    }

    @SneakyThrows
    protected void onErrorAfterRetries(Request request, Response response, int numTries) {
        log.error(String.format("Operation [%s] failed to complete after [%d] tries.\nRequet:\n%s\nResponse:\n%s",
                request.getOperatonId(), numTries, request, response));
    }
}
