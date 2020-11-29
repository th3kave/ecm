/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import java.util.ArrayList;
import java.util.List;

import com.bitsandgates.ecm.domain.BranchOutput;

public class BranchOutputs {

    private List<BranchOutput<?>> outputs = new ArrayList<>();

    boolean isRetry() {
        return outputs.stream().filter(o -> o.isRetry()).findFirst().map(__ -> true).orElse(false);
    }

    void addOutput(BranchOutput<?> output) {
        outputs.add(output);
    }

    List<BranchOutput<?>> getAll() {
        return new ArrayList<>(outputs);
    }
}
