/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service.example;

import org.springframework.stereotype.Component;

import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.service.Service;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SpringExampleClient {

    private final Service service;

    public Response callService() {
    
        String payload = "payload";
        
        Request request = Request.builder()
                .traceId("123")
                .operatonId(SpringExampleOperation.class.getName())
                .payload(payload)
                .build();

        return service.process(request);
    }
}
