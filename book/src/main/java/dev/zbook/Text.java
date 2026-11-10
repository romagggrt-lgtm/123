package dev.zbook;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

final class Text {
 private static final LegacyComponentSerializer SERIALIZER=LegacyComponentSerializer.builder().character('&').hexColors().build();
 private final JavaPlugin plugin;
 Text(JavaPlugin plugin){this.plugin=plugin;}
 String raw(String path){return plugin.getConfig().getString(path, "");}
 List<String> lines(String path){return plugin.getConfig().getStringList(path);}
 Component component(String value){return Component.empty().decoration(TextDecoration.ITALIC,false).append(SERIALIZER.deserialize(value));}
 Component get(String path){return component(raw(path));}
 String replace(String input, Object... pairs){String result=input;for(int i=0;i+1<pairs.length;i+=2)result=result.replace("{"+pairs[i]+"}",String.valueOf(pairs[i+1]));return result;}
 Component format(String path,Object... pairs){return component(replace(raw(path),pairs));}
 void send(CommandSender sender,String path,Object... pairs){sender.sendMessage(get("messages.prefix").append(format("messages."+path,pairs)));}
}
