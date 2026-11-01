package dev.zap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiThemeTest {
 private String text(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
 @Test void choosesMatchingAtlasFramesAndLayouts(){
  for(int frame=0;frame<3;frame++){
   String t=text(GuiTheme.chest("home","Главная",frame));int first=0xe100+frame*16;
   for(int code=first;code<first+16;code++)assertTrue(t.indexOf(code)>=0,"Missing home tile "+code);
  }
  assertTrue(text(GuiTheme.chest("players","Игроки",2)).indexOf(0xe160)>=0);
  assertTrue(text(GuiTheme.chest("morph","Морфы",2)).indexOf(0xe160)>=0);
  assertTrue(text(GuiTheme.chest("modes","Режим",2)).indexOf(0xe170)>=0);
  assertTrue(text(GuiTheme.chest("settings","Настройки",2)).indexOf(0xe130)>=0);
  assertTrue(text(GuiTheme.anvil("Введите пароль")).indexOf(0xe140)>=0);
  for(int code=0xe150;code<=0xe153;code++)assertTrue(text(GuiTheme.welcome()).indexOf(code)>=0);
 }
}
