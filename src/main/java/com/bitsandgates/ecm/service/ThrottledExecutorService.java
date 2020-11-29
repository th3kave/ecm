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
import java.util.concurrent.atomic.AtomicBoolean;

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

        private AtomicBoolean interrupted = new AtomicBoolean();

        public Runner(int size) {
            queue = new ArrayBlockingQueue<>(size, true);
            executorService.execute(this);
        }

        public void run(Runnable r) throws InterruptedException {
            if (!interrupted.get()) {
                try {
                    queue.put(r);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    throw e;
                }
            }
        }

        public void close() {
            if (!interrupted.get()) {
                try {
                    queue.put(killSignal);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            }
        }

        public void run() {
            while (!interrupted.get()) {
                try {
                    Runnable r = queue.take();
                    if (r == killSignal) {
                        break;
                    }
                    executorService.execute(r);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    break;
                }
            }
        }
    }
}
