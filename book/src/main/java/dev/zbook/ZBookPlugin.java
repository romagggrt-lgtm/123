package dev.zbook;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ZBookPlugin extends JavaPlugin {
 private SpellRegistry registry;private BookItems items;private BookUI ui;private CastManager casts;private Text text;private NamespacedKey protectedCaster;
 @Override public void onEnable(){saveDefaultConfig();text=new Text(this);protectedCaster=new NamespacedKey(this,"lightning_protected_caster");registry=new SpellRegistry();registry.register(new EkspanorFanfiction());items=new BookItems(this);casts=new CastManager(this);ui=new BookUI(this);var pm=Bukkit.getPluginManager();pm.registerEvents(casts,this);pm.registerEvents(new CraftHandler(this),this);BookCommand executor=new BookCommand(this);PluginCommand command=Objects.requireNonNull(getCommand("zbook"));command.setExecutor(executor);command.setTabCompleter(executor);}
 SpellRegistry registry(){return registry;}BookItems items(){return items;}BookUI ui(){return ui;}CastManager casts(){return casts;}Text text(){return text;}NamespacedKey protectedCasterKey(){return protectedCaster;}
}
