package dev.zlor;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StateStoreTest {
    @TempDir Path directory;
    private void drain(StateStore store)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!store.idle()&&System.nanoTime()<end){store.pulse();Thread.sleep(1);}assertTrue(store.idle());}
    @Test void slowDiskNeverPublishesStateOrEffectsBeforeDurability()throws Exception{
        var release=new CountDownLatch(1);var entered=new CountDownLatch(1);var called=new ArrayList<Thread>();Thread owner=Thread.currentThread();
        try(var store=new StateStore(directory.resolve("state.json"),e->fail(e),(path,data)->{assertNotSame(owner,Thread.currentThread());entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new java.io.IOException(e);}StateStore.atomicWrite(path,data);})){
            var id=UUID.randomUUID();var future=store.change(s->s.immune.add(id),s->called.add(Thread.currentThread()),e->fail(e));
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertFalse(future.isDone());assertFalse(store.state().immune.contains(id));assertTrue(called.isEmpty());
            release.countDown();drain(store);assertTrue(future.isDone());assertTrue(store.state().immune.contains(id));assertEquals(List.of(owner),called);
        }finally{release.countDown();}
    }
    @Test void queuedMutationsPrepareFromLatestCommittedRevision()throws Exception{
        try(var store=new StateStore(directory.resolve("state.json"),e->fail(e))){
            var a=UUID.randomUUID();var b=UUID.randomUUID();store.change(s->s.immune.add(a));store.change(s->s.immune.add(b));drain(store);
            assertEquals(Set.of(a,b),store.state().immune);assertEquals(2,store.state().revision);
            State saved=StateStore.JSON.fromJson(Files.readString(store.file()),State.class);assertEquals(Set.of(a,b),saved.immune);
            State backup=StateStore.JSON.fromJson(Files.readString(store.file().resolveSibling("state.json.bak")),State.class);assertEquals(Set.of(a),backup.immune);
        }
    }
    @Test void failedWriteKeepsLastFileAndRunsNoSuccessEffects()throws Exception{
        Path file=directory.resolve("state.json");try(var first=new StateStore(file,e->fail(e))){first.change(s->s.fuelClock=100);drain(first);}
        byte[] before=Files.readAllBytes(file);var errors=new ArrayList<Throwable>();var effects=new ArrayList<String>();
        try(var store=new StateStore(file,errors::add,(path,data)->{throw new java.io.IOException("Synthetic full disk");})){
            store.change(s->s.fuelClock=200,s->effects.add("bad"),errors::add);store.change(s->s.fuelClock=300,s->effects.add("also bad"),errors::add);drain(store);
            assertFalse(store.healthy());assertEquals(100,store.state().fuelClock);assertTrue(effects.isEmpty());assertArrayEquals(before,Files.readAllBytes(file));assertFalse(errors.isEmpty());
        }
    }
    @Test void malformedOrIncompleteInputIsNeverReplaced()throws Exception{
        Path file=directory.resolve("state.json");for(String invalid:List.of("{oops","{}","{\"schema\":999,\"revision\":0,\"roles\":{},\"devices\":{},\"dimensions\":{}}","null")){
            Files.writeString(file,invalid);assertThrows(java.io.IOException.class,()->new StateStore(file,e->fail(e)));assertEquals(invalid,Files.readString(file));
        }
    }
    @Test void suppressedShutdownEffectsDoNotSpawnOrGiveAfterDisable()throws Exception{
        var effects=new ArrayList<String>();var store=new StateStore(directory.resolve("state.json"),e->fail(e));
        store.change(s->s.fuelClock=100,s->effects.add("spawn"),e->fail(e));store.suppressEffects();assertTrue(store.finish(Duration.ofSeconds(3)));assertTrue(effects.isEmpty());
        try(var restarted=new StateStore(directory.resolve("state.json"),e->fail(e))){assertEquals(100,restarted.state().fuelClock);}
    }
    @Test void invalidMutationDoesNotCorruptQueueOrWriteAFile()throws Exception{
        try(var store=new StateStore(directory.resolve("state.json"),e->fail(e))){
            var rejected=store.change(s->{throw new IllegalArgumentException("bad value");});assertTrue(rejected.isCompletedExceptionally());assertFalse(Files.exists(store.file()));
            store.change(s->s.fuelClock=12);drain(store);assertEquals(12,store.state().fuelClock);
        }
    }
}
