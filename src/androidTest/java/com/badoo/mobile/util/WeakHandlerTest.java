/*
 * Copyright (c) 2014 Badoo Trading Limited
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.badoo.mobile.util;

import android.os.HandlerThread;
import android.os.SystemClock;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.badoo.mobile.util.WeakHandler.ChainedRef;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * Unit tests for {@link com.badoo.mobile.util.WeakHandler}
 *
 * Created by Dmytro Voronkevych on 17/06/2014.
 */
@SuppressWarnings("ALL")
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WeakHandlerTest {

    private HandlerThread mThread;
    private WeakHandler mHandler;

    @Before
    public void setup() {
        mThread = new HandlerThread("test");
        mThread.start();
        mHandler = new WeakHandler(mThread.getLooper());
    }

    @After
    public void tearDown() {
        mHandler.getLooper().quit();
    }

    @FlakyTest
    @Test
    public void postDelayed() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        long startTime = SystemClock.elapsedRealtime();
        final AtomicBoolean executed = new AtomicBoolean(false);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executed.set(true);
                latch.countDown();
            }
        }, 300);

        latch.await(1, TimeUnit.SECONDS);
        assertTrue(executed.get());

        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        assertTrue("Elapsed time should be 300, but was " + elapsedTime, elapsedTime <= 330 && elapsedTime >= 300);
    }

    @Test
    public void removeCallbacks() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        long startTime = SystemClock.elapsedRealtime();
        final AtomicBoolean executed = new AtomicBoolean(false);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                executed.set(true);
                latch.countDown();
            }
        };
        mHandler.postDelayed(r, 300);
        mHandler.removeCallbacks(r);
        latch.await(1, TimeUnit.SECONDS);
        assertFalse(executed.get());

        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        assertTrue(elapsedTime > 300);
    }

    @Test(timeout = 30000)
    public void concurrentRemoveAndExecute() throws Throwable {
        final int repeatCount = 100;
        final int numberOfRunnables = 10000;

        // Councurrent cases sometimes very hard to spot, so we will do it by repeating same test 1000 times
        // Problem was reproducing always by this test until I fixed WeakHandler
        for (int testNum = 0; testNum < repeatCount; ++testNum) {
            final AtomicReference<Throwable> mExceptionInThread = new AtomicReference<>();

            HandlerThread thread = new HandlerThread("HandlerThread");
            // Concurrent issue can occur inside HandlerThread or inside main thread
            // Catching both of cases
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable ex) {
                    mExceptionInThread.set(ex);
                }
            });
            thread.start();

            WeakHandler handler = new WeakHandler(thread.getLooper());
            Runnable[] runnables = new Runnable[numberOfRunnables];
            for (int i = 0; i < runnables.length; ++i) {
                runnables[i] = new DummyRunnable();
                handler.post(runnables[i]); // Many Runnables been posted
            }

            for (Runnable runnable : runnables) {
                handler.removeCallbacks(runnable); // All of them now quickly removed
                // Before I fixed impl of WeakHandler it always caused exceptions
            }
            if (mExceptionInThread.get() != null) {
                throw mExceptionInThread.get(); // Exception from HandlerThread. Sometimes it occured as well
            }
            thread.getLooper().quit();
        }
    }

    @Test(timeout = 30000)
    public void concurrentAdd() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 50, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(100));
        final Set<Runnable> added = Collections.synchronizedSet(new HashSet());
        final CountDownLatch latch = new CountDownLatch(999);
        // Adding 1000 Runnables from different threads
        mHandler.post(new SleepyRunnable(0));
        for (int i = 0; i < 999; ++i) {
            final SleepyRunnable sleepyRunnable = new SleepyRunnable(i+1);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    mHandler.post(sleepyRunnable);
                    added.add(sleepyRunnable);
                    latch.countDown();
                }
            });
        }

        // Waiting until all runnables added
        // Notified by #Notify1
        latch.await();

        ChainedRef ref = mHandler.mRunnables.next;
        while (ref != null) {
            assertTrue("Must remove runnable from chained list: " + ref.runnable, added.remove(ref.runnable));
            ref = ref.next;
        }

        assertTrue("All runnables should present in chain, however we still haven't found " + added, added.isEmpty());
    }

    private class DummyRunnable implements Runnable {
        @Override
        public void run() {
        }
    }

    private class SleepyRunnable implements Runnable {
        private final int mNum;

        public SleepyRunnable(int num) {
            mNum = num;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1000000);
            }
            catch (Exception e) {
                // Ignored
            }
        }

        @Override
        public String toString() {
            return String.valueOf(mNum);
        }
    }
}
