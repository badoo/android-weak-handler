package com.badoo.mobile.util;

import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

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
    private WeakHandler.ChainedRef mFirst;
    private WeakHandler.ChainedRef mSecond;
    private WeakHandler.WeakRunnable mHeadWeakRunnable;
    private WeakHandler.WeakRunnable mFirstWeakRunnable;
    private WeakHandler.WeakRunnable mSecondWeakRunnable;

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
                return "first";
            }
        };
        mSecond = new WeakHandler.ChainedRef(mLock, mSecondRunnable) {
            @Override
            public String toString() {
                return "second";
            }
        };

        mRefHead.insertAbove(mFirst);
        mRefHead.insertAbove(mSecond);

        mHeadWeakRunnable = mRefHead.wrapper;
        mFirstWeakRunnable = mFirst.wrapper;
        mSecondWeakRunnable = mSecond.wrapper;
    }

    @Test
    public void insertAbove() {
        assertSame(mSecond, mRefHead.next);
        assertSame(mFirst, mRefHead.next.next);
        assertNull(mRefHead.next.next.next);

        assertNull(mRefHead.prev);
        assertSame(mSecond, mFirst.prev);
        assertSame(mRefHead, mSecond.prev);
    }

    @Test
    public void removeFirst() {
        mFirst.remove();
        assertNull(mFirst.next);
        assertNull(mFirst.prev);

        assertSame(mSecond, mRefHead.next);
        assertSame(mRefHead, mSecond.prev);
        assertNull(mSecond.next);
    }

    @Test
    public void removeSecond() {
        mSecond.remove();

        assertNull(mSecond.next);
        assertNull(mSecond.prev);

        assertSame(mFirst, mRefHead.next);
        assertNull(mFirst.next);
        assertSame(mRefHead, mFirst.prev);
    }

    @Test
    public void removeFirstByRunnable() {
        assertSame(mFirstWeakRunnable, mRefHead.remove(mFirstRunnable));
        assertSame(mRefHead.next, mSecond);
        assertSame(mRefHead, mSecond.prev);
        assertNull(mSecond.next);
    }

    @Test
    public void removeSecondByRunnable() {
        assertSame(mSecondWeakRunnable, mRefHead.remove(mSecondRunnable));
        assertSame(mFirst, mRefHead.next);
        assertSame(mRefHead, mFirst.prev);
    }

    @Test
    public void removeUnexistentRunnableReturnNull() {
        assertNull(mRefHead.remove(new DummyRunnable()));
        assertSame(mSecond, mRefHead.next);
        assertNull(mFirst.next);
        assertSame(mSecond, mFirst.prev);
        assertSame(mRefHead, mSecond.prev);
    }

    private class DummyRunnable implements Runnable {
        @Override
        public void run() {
        }
    }
}
