package dev.zlor.rules;

import java.io.*;
import java.nio.file.*;
import javax.sound.sampled.*;
import javazoom.jl.decoder.*;
import com.github.axet.jvorbis.spi.file.*;

/** Pure Java conversion. Caller runs it on a worker, never the game/render thread. */
public final class AudioImporter {
    private AudioImporter() { }
    public static byte[] read(Path file)throws Exception{
        if(!Files.isRegularFile(file)||Files.size(file)>AudioLimits.bytes())throw new IOException("Файл превышает лимит "+AudioLimits.bytes()/1048576+" МиБ.");
        String name=file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if(name.endsWith(".ogg")){
            byte[] b=Files.readAllBytes(file);OggClip.validate(b);
            try(var source=new javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader().getAudioInputStream(new ByteArrayInputStream(b))){
                var f=source.getFormat();validate(f.getSampleRate(),f.getChannels());
                var pcm=new AudioFormat(f.getSampleRate(),16,f.getChannels(),true,false);
                try(var decoded=new javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider().getAudioInputStream(pcm,source)){
                    if(pcm.getChannels()==1){
                        long count=0,max=(long)pcm.getSampleRate()*2*AudioLimits.seconds();byte[] buffer=new byte[8192];
                        for(int n;(n=decoded.read(buffer))!=-1;){if(Thread.currentThread().isInterrupted())throw new IOException("Импорт отменён.");count+=n;if(count>max)throw new IOException("Слишком длинный звук.");}
                        return b;
                    }
                    return encode(decoded,pcm);
                }
            }
        }
        if(name.endsWith(".wav")){
            try(var source=AudioSystem.getAudioInputStream(file.toFile())){
                var f=source.getFormat();validate(f.getSampleRate(),f.getChannels());
                var pcm=new AudioFormat(f.getSampleRate(),16,f.getChannels(),true,false);
                try(var decoded=AudioSystem.getAudioInputStream(pcm,source)){return encode(decoded,pcm);}
            }
        }
        if(name.endsWith(".mp3")){
            try(var source=Files.newInputStream(file);var decoded=new Mp3Stream(source)){return encode(decoded,decoded.format);}
        }
        throw new IOException("Выберите OGG Vorbis, MP3 или WAV.");
    }
    private static void validate(float rate,int channels)throws IOException{
        if(!Float.isFinite(rate)||rate<8000||rate>48000||channels<1||channels>2)throw new IOException("Нужен звук 8–48 кГц, моно или стерео.");
    }
    private static byte[] encode(InputStream pcm,AudioFormat format)throws Exception{
        AudioFormat mono=new AudioFormat(format.getSampleRate(),16,1,true,false);
        InputStream samples=format.getChannels()==2?new StereoToMono(pcm):pcm;
        long max=(long)mono.getSampleRate()*mono.getFrameSize()*AudioLimits.seconds();
        try(var bounded=new LimitedInput(samples,max,mono.getFrameSize());var input=new AudioInputStream(bounded,mono,AudioSystem.NOT_SPECIFIED)){
            var output=new ByteArrayOutputStream();
            var sink=new FilterOutputStream(output){
                private int count;
                @Override public void write(int b)throws IOException{if(++count>AudioLimits.bytes())throw new IOException("Преобразованный звук превышает лимит.");out.write(b);}
                @Override public void write(byte[] b,int off,int len)throws IOException{if(len>AudioLimits.bytes()-count)throw new IOException("Преобразованный звук превышает лимит.");count+=len;out.write(b,off,len);}
            };
            new OggVorbisAudioFileWriter().write(input,new EncoderFileFormatType("OggVorbis","ogg",EncoderFileFormatType.VBR,.25f),sink);
            byte[] result=output.toByteArray();OggClip.validate(result);return result;
        }
    }
    private static final class StereoToMono extends InputStream{
        final InputStream source;final byte[] frame=new byte[4];int high=-1;
        StereoToMono(InputStream source){this.source=source;}
        @Override public int read()throws IOException{
            if(high>=0){int value=high;high=-1;return value;}
            int at=0;while(at<4){int n=source.read(frame,at,4-at);if(n<0){if(at==0)return -1;throw new IOException("Обрезанный PCM кадр");}if(n==0)continue;at+=n;}
            int left=(short)((frame[0]&255)|(frame[1]<<8)),right=(short)((frame[2]&255)|(frame[3]<<8));int value=(left+right)/2;high=(value>>>8)&255;return value&255;
        }
        @Override public int read(byte[] b,int off,int len)throws IOException{if(len==0)return 0;int count=0;for(;count<len;count++){int value=read();if(value<0)break;b[off+count]=(byte)value;}return count==0?-1:count;}
        @Override public void close()throws IOException{source.close();}
    }
    private static final class LimitedInput extends FilterInputStream {
        private long left;private final int frameSize;
        LimitedInput(InputStream in,long max,int frameSize){super(in);left=max;this.frameSize=frameSize;}
        @Override public int read()throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("Импорт отменён.");int b=in.read();if(b!=-1&&left--<=0)throw new IOException("Звук длиннее "+AudioLimits.seconds()+" секунд.");return b;}
        @Override public int read(byte[] b,int off,int len)throws IOException{
            if(Thread.currentThread().isInterrupted())throw new IOException("Импорт отменён.");
            int n=in.read(b,off,(int)Math.min(len,left+frameSize));if(n>left)throw new IOException("Звук длиннее "+AudioLimits.seconds()+" секунд.");if(n>0)left-=n;return n;
        }
    }
    private static final class Mp3Stream extends InputStream {
        private final Bitstream bits;private final Decoder decoder=new Decoder();
        private byte[] frame=new byte[0];private int at;final AudioFormat format;
        Mp3Stream(InputStream in)throws Exception{bits=new Bitstream(in);Header h=bits.readFrame();if(h==null)throw new IOException("Пустой MP3.");format=new AudioFormat(h.frequency(),16,h.mode()==Header.SINGLE_CHANNEL?1:2,true,false);validate(format.getSampleRate(),format.getChannels());decode(h);}
        private void decode(Header h)throws Exception{var s=(SampleBuffer)decoder.decodeFrame(h,bits);if(s.getChannelCount()!=format.getChannels()||s.getSampleFrequency()!=format.getSampleRate())throw new IOException("MP3 меняет формат внутри потока.");frame=new byte[s.getBufferLength()*2];for(int i=0;i<s.getBufferLength();i++){short v=s.getBuffer()[i];frame[i*2]=(byte)v;frame[i*2+1]=(byte)(v>>>8);}at=0;bits.closeFrame();}
        @Override public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
        @Override public int read(byte[] b,int off,int len)throws IOException{
            if(len==0)return 0;
            try{while(at==frame.length){Header h=bits.readFrame();if(h==null)return -1;decode(h);}int n=Math.min(len,frame.length-at);System.arraycopy(frame,at,b,off,n);at+=n;return n;}
            catch(Exception e){throw new IOException("Не удалось прочитать MP3.",e);}
        }
        @Override public void close()throws IOException{try{bits.close();}catch(Exception e){throw new IOException(e);}}
    }
}
