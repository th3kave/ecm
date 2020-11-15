/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.domain.Retry;

@ExtendWith(MockitoExtension.class)
public class ServiceTest {

    private static final String traceId = "321";

    private static final String operationId = "id";

    @Mock
    private Operation operation;

    private Service service;

    @BeforeEach
    void before() {
        service = spy(new Service(null, 2));
        when(operation.getId()).thenReturn(operationId);
        service.addOperation(operation);
    }

    @Test
    void given_service_when_operationDuplicated_then_exceptionThrown() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.addOperation(operation);
        });

    }

    @Test
    void given_request_when_operationSucceeds_then_responseReturned() {
        Response expected = Response.builder().build();
        when(operation.execute(any(OperationContext.class))).thenReturn(expected);

        Request request = Request.builder().operatonId(operationId).traceId(traceId).build();
        Response response = service.process(request);

        assertThat(response).isEqualTo(expected);
        verify(operation, times(1)).execute(any(OperationContext.class));
    }

    @Test
    void given_request_when_operationHasError_then_retry() {
        Response expected = Response.builder().retry(Retry.builder().build()).build();
        when(operation.execute(any(OperationContext.class))).thenReturn(expected);

        Request request = Request.builder().operatonId(operationId).traceId(traceId).build();
        Response response = service.process(request);

        assertThat(response).isEqualTo(expected);
        verify(operation, times(2)).execute(any(OperationContext.class));
        verify(service, times(1)).onErrorAfterRetries(any(Request.class), any(Response.class), any(Integer.class));
    }
}
