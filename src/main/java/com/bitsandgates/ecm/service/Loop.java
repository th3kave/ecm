package com.bitsandgates.ecm.service;

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

    private int count;
    
    private int concurrency;

    private int retryCount;

    private Retry retry;

    private BranchInput<?> input;
    
    public String getOperationId() {
        return context.getOperationId();
    }
}
