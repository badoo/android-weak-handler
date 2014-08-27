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

import junit.framework.TestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for {@link com.badoo.mobile.util.WeakHandler}
 *
 * Created by Dmytro Voronkevych on 17/06/2014.
 */
public class WeakHandlerTest extends TestCase {

    public void testChainedRef() {
        final Runnable runHead = new DummyRunnable();
        final Runnable runFirst = new DummyRunnable();
        final Runnable runSecond = new DummyRunnable();

        WeakHandler.ChainedRef refHead = new WeakHandler.ChainedRef(runHead) {
            @Override
            public String toString() {
                return "refHead";
            }
        };
        WeakHandler.ChainedRef first = new WeakHandler.ChainedRef(runFirst) {
            @Override
            public String toString() {
                return "first";
            }
        };
        WeakHandler.ChainedRef second = new WeakHandler.ChainedRef(runSecond) {
            @Override
            public String toString() {
                return "second";
            }
        };

        refHead.insertAbove(first);
        refHead.insertAbove(second);

        assertSame(second, refHead.next);
        assertSame(first, refHead.next.next);
        assertNull(refHead.next.next.next);

        assertNull(refHead.prev);
        assertSame(second, first.prev);
        assertSame(refHead, second.prev);

        assertSame(second, refHead.findForward(runSecond));
        assertSame(first, refHead.findForward(runFirst));
        assertSame(refHead, refHead.findForward(runHead));
        assertNull(refHead.findForward(new DummyRunnable()));

        second.remove();
        assertNull(second.prev);
        assertNull(second.next);
        assertNull(refHead.prev);
        assertNull(first.next);
        assertSame(first, refHead.next);
        assertSame(refHead, first.prev);

        assertSame(first, refHead.findForward(runFirst));
        assertSame(refHead, refHead.findForward(runHead));
        assertNull(refHead.findForward(runSecond));

        first.remove();
        assertSame(WeakHandler.ChainedRef.sPool, first); // It was put in pull
        assertSame(second, first.next);
        assertNotSame(refHead, first.next);
        assertNull(first.prev);
        assertNull(refHead.next);
    }

    public void testChainedRefAlloc() {
        WeakHandler.ChainedRef.sPool = null;
        WeakHandler.ChainedRef.sPoolSize = 0;

        WeakHandler.ChainedRef ref1 = WeakHandler.ChainedRef.obtain(null);
        assertNotNull(ref1);
        assertEquals(0, WeakHandler.ChainedRef.sPoolSize);
        WeakHandler.ChainedRef ref2 = WeakHandler.ChainedRef.obtain(null);
        assertNotNull(ref2);
        assertNotSame(ref1, ref2);
        assertEquals(0, WeakHandler.ChainedRef.sPoolSize);
        ref1.remove();
        assertEquals(1, WeakHandler.ChainedRef.sPoolSize);
        ref2.remove();
        assertEquals(2, WeakHandler.ChainedRef.sPoolSize);
        assertSame(ref2, WeakHandler.ChainedRef.obtain(null));
        assertEquals(1, WeakHandler.ChainedRef.sPoolSize);
        assertSame(ref1, WeakHandler.ChainedRef.obtain(null));
        assertEquals(0, WeakHandler.ChainedRef.sPoolSize);
    }

    public void testPostDelayed() throws InterruptedException {
        HandlerThread thread = new HandlerThread("test");
        thread.start();

        final CountDownLatch latch = new CountDownLatch(1);

        WeakHandler handler = new WeakHandler(thread.getLooper());

        long startTime = SystemClock.elapsedRealtime();
        final AtomicBoolean executed = new AtomicBoolean(false);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executed.set(true);
                latch.countDown();
            }
        }, 300);

        latch.await(1, TimeUnit.SECONDS);
        assertTrue(executed.get());

        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        assertTrue(elapsedTime <= 305 && elapsedTime >= 300);
        thread.getLooper().quit();
    }

    public void testRemoveCallbacks() throws InterruptedException {
        HandlerThread thread = new HandlerThread("test");
        thread.start();

        final CountDownLatch latch = new CountDownLatch(1);

        WeakHandler handler = new WeakHandler(thread.getLooper());

        long startTime = SystemClock.elapsedRealtime();
        final AtomicBoolean executed = new AtomicBoolean(false);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                executed.set(true);
                latch.countDown();
            }
        };
        handler.postDelayed(r, 300);
        handler.removeCallbacks(r);
        latch.await(1, TimeUnit.SECONDS);
        assertFalse(executed.get());

        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        assertTrue(elapsedTime > 300);
        thread.getLooper().quit();
    }

    private class DummyRunnable implements Runnable {
        @Override
        public void run() {
        }
    }
}
