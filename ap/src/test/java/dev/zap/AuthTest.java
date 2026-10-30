package dev.zap;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mindrot.jbcrypt.BCrypt;
class AuthTest {
 @TempDir Path dir;
 @Test void saltedHashAndLockPersistAcrossRestart() throws Exception {
  Auth a=new Auth(dir,5,900,10);String hash=a.currentHash();assertTrue(Auth.verify("324657",hash));assertFalse(Auth.verify("incorrect",hash));
  assertFalse(Files.readString(dir.resolve("auth.yml")).contains("324657"));UUID id=UUID.randomUUID();long now=System.currentTimeMillis();for(int n=0;n<5;n++)a.fail(id,now);assertTrue(a.locked(id,now));
  Auth after=new Auth(dir,5,900,10);assertTrue(after.locked(id,now));assertFalse(after.locked(UUID.randomUUID(),now));assertFalse(after.locked(id,now+901_000));
 }
 @Test void passwordChangeAndInputBounds() throws Exception {
  Auth a=new Auth(dir,5,900,10);String next=BCrypt.hashpw("новый-пароль",BCrypt.gensalt(10));a.change(next);Auth after=new Auth(dir,5,900,10);
  assertTrue(Auth.verify("новый-пароль",after.currentHash()));assertFalse(Auth.verify("324657",after.currentHash()));assertFalse(Auth.validPassword("a".repeat(73)));assertFalse(Auth.validPassword(""));
 }
 @Test void invalidStoredHashDoesNotResetToDefault() throws Exception {Files.writeString(dir.resolve("auth.yml"),"password-hash: invalid\n");assertThrows(java.io.IOException.class,()->new Auth(dir,5,900,10));}
}
