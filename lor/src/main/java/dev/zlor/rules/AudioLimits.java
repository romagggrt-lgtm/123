package dev.zlor.rules;

import java.nio.file.*;
import java.util.Properties;

public final class AudioLimits {
    private AudioLimits() { }
    public static final int HARD_BYTES=20*1024*1024;
    private static volatile int bytes=HARD_BYTES,seconds=600;
    public static int bytes(){return bytes;}
    public static int seconds(){return seconds;}
    public static void restrict(int maxBytes,int maxSeconds){
        if(maxBytes<65536||maxBytes>HARD_BYTES||maxSeconds<1||maxSeconds>600)throw new IllegalArgumentException("Некорректные лимиты аудио");
        bytes=Math.min(bytes,maxBytes);seconds=Math.min(seconds,maxSeconds);
    }
    public static void load(Path dir)throws Exception{
        Files.createDirectories(dir);Path file=dir.resolve("audio.properties");
        if(!Files.exists(file))Files.writeString(file,"# OGG / MP3 / WAV; bytes <= 20971520, duration <= 600 seconds\nmax_bytes=20971520\nmax_seconds=600\n");
        var p=new Properties();try(var reader=Files.newBufferedReader(file)){p.load(reader);}
        int size=Integer.parseInt(p.getProperty("max_bytes","20971520"));int time=Integer.parseInt(p.getProperty("max_seconds","600"));
        if(size<65536||size>HARD_BYTES||time<1||time>600)throw new IllegalArgumentException("audio.properties: max_bytes 65536..20971520, max_seconds 1..600");
        bytes=size;seconds=time;
    }
}
