package dev.zgold;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Immutable snapshots only on the disk thread; gameplay callbacks only in drain(). */
final class GoldWriter {
    interface Sink { void write(byte[] data) throws IOException; }
    private final Sink sink;
    private final ThreadPoolExecutor worker;
    private final ConcurrentLinkedQueue<Runnable> completed=new ConcurrentLinkedQueue<>();
    private volatile IOException failure;
    private final AtomicInteger outstanding=new AtomicInteger();
    private final AtomicLong submitted=new AtomicLong();
    GoldWriter(Sink sink) {
        this.sink=sink;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>(256),r->{
            Thread t=new Thread(r,"Z_Gold-Storage");t.setDaemon(true);return t;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    synchronized boolean submit(byte[] data,boolean critical,Runnable success,Consumer<IOException> error) {
        if(failure!=null||worker.isShutdown())return false;
        // A newer full snapshot covers any queued checkpoint. Durable action jobs retain FIFO order.
        for(Runnable queued:worker.getQueue().toArray(new Runnable[0]))
            if(queued instanceof Job j&&!j.critical&&worker.getQueue().remove(queued))outstanding.decrementAndGet();
        outstanding.incrementAndGet();
        try {worker.execute(new Job(data.clone(),critical,success,error));submitted.incrementAndGet();return true;}
        catch(RejectedExecutionException e){outstanding.decrementAndGet();return false;}
    }
    private final class Job implements Runnable {
        final byte[] data;final boolean critical;final Runnable success;final Consumer<IOException> error;
        Job(byte[] data,boolean critical,Runnable success,Consumer<IOException> error){this.data=data;this.critical=critical;this.success=success;this.error=error;}
        public void run() {
            IOException problem=failure;
            if(problem==null)try {sink.write(data);}
            catch(IOException e){failure=e;problem=e;}
            catch(RuntimeException e){failure=new IOException("Golda storage failed",e);problem=failure;}
            IOException saved=problem;
            completed.add(()->{try {if(saved==null)success.run();else error.accept(saved);}finally{outstanding.decrementAndGet();}});
        }
    }
    void drain(){for(Runnable callback;(callback=completed.poll())!=null;)callback.run();}
    boolean idle(){return outstanding.get()==0;}
    long submitted(){return submitted.get();}
    IOException failure(){return failure;}
    boolean finish(byte[] finalState,Duration deadline) {
        submit(finalState,false,()->{},e->{});worker.shutdown();
        try {return worker.awaitTermination(deadline.toMillis(),TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
    }
}
