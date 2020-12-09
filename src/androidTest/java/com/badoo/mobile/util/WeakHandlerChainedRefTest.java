package com.badoo.mobile.util;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;

/**
 * Tests for {@link com.badoo.mobile.util.WeakHandler.ChainedRef}
 *
 * @author Dmytro Voronkevych
 */
@SuppressWarnings("ALL")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WeakHandlerChainedRefTest {

    private Runnable mHeadRunnable;
    private Runnable mFirstRunnable;
    private Runnable mSecondRunnable;
    private Lock mLock;
    private WeakHandler.ChainedRef mRefHead;
    private WeakHandler.ChainedRef mSecond;
    private WeakHandler.ChainedRef mFirst;
    private WeakHandler.WeakRunnable mHeadWeakRunnable;
    private WeakHandler.WeakRunnable mFirstWeakRunnable;
    private WeakHandler.WeakRunnable mSecondWeakRunnable;

    // Creates linked list refHead <-> first <-> second
    @Before
    public void setUp() {
        mLock = new ReentrantLock();

        mHeadRunnable = new DummyRunnable();
        mFirstRunnable = new DummyRunnable();
        mSecondRunnable = new DummyRunnable();

        mRefHead = new WeakHandler.ChainedRef(mLock, mHeadRunnable) {
            @Override
            public String toString() {
                return "refHead";
            }
        };
        mFirst = new WeakHandler.ChainedRef(mLock, mFirstRunnable) {
            @Override
            public String toString() {
                return "second";
            }
        };
        mSecond = new WeakHandler.ChainedRef(mLock, mSecondRunnable) {
            @Override
            public String toString() {
                return "first";
            }
        };

        mRefHead.insertAfter(mSecond);
        mRefHead.insertAfter(mFirst);

        mHeadWeakRunnable = mRefHead.wrapper;
        mFirstWeakRunnable = mFirst.wrapper;
        mSecondWeakRunnable = mSecond.wrapper;
    }

    @Test
    public void insertAfter() {
        assertSame(mFirst, mRefHead.next);
        assertSame(mSecond, mRefHead.next.next);
        assertNull(mRefHead.next.next.next);

        assertNull(mRefHead.prev);
        assertSame(mFirst, mSecond.prev);
        assertSame(mRefHead, mFirst.prev);
    }

    @Test
    public void removeFirst() {
        mFirst.remove();

        assertNull(mFirst.next);
        assertNull(mFirst.prev);

        assertSame(mSecond, mRefHead.next);
        assertNull(mSecond.next);
        assertSame(mRefHead, mSecond.prev);
    }

    @Test
    public void removeSecond() {
        mSecond.remove();
        assertNull(mSecond.next);
        assertNull(mSecond.prev);

        assertSame(mFirst, mRefHead.next);
        assertSame(mRefHead, mFirst.prev);
        assertNull(mFirst.next);
    }

    @Test
    public void removeFirstByRunnable() {
        assertSame(mFirstWeakRunnable, mRefHead.remove(mFirstRunnable));
        assertSame(mRefHead.next, mSecond);
        assertSame(mRefHead, mSecond.prev);
        assertNull(mFirst.next);
        assertNull(mFirst.prev);
    }

    @Test
    public void removeSecondByRunnable() {
        assertSame(mSecondWeakRunnable, mRefHead.remove(mSecondRunnable));
        assertSame(mFirst, mRefHead.next);
        assertSame(mRefHead, mFirst.prev);
        assertNull(mSecond.next);
        assertNull(mSecond.prev);
    }

    @Test
    public void removeNonExistentRunnableReturnNull() {
        assertNull(mRefHead.remove(new DummyRunnable()));
        assertSame(mFirst, mRefHead.next);
        assertNull(mSecond.next);
        assertSame(mFirst, mSecond.prev);
        assertSame(mRefHead, mFirst.prev);
    }

    private class DummyRunnable implements Runnable {
        @Override
        public void run() {
        }
    }
}
