package dev.zuluz;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** A short title without a chat message; each glyph keeps its own colour and style. */
final class PossessionTitle {
 private PossessionTitle(){}
 private static final Title MESSAGE=Title.title(text(),Component.empty(),Title.Times.times(Duration.ofMillis(150),Duration.ofMillis(1500),Duration.ofMillis(1000)));
 private static Component text(){
  TextComponent.Builder out=Component.text();
  add(out,"ф","#664969",true,false);add(out,"ц","#6B4A6F",true,false);add(out,"в","#6F4B74",true,false);
  add(out,"В ","#744B7A",false,false);add(out,"В","#7D4D85",false,true);add(out,"А","#814E8A",false,true);add(out,"С ","#864F90",false,false);
  add(out,"ф","#8F509B",true,false);add(out,"Ч","#9351A0",false,false);add(out,"Т","#9852A6",false,false);add(out,"О ","#9C52AB",false,false);
  add(out,"Т","#A554B6",false,true);add(out,"О ","#AC5CBB",false,false);add(out,"В","#B96CC5",false,true);add(out,"С","#BF75CB",false,false);add(out,"Е","#C67DD0",false,false);
  add(out,"Л","#CD85D5",true,false);add(out,"И","#D38DDA",false,true);add(out,"Л","#DA95DF",false,true);add(out,"О","#E19DE4",false,true);add(out,"С","#E7A6EA",false,false);add(out,"Ь","#EEAEEF",false,false);
  add(out,"Ф","#F4B6F4",true,false);add(out,"ф","#FBBEF9",true,false);return out.build();
 }
 private static void add(TextComponent.Builder out,String text,String color,boolean magic,boolean italic){
  out.append(Component.text(text).color(TextColor.fromHexString(color)).decoration(TextDecoration.BOLD,true).decoration(TextDecoration.OBFUSCATED,magic).decoration(TextDecoration.ITALIC,italic));
 }
 static void show(Player player){player.showTitle(MESSAGE);}
}
