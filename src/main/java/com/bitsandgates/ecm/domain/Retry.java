/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.domain;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Retry {

    private BranchInput<?> branchInput;

    private List<BranchOutput<?>> outputs;
}
