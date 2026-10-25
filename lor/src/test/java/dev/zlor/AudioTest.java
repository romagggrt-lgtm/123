package dev.zlor;

import dev.zlor.rules.*;
import com.github.axet.jvorbis.spi.file.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.sound.sampled.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AudioTest {
    @TempDir Path directory;
    @BeforeEach void defaults()throws Exception{AudioLimits.load(directory);}
    private int channels(byte[] ogg){int header=27+(ogg[26]&255);return ogg[header+11]&255;}
    private byte[] pcm(int seconds,int channels){byte[] samples=new byte[44100*seconds*channels*2];for(int i=0;i<samples.length/2;i++){short value=(short)(Math.sin((i/channels)*440*2*Math.PI/44100)*10000);samples[2*i]=(byte)value;samples[2*i+1]=(byte)(value>>>8);}return samples;}
    private Path wav(String name,int seconds,int channels)throws Exception{
        var format=new AudioFormat(44100,16,channels,true,false);byte[] data=pcm(seconds,channels);Path file=directory.resolve(name);
        try(var stream=new AudioInputStream(new ByteArrayInputStream(data),format,data.length/format.getFrameSize())){AudioSystem.write(stream,AudioFileFormat.Type.WAVE,file.toFile());}return file;
    }
    private Path ogg(String name,int channels)throws Exception{
        var format=new AudioFormat(44100,16,channels,true,false);byte[] data=pcm(1,channels);Path file=directory.resolve(name);
        try(var stream=new AudioInputStream(new ByteArrayInputStream(data),format,data.length/format.getFrameSize())){new OggVorbisAudioFileWriter().write(stream,new EncoderFileFormatType("OggVorbis","ogg",EncoderFileFormatType.VBR,.25f),file.toFile());}return file;
    }
    @Test void uploadedMp3FixtureConvertsToPlayableMonoVorbis()throws Exception{
        Path source=directory.resolve("tone.mp3");try(var input=getClass().getResourceAsStream("/audio/tone.mp3")){Files.copy(input,source);}
        byte[] result=AudioImporter.read(source);assertEquals(1,channels(result));assertTrue(OggClip.validate(result)>0);assertEquals(64,OggClip.hash(result).length());
        Path file=directory.resolve("roundtrip.ogg");Files.write(file,result);assertArrayEquals(result,AudioImporter.read(file));
    }
    @Test void stereoWavBecomesMonoWithoutChangingDuration()throws Exception{byte[] result=AudioImporter.read(wav("stereo.wav",1,2));assertEquals(1,channels(result));assertEquals(1,OggClip.validate(result),.1);}
    @Test void stereoOggIsDownmixedForSpatialPlayback()throws Exception{byte[] result=AudioImporter.read(ogg("stereo.ogg",2));assertEquals(1,channels(result));assertEquals(1,OggClip.validate(result),.1);}
    @Test void monoOggRetainsExactBytesAfterActualDecodeValidation()throws Exception{Path source=ogg("mono.ogg",1);assertArrayEquals(Files.readAllBytes(source),AudioImporter.read(source));}
    @Test void corruptContainersAndRenamedNonAudioAreRejected()throws Exception{
        Path source=ogg("bad.ogg",1);byte[] bytes=Files.readAllBytes(source);bytes[bytes.length-10]^=1;Files.write(source,bytes);assertThrows(Exception.class,()->AudioImporter.read(source));
        Path fake=directory.resolve("fake.mp3");Files.writeString(fake,"not an mp3");assertThrows(Exception.class,()->AudioImporter.read(fake));
    }
    @Test void oversizeIsRejectedBeforeDecoding()throws Exception{Path source=directory.resolve("oversize.wav");try(var file=new RandomAccessFile(source.toFile(),"rw")){file.setLength(AudioLimits.HARD_BYTES+1L);}assertThrows(IOException.class,()->AudioImporter.read(source));}
    @Test void durationAndConfiguredHardCapsAreEnforced()throws Exception{
        Path source=wav("two-seconds.wav",2,1);Files.writeString(directory.resolve("audio.properties"),"max_bytes=20971520\nmax_seconds=1\n");AudioLimits.load(directory);assertThrows(Exception.class,()->AudioImporter.read(source));
        Files.writeString(directory.resolve("audio.properties"),"max_bytes=999999999\nmax_seconds=600\n");assertThrows(IllegalArgumentException.class,()->AudioLimits.load(directory));
    }
    @Test void cancellationInterruptsConversion()throws Exception{
        Path source=wav("cancel.wav",1,2);Thread.currentThread().interrupt();try{assertThrows(Exception.class,()->AudioImporter.read(source));}finally{Thread.interrupted();}
    }
    @Test void sirenCatalogueHasExactlyTheEightApprovedTracks(){assertEquals(8,Audio.TRACKS.size());assertEquals(List.of("alarm","exchange","watch","frontier","evacuation","mechanical","classic","pulse"),Audio.TRACKS);assertEquals("minecraft:block.end_portal_frame.fill",Audio.gateSound("portal"));assertEquals("minecraft:block.respawn_anchor.deplete",Audio.gateSound("anchor"));}
}
