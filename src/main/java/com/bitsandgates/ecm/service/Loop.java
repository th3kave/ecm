/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import java.util.Collection;

import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.Retry;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Builder(toBuilder = true)
class Loop {

    private OperationContext context;

    private String branchId;
    
    private int concurrency;

    private int retryCount;

    private Retry retry;

    private BranchInput<?> input;
    
    private Object loopData;
    
    private Collection<?> collection;
    
    public String getOperationId() {
        return context.getOperationId();
    }
    
    public int getCount() {
        return collection.size();
    }
}
