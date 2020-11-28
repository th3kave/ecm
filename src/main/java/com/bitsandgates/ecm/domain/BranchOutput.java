/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class BranchOutput<T> {

    private String branchId;

    private BranchError error;
    
    private int index;

    private T result;

    public boolean isError() {
        return error != null;
    }
    
    public boolean isRetry() {
        return error != null && error.canRetry();
    }
}
