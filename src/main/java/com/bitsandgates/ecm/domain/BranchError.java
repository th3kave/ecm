/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
public class BranchError {

    @Getter(AccessLevel.NONE)
    private boolean canRetry;

    private String errorMessage;

    private String errorTrace;
    
    public boolean canRetry() {
        return canRetry;
    }
}
