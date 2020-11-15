/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service.example;

import java.io.File;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.bitsandgates.ecm.annotation.AfterBranches;
import com.bitsandgates.ecm.annotation.BeforeBranches;
import com.bitsandgates.ecm.annotation.Branch;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.NonRecoverableBranchException;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.service.BranchContext;
import com.bitsandgates.ecm.service.Operation;
import com.bitsandgates.ecm.service.OperationContext;
import com.bitsandgates.ecm.service.Service;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SpringExampleOperation {

    private final Service service;

    @PostConstruct
    void init() {
        Operation.bindToServcie(service, this);
    }

    // Optional - if not provided, default behaviour is as this method
    @BeforeBranches
    public BranchInput<?> before(OperationContext context) {
        return BranchInput.builder().value(context.getRequest().getPayload()).build();
    }

    // Optional - if not provided, default behaviour is as this method
    @AfterBranches
    public Response after(OperationContext context) {
        return context.responseBuilder().payload(context.getBrancheOutputs()).build();
    }

    // This method will run concurrently with branch2()
    @Branch
    public BranchOutput<String> branch1(BranchContext context) {
        BranchInput<String> input = context.getInput();
        // do stuff
        String result = input.getValue() + "/path/to";
        return context.outputBuilder(String.class)
                .result(result)
                .build();
    }

    // This method will run concurrently with branch1()
    @Branch
    public BranchOutput<String> branch2(BranchContext context) {
        // do stuff
        String result = "file.txt";
        return context.outputBuilder(String.class)
                .result(result)
                .build();
    }

    // This method will run after branch1() and branch2() have completed and will
    // have access to output produced by both methods
    @Branch(dependencies = { "branch1", "branch2" })
    public BranchOutput<File> dependent(BranchContext context) {
        BranchOutput<String> branch1Output = context.getDependencyOutput("branch1");
        BranchOutput<String> branch2Output = context.getDependencyOutput("branch2");
        try {
            // do stuff
            return context.outputBuilder(File.class)
                    .result(new File(branch1Output.getResult() + "/" + branch2Output.getResult()))
                    .build();
        } catch (ClassCastException e) {
            // will prevent retry
            throw new NonRecoverableBranchException(e);
        } catch (IllegalStateException e) {
            // will retry
            throw e;
        }
    }
}
