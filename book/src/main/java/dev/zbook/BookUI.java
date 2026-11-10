package dev.zbook;
import java.time.Duration;
import java.util.*;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class BookUI {
 private final ZBookPlugin plugin;
 BookUI(ZBookPlugin plugin){this.plugin=plugin;}
 private Component line(String path){return plugin.text().get(path).append(Component.newline());}
 void open(Player p,ItemStack item){
  Set<String> learned=plugin.items().learned(item);String bookId=plugin.items().id(item);if(bookId==null)return;
  List<Spell> spells=plugin.registry().all().stream().filter(s->learned.contains(s.id())).limit(99).toList();
  Component index=line("book.index-title").append(line("book.index-header"));
  if(spells.isEmpty())for(String s:plugin.text().lines("book.empty-lines"))index=index.append(plugin.text().component(s)).append(Component.newline());
  else for(int n=0;n<spells.size();n++){Spell spell=spells.get(n);Component link=plugin.text().format("book.index-spell","name",spell.name(plugin.text())).clickEvent(ClickEvent.changePage(n+2)).hoverEvent(HoverEvent.showText(plugin.text().get("book.index-hover")));index=index.append(link).append(Component.newline());}
  List<Component> pages=new ArrayList<>();pages.add(index);UUID owner=p.getUniqueId();
  for(Spell spell:spells){
   Component page=line("book.page-header").append(plugin.text().component(spell.name(plugin.text()))).append(Component.newline()).append(line("book.page-header"));
   for(String text:spell.description(plugin.text()))page=page.append(plugin.text().component(text)).append(Component.newline());
   page=page.append(Component.newline()).append(plugin.text().format("book.cooldown","seconds",spell.cooldownMillis(plugin)/1000)).append(Component.newline()).append(plugin.text().format("book.range","range",Math.round(spell.range(plugin)))).append(Component.newline()).append(Component.newline());
   Component use=plugin.text().get("book.use").hoverEvent(HoverEvent.showText(plugin.text().get("book.use-hover"))).clickEvent(ClickEvent.callback(audience->{if(plugin.isEnabled()&&audience instanceof Player target&&target.isOnline()&&target.getUniqueId().equals(owner))plugin.casts().select(target,spell,bookId);},ClickCallback.Options.builder().uses(10).lifetime(Duration.ofMinutes(10)).build()));
   page=page.append(use).append(Component.newline()).append(Component.newline()).append(plugin.text().get("book.back").clickEvent(ClickEvent.changePage(1)).hoverEvent(HoverEvent.showText(plugin.text().get("book.back-hover"))));pages.add(page);
  }
  p.openBook(Book.book(plugin.text().get("book.title"),plugin.text().get("book.author"),pages));
 }
}
