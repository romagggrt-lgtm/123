package dev.znick;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NickTest {
 @Test void hexColorsHaveNoVisibleControlCodes() {
  var n=Nick.parse(UUID.randomUUID(),"Player123","&#54DAF4н&#54C5EAи&#54B1DFк",1,32,NamedTextColor.WHITE);
  assertEquals("ник",n.plain());
  assertEquals("ник",PlainTextComponentSerializer.plainText().serialize(n.text()));
  assertNotNull(n.text().children().getFirst().color());
  assertEquals(15,n.alias().length());
 }
 @Test void supportsStandardFormatsAndRejectsInvalidControl() {
  var id=UUID.randomUUID();
  assertEquals("Tom",Nick.parse(id,"Tom","&a&lTom&r",4,32,NamedTextColor.WHITE).plain());
  assertThrows(IllegalArgumentException.class,()->Nick.parse(id,"Tom","&zTom",4,32,NamedTextColor.WHITE));
  assertThrows(IllegalArgumentException.class,()->Nick.parse(id,"Tom","Том!!!",4,32,NamedTextColor.WHITE));
 }
 @Test void journalRoundTripsAndRejectsCorruption() throws Exception {
  Path dir=Files.createTempDirectory("znick-test"); var store=new NickStore(dir.resolve("nicknames.yml")); var id=UUID.randomUUID();
  var n=Nick.parse(id,"Tom","&#54DAF4Том",7,32,NamedTextColor.WHITE); store.save(Map.of(id,n));
  assertEquals(n.raw(),store.load(32,NamedTextColor.WHITE).get(id).raw());
  Files.writeString(dir.resolve("nicknames.yml"),"schema: 1\nplayers: [bad\n");
  assertThrows(Exception.class,()->store.load(32,NamedTextColor.WHITE));
  assertEquals("schema: 1\nplayers: [bad\n",Files.readString(dir.resolve("nicknames.yml")));
 }
 @Test void rewriteNamesByBoundariesAndKeepOtherText() {
  var n=Nick.parse(UUID.randomUUID(),"Tom","&aДруг",7,32,NamedTextColor.WHITE);
  var s=new NickState(Map.of(n.id(),n));
  assertEquals("Друг killed Tomcat",PlainTextComponentSerializer.plainText().serialize(s.rewrite(net.kyori.adventure.text.Component.text("Tom killed Tomcat"))));
 }
}
