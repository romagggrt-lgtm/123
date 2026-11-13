package dev.zplast;

import org.junit.jupiter.api.Test;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectDownloadTest {
 @Test void onlyPublicDestinationsArePermitted() throws Exception {
  for(String address:new String[]{"127.0.0.1","10.1.2.3","169.254.169.254","192.168.1.2","100.64.1.1","::1","fc00::1","2001:db8::1"})assertFalse(DirectDownload.isPublic(InetAddress.getByName(address)),address);
  for(String address:new String[]{"8.8.8.8","1.1.1.1","2606:4700:4700::1111"})assertTrue(DirectDownload.isPublic(InetAddress.getByName(address)),address);
 }
 @Test void validatesUrlsAndDoesNotPermitCredentials() throws Exception {
  assertDoesNotThrow(()->DirectDownload.validate(URI.create("https://example.com/music.mp3?token=abc")));
  for(String value:new String[]{"file:///etc/passwd","http://user:pass@example.com/music.mp3","https://example.com/music.mp3#fragment","ftp://example.com/x.mp3"})assertThrows(java.io.IOException.class,()->DirectDownload.validate(URI.create(value)),value);
 }
 @Test void rejectsLocalNetworkWithoutDownloading() throws Exception {
  var file=java.nio.file.Files.createTempFile("zplast-ssrf", ".mp3");
  try{var e=assertThrows(java.io.IOException.class,()->DirectDownload.download("http://127.0.0.1:8080/",file,100));assertTrue(e.getMessage().contains("публичные"));assertEquals(0,java.nio.file.Files.size(file));}finally{java.nio.file.Files.deleteIfExists(file);}
 }
}
