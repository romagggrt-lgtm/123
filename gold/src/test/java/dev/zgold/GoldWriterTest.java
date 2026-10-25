package dev.zgold;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoldWriterTest {
    @Test void slowDiskNeverRunsOnTheSubmittingOrCallbackThread()throws Exception{
        Thread main=Thread.currentThread();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var disk=new AtomicReference<Thread>();var callback=new AtomicReference<Thread>();
        GoldWriter w=new GoldWriter(data->{disk.set(Thread.currentThread());entered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}});
        try {
            assertTrue(w.submit(new byte[]{1},true,()->callback.set(Thread.currentThread()),e->fail(e)));
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertNotSame(main,disk.get());assertNull(callback.get());assertFalse(w.idle());
            release.countDown();settle(w);assertSame(main,callback.get());
        }finally{release.countDown();w.finish(new byte[]{1},Duration.ofSeconds(1));}
    }
    @Test void checkpointsCoalesceButCriticalCommitsKeepTheirOrder()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);List<Integer> written=Collections.synchronizedList(new ArrayList<>());
        GoldWriter w=new GoldWriter(data->{if(data[0]==0){entered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}}written.add((int)data[0]);});
        try {
            assertTrue(w.submit(new byte[]{0},true,()->{},e->fail(e)));assertTrue(entered.await(1,TimeUnit.SECONDS));
            for(int n:new int[]{1,2})assertTrue(w.submit(new byte[]{(byte)n},false,()->{},e->fail(e)));
            assertTrue(w.submit(new byte[]{3},true,()->{},e->fail(e)));
            for(int n:new int[]{4,5})assertTrue(w.submit(new byte[]{(byte)n},false,()->{},e->fail(e)));
            release.countDown();settle(w);assertEquals(List.of(0,3,5),written);
        }finally{release.countDown();w.finish(new byte[]{6},Duration.ofSeconds(1));}
    }
    @Test void failureStopsLaterWritesAndDoesNotRunSuccessEffects()throws Exception{
        AtomicInteger writes=new AtomicInteger(),success=new AtomicInteger(),errors=new AtomicInteger();
        GoldWriter w=new GoldWriter(data->{writes.incrementAndGet();throw new IOException("test disk failure");});
        assertTrue(w.submit(new byte[]{1},true,success::incrementAndGet,e->errors.incrementAndGet()));settle(w);
        assertEquals(1,writes.get());assertEquals(0,success.get());assertEquals(1,errors.get());assertNotNull(w.failure());
        assertFalse(w.submit(new byte[]{2},true,success::incrementAndGet,e->errors.incrementAndGet()));w.finish(new byte[]{3},Duration.ofSeconds(1));
    }
    @Test void shutdownWaitHasABoundedDeadline()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        GoldWriter w=new GoldWriter(data->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}});
        w.submit(new byte[]{1},true,()->{},e->fail(e));assertTrue(entered.await(1,TimeUnit.SECONDS));
        try {long start=System.nanoTime();assertFalse(w.finish(new byte[]{2},Duration.ofMillis(30)));assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));}
        finally{release.countDown();settle(w);}
    }
    @Test void queuedSnapshotIsIsolatedFromLaterCallerMutation()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);AtomicInteger observed=new AtomicInteger();
        GoldWriter w=new GoldWriter(data->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}observed.set(data[0]);});
        byte[] source={7};w.submit(source,true,()->{},e->fail(e));assertTrue(entered.await(1,TimeUnit.SECONDS));source[0]=99;
        release.countDown();settle(w);assertEquals(7,observed.get());w.finish(new byte[]{7},Duration.ofSeconds(1));
    }
    private void settle(GoldWriter writer)throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<until){writer.drain();if(writer.idle())return;Thread.sleep(1);}
        fail("Writer did not settle");
    }
}
