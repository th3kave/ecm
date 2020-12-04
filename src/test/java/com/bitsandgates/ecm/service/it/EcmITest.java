/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import com.bitsandgates.ecm.annotation.Branch;
import com.bitsandgates.ecm.annotation.LoopBranch;
import com.bitsandgates.ecm.domain.BranchInput;
import com.bitsandgates.ecm.domain.BranchOutput;
import com.bitsandgates.ecm.domain.Request;
import com.bitsandgates.ecm.domain.Response;
import com.bitsandgates.ecm.domain.Retry;
import com.bitsandgates.ecm.service.BranchContext;
import com.bitsandgates.ecm.service.Operation;
import com.bitsandgates.ecm.service.Service;

import lombok.RequiredArgsConstructor;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
public class EcmITest {

    private static final String testPayload = "testPayload";

    @Autowired
    private TestService service;

    @Test
    void normalFlow() {
        Request request = Request.builder()
                .traceId("123")
                .operatonId(Op.class.getName())
                .payload(testPayload)
                .build();
        Response response = service.process(request);
        @SuppressWarnings("unchecked")
        List<BranchOutput<?>> payload = (List<BranchOutput<?>>) response.getPayload();
        assertThat(payload).hasSize(2);
        assertThat(payload.get(0).getResult()).isEqualTo(testPayload);
        assertThat(payload.get(1).getResult()).isEqualTo(testPayload);
    }

    @Test
    void errorFlowWithRetrySuccess() {
        Request request = Request.builder()
                .traceId("123")
                .operatonId(OpWithError.class.getName())
                .payload(testPayload)
                .build();
        Response response = service.process(request);
        @SuppressWarnings("unchecked")
        List<BranchOutput<?>> payload = (List<BranchOutput<?>>) response.getPayload();
        assertThat(payload).hasSize(3);
        assertThat(payload.get(0).getResult()).isEqualTo(testPayload);
        assertThat(payload.get(1).getResult()).isEqualTo(testPayload);
        assertThat(payload.get(2).getResult()).isEqualTo(testPayload);
    }

    @Test
    void errorFlowWithRetryFail() {
        Request request = Request.builder()
                .traceId("123")
                .operatonId(OpWithErrorAfterRetry.class.getName())
                .payload(testPayload)
                .build();
        Response response = service.process(request);
        Retry retry = response.getRetry();
        assertThat(retry).isNotNull();
    }

    @Test
    void normalFlowWithTransactionManagement() {
        Request request = Request.builder()
                .traceId("123")
                .operatonId(OpTx.class.getName())
                .payload(testPayload)
                .build();
        Response response = service.process(request);
        List<BranchOutput<?>> payload = response.getRetry().getOutputs();
        assertThat(payload).hasSize(4);
        assertThat(payload.stream().filter(e -> e.getBranchId().equals("successBranch")).findFirst().map(o -> o.getResult()).get())
                .isEqualTo(11);
    }

    @Component
    static class TestService extends Service {

        public TestService(ExecutorService executorService) {
            super(executorService, 2);
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Op {

        private final Service service;

        @PostConstruct
        void init() {
            Operation.bindToServcie(service, this);
        }

        @Branch
        public BranchOutput<String> branch1(BranchContext context) {
            BranchInput<String> in = context.getInput();
            return context.outputBuilder(String.class).result(in.getValue()).build();
        }

        @Branch(dependencies = "branch1")
        public BranchOutput<String> successBranch(BranchContext context) {
            BranchInput<String> in = context.getInput();
            return context.outputBuilder(String.class).result(in.getValue()).build();
        }
    }

    @Component
    public static class OpWithError extends Op {

        private final Service service;

        public OpWithError(Service service) {
            super(service);
            this.service = service;
        }

        @PostConstruct
        void init() {
            Operation.bindToServcie(service, this);
        }

        @Branch(dependencies = "branch1")
        public BranchOutput<String> errorFirstSuccessOnRetryBranch(BranchContext context) {
            if (context.getRetryCount() == 0) {
                return context.outputBuilder(String.class, new Exception("Test error"), true).build();
            }
            BranchInput<String> in = context.getInput();
            return context.outputBuilder(String.class).result(in.getValue()).build();
        }
    }

    @Component
    public static class OpWithErrorAfterRetry extends Op {

        private final Service service;

        public OpWithErrorAfterRetry(Service service) {
            super(service);
            this.service = service;
        }

        @PostConstruct
        void init() {
            Operation.bindToServcie(service, this);
        }

        @Branch(dependencies = "branch1")
        public BranchOutput<?> alwaysErrorBranch(BranchContext context) {
            return context.outputBuilder(String.class, new Exception("Test error"), true).build();
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class OpTx {

        private final Service service;

        @Autowired
        private SpringProxyFactory proxyFactory;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @PostConstruct
        void init() {
            Operation.bindToServcie(service, this, proxyFactory);
        }

        @Transactional
        @Branch
        public BranchOutput<Void> branch1(BranchContext context) {
            jdbcTemplate.execute("insert into test values('1', 'a')");
            return context.outputBuilder(Void.class).build();
        }

        @Transactional
        @Branch
        public BranchOutput<Void> branch2(BranchContext context) {
            jdbcTemplate.execute("insert into test values('2', 'b')");
            jdbcTemplate.execute("insert into test values('2', 'c')");
            return context.outputBuilder(Void.class).build();
        }

        @Transactional
        @Branch(dependencies = { "branch1", "branch2", "loopCaller" })
        public BranchOutput<Integer> successBranch(BranchContext context) {
            Integer count = jdbcTemplate.queryForObject("select count(*) from test", Integer.class);
            return context.outputBuilder(Integer.class).result(count).build();
        }

        @Branch
        public BranchOutput<Object> loopCaller(BranchContext context) throws Exception {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                list.add("t_" + i);
            }
            Response response = context.loopBranch("loop", list);
            return context.outputBuilder(Object.class).result(response.getPayload()).build();
        }

        @Transactional
        @LoopBranch
        public BranchOutput<Void> loop(BranchContext context, String element, int index) {
            String sql = String.format("insert into test values('%d', '%s')", index + 10, element);
            jdbcTemplate.execute(sql);
            return context.outputBuilder(Void.class).build();
        }
    }
}
