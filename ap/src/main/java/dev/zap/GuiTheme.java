package dev.zap;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Isolated resource-pack font; never overrides the vanilla Minecraft font. */
final class GuiTheme {
 static final String DEFAULT_PACK_SHA1="112d2874851e8ddc7f6440489bb836709f932e5b";
 private static final Key FONT=Key.key("z_ap","gui"),DEFAULT=Key.key("minecraft","default");
 private GuiTheme(){}
 private static Component glyph(String text){return Component.text(text).font(FONT).color(NamedTextColor.WHITE);}
 static Component chest(String kind,String title,int frame){
  boolean home=kind.equals("home");char pic=home?(char)('\ue010'+Math.clamp(frame,0,2)):'\ue013';
  Component image=glyph("\ue000"+pic);if(home)return image;
  String label=title.length()>20?title.substring(0,19)+"…":title;
  return image.append(glyph("\ue001")).append(Component.text(label,NamedTextColor.WHITE).font(DEFAULT));
 }
 static Component anvil(String title){String label=title.length()>20?title.substring(0,19)+"…":title;return glyph("\ue002\ue014\ue003").append(Component.text(label,NamedTextColor.WHITE).font(DEFAULT));}
 static Component welcome(){return glyph("\ue015");}
}
