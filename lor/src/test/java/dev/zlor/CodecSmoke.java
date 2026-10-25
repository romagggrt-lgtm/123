package dev.zlor;

import dev.zlor.rules.*;
import java.nio.file.*;
import java.util.Arrays;

/** Run after shading with only this test class and the deliverable JAR on classpath. */
public final class CodecSmoke {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("tone.mp3 output.ogg");
        byte[] ogg=AudioImporter.read(Path.of(args[0]));Files.write(Path.of(args[1]),ogg);
        if(!Arrays.equals(ogg,AudioImporter.read(Path.of(args[1]))))throw new IllegalStateException("Mono OGG changed");
        System.out.println("SHADED_CODEC_PASS seconds="+OggClip.validate(ogg)+" bytes="+ogg.length+" source="+AudioImporter.class.getProtectionDomain().getCodeSource().getLocation());
    }
}
