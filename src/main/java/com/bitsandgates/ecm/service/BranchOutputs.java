package com.bitsandgates.ecm.service;

import java.util.ArrayList;
import java.util.List;

import com.bitsandgates.ecm.domain.BranchOutput;

public class BranchOutputs {

    private List<BranchOutput<?>> outputs = new ArrayList<>();

    boolean isError() {
        return outputs.stream().filter(o -> o.isError()).findFirst().map(__ -> true).orElse(false);
    }

    boolean isRetry() {
        return outputs.stream().filter(o -> o.isRetry()).findFirst().map(__ -> true).orElse(false);
    }
    
    void addOutput(BranchOutput<?> output) {
        outputs.add(output);
    }
    
    BranchOutput<?> getOutput(int index) {
        return outputs.get(index);
    }
    
    List<BranchOutput<?>> getAll() {
        return new ArrayList<>(outputs);
    }
}
