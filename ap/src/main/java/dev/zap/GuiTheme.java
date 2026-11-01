package dev.zap;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Isolated resource-pack font; never overrides the vanilla Minecraft font. */
final class GuiTheme {
 static final String DEFAULT_PACK_SHA1="64baa1f393ec010696c9c28f989d39228b1d1301";
 private static final Key FONT=Key.key("z_ap","gui"),DEFAULT=Key.key("minecraft","default");
 private GuiTheme(){}
 private static Component glyph(String text){return Component.text(text).font(FONT).color(NamedTextColor.WHITE);}
 private static String image(int start,int rows,char offset){
  StringBuilder b=new StringBuilder().append(offset);
  for(int row=0;row<rows;row++){
   for(int col=0;col<4;col++)b.append((char)(start+row*4+col)).append('\ue005');
   if(row+1<rows)b.append('\ue004');
  }
  return b.toString();
 }
 static Component chest(String kind,String title,int frame){
  boolean home=kind.equals("home");int first=home?0xe100+Math.clamp(frame,0,2)*16:0xe130;
  Component image=glyph(image(first,4,'\ue000'));if(home)return image;
  String label=title.length()>20?title.substring(0,19)+"…":title;
  return image.append(glyph("\ue001")).append(Component.text(label,NamedTextColor.WHITE).font(DEFAULT));
 }
 static Component anvil(String title){String label=title.length()>20?title.substring(0,19)+"…":title;return glyph(image(0xe140,3,'\ue002')+"\ue003").append(Component.text(label,NamedTextColor.WHITE).font(DEFAULT));}
 static Component welcome(){return glyph("\ue150\ue005\ue151\ue005\ue006\ue152\ue005\ue153\ue005");}
}
