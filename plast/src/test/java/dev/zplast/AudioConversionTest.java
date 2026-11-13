package dev.zplast;

import dev.zplast.audio.AudioImporter;
import dev.zplast.audio.OggClip;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioConversionTest {
 @Test void mp3ConvertsToValidOggWithoutExternalPrograms() throws Exception {
  byte[] audio=AudioImporter.read(Path.of("../tests/zplast/sample.mp3"));
  assertArrayEquals(new byte[]{'O','g','g','S'},java.util.Arrays.copyOf(audio,4));
  double seconds=OggClip.validate(audio);assertTrue(seconds>1.2&&seconds<2.0,"unexpected duration "+seconds);
 }
}
