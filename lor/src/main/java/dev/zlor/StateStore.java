package dev.zlor;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** FIFO durable transactions. Preparation/publication/effects stay on the main thread. */
public final class StateStore implements AutoCloseable {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public interface Writer { void write(Path path, byte[] bytes) throws IOException; }
    private record Change(Consumer<State> prepare, Consumer<State> effect, Consumer<Throwable> fail,
                          CompletableFuture<State> result) {}
    private final Path file;
    private final Writer writer;
    private final ThreadPoolExecutor io;
    private final ArrayDeque<Change> queue = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<Runnable> completed = new ConcurrentLinkedQueue<>();
    private final Consumer<Throwable> logger;
    private final Thread owner = Thread.currentThread();
    private State state;
    private boolean busy, stopping, effectsSuppressed;
    private Throwable failure;
    private long writes;

    public StateStore(Path file, Consumer<Throwable> logger) throws IOException {
        this(file,logger,StateStore::atomicWrite);
    }
    public StateStore(Path file, Consumer<Throwable> logger, Writer writer) throws IOException {
        this.file=file; this.logger=logger; this.writer=writer;
        if (Files.exists(file)) {
            if (Files.size(file)>16*1024*1024) throw new IOException("Слишком большой state.json; исходник сохранён");
            try {
                var document=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for(String key:List.of("schema","revision","fuelClock","roles","devices","dimensions","dimensionRevisions","names","immune","actions","restrictions","restrictionsRevision","dragons","arenas","structures","beamHits","audio"))if(!document.has(key))throw new IllegalArgumentException("Нет обязательного поля "+key);
                state=JSON.fromJson(document,State.class);
                if (state==null) throw new IllegalArgumentException("Пустой JSON");
                state.validate();
            } catch (RuntimeException e) {throw new IOException("Повреждены данные Z_Lor; файл не перезаписан: "+file,e);}
        } else state=new State();
        io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{
            var thread=new Thread(r,"Z_Lor-Storage");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public State state() { assertOwner(); return state; }
    public Path file() { return file; }
    public boolean idle() { return !busy && queue.isEmpty() && completed.isEmpty(); }
    public boolean healthy() { return failure==null && !stopping; }
    public long writes() { return writes; }
    public State copy() { return JSON.fromJson(JSON.toJson(state),State.class); }
    private void assertOwner() { if(Thread.currentThread()!=owner)throw new IllegalStateException("Игровые данные доступны только главному потоку"); }

    public CompletableFuture<State> change(Consumer<State> prepare,Consumer<State> effect,Consumer<Throwable> fail) {
        assertOwner(); var result=new CompletableFuture<State>();
        if (stopping || failure!=null || queue.size()>=128) {
            var error=new IllegalStateException("Хранилище Z_Lor временно недоступно",failure);
            result.completeExceptionally(error);if(fail!=null)fail.accept(error);return result;
        }
        queue.addLast(new Change(prepare,effect,fail,result));startNext();return result;
    }
    public CompletableFuture<State> change(Consumer<State> prepare) { return change(prepare,null,null); }
    private void startNext() {
        if (busy || queue.isEmpty() || failure!=null || stopping) return;
        var change=queue.removeFirst(); final State candidate; final byte[] encoded;
        try {
            candidate=copy();change.prepare.accept(candidate);candidate.revision=Math.addExact(state.revision,1);
            candidate.validate();encoded=JSON.toJson(candidate).getBytes(StandardCharsets.UTF_8);
            if(encoded.length>16*1024*1024)throw new IllegalArgumentException("Превышен лимит хранилища");
        } catch (Throwable error) { reject(change,error);startNext();return; }
        busy=true;
        io.execute(()->{
            try {
                writer.write(file,encoded);
                completed.add(()->{
                    writes++;state=candidate;busy=false;
                    try { if(!stopping && !effectsSuppressed && change.effect!=null)change.effect.accept(state);change.result.complete(state); }
                    catch(Throwable error){logger.accept(error);change.result.completeExceptionally(error);}
                    startNext();
                });
            } catch (Throwable error) {
                completed.add(()->{
                    busy=false;failure=error;logger.accept(error);reject(change,error);
                    while(!queue.isEmpty())reject(queue.removeFirst(),error);
                });
            }
        });
    }
    private void reject(Change change,Throwable error) {
        change.result.completeExceptionally(error);if(change.fail!=null)change.fail.accept(error);
    }
    public void pulse() { assertOwner();for(Runnable task;(task=completed.poll())!=null;)task.run(); }
    public void suppressEffects(){assertOwner();effectsSuppressed=true;}
    public boolean finish(Duration timeout) {
        assertOwner();long deadline=System.nanoTime()+timeout.toNanos();
        while(!idle() && System.nanoTime()<deadline){pulse();try{Thread.sleep(1);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}
        stopping=true;io.shutdown();
        try{return io.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS)&&failure==null;}
        catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
    }
    @Override public void close() { finish(Duration.ofSeconds(3)); }

    public static void atomicWrite(Path file,byte[] bytes) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary=Files.createTempFile(file.toAbsolutePath().getParent(),"z-lor-",".tmp");
        try {
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                var data=ByteBuffer.wrap(bytes);while(data.hasRemaining())channel.write(data);channel.force(true);
            }
            if(Files.exists(file)) {
                Path backup=file.resolveSibling(file.getFileName()+".bak");
                Files.copy(file,backup,StandardCopyOption.REPLACE_EXISTING);
            }
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
}
