package dev.zbanya;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Write-ahead records: no world edit is permitted until the journal is durable. */
public class Journal implements AutoCloseable {
    public static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Z_Banya-Journal");t.setDaemon(true);return t;});
    public Journal(Path directory)throws Exception{this.directory=directory;Files.createDirectories(directory);}
    public List<Bath> load()throws Exception{
        var result=new ArrayList<Bath>();try(var files=Files.list(directory)){for(Path path:files.filter(p->p.toString().endsWith(".json")).toList()){
            if(Files.size(path)>2_000_000)throw new IllegalArgumentException("Слишком большой журнал: "+path.getFileName());
            var object=JsonParser.parseString(Files.readString(path)).getAsJsonObject();for(String key:List.of("schema","id","owner","world","x","y","z","turn","phase","hasFood","restore","foodAmount","cells"))if(!object.has(key))throw new IllegalArgumentException("Неполный журнал: "+path.getFileName());
            Bath b=JSON.fromJson(object,Bath.class);b.validate();if(!path.getFileName().toString().equals(b.id+".json"))throw new IllegalArgumentException("UUID журнала не совпал");result.add(b);
        }}return result;
    }
    public CompletableFuture<Void> save(Bath bath){String text=JSON.toJson(bath);return CompletableFuture.runAsync(()->{try{atomic(directory.resolve(bath.id+".json"),text);}catch(Exception e){throw new CompletionException(e);}},writer);}
    public CompletableFuture<Void> delete(UUID id){return CompletableFuture.runAsync(()->{try{Files.deleteIfExists(directory.resolve(id+".json"));}catch(Exception e){throw new CompletionException(e);}},writer);}
    public static void atomic(Path file,String text)throws Exception{
        Path temp=Files.createTempFile(file.getParent(),"banya-",".pending");try{
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){var bytes=ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    @Override public void close(){writer.shutdown();try{if(!writer.awaitTermination(10,TimeUnit.SECONDS))writer.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
