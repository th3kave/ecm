/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ThrottledExecutorService {

    private static final Runnable killSignal = () -> {
    };

    private final ExecutorService executorService;

    public Runner newRunner(int size) {
        return new Runner(size);
    }

    public class Runner implements Runnable {

        private final BlockingQueue<Runnable> queue;

        public Runner(int size) {
            queue = new ArrayBlockingQueue<>(size, true);
            executorService.execute(this);
        }

        public void run(Runnable r) throws InterruptedException {
            queue.put(r);
        }

        public void close() throws InterruptedException {
            queue.put(killSignal);
        }

        public void run() {
            while (true) {
                try {
                    Runnable r = queue.take();
                    if (r == killSignal) {
                        break;
                    }
                    executorService.execute(r);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
