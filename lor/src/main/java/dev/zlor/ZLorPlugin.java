package dev.zlor;

import java.time.Duration;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

public class ZLorPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    public StateStore store;
    public Roles roles;
    public Items items;
    public Confirmations confirmations;
    public NativeBoundary nativeBoundary;
    public Voids voids;
    public Devices devices;
    public Redstone redstone;
    public LootShields loot;
    public Restrictions restrictions;
    public Structures structures;
    public Dragons dragons;
    public Visuals visuals;
    public Menus menus;
    public Audio audio;
    public LorCommands commands;
    public ChunkLeases tickets;
    public CrystalRestorer crystals;
    public long tick;
    protected LongSupplier wall=System::currentTimeMillis,nanos=System::nanoTime;
    private long fuelBase,fuelStarted,lastWall;
    private boolean stopping;
    private final Map<UUID,PlayerResourcePackStatusEvent.Status> packStatuses=new HashMap<>();
    protected NativeBoundary createNativeBoundary(){return new NativeBoundary(this);}
    public long now(){return lastWall=Math.max(lastWall,wall.getAsLong());}
    public long fuelNow(){return fuelBase+Math.max(0,nanos.getAsLong()-fuelStarted)/1_000_000;}
    public State state(){return store.state();}
    public String worldKey(World world){return world.getKey().asString();}
    public World world(String key){return Bukkit.getWorlds().stream().filter(w->worldKey(w).equals(key)).findFirst().orElse(null);}
    public UUID packId(){var pack=Bukkit.getServer().getServerResourcePack();return pack!=null?pack.getId():UUID.fromString("17e36816-ac9c-57bc-8a02-d7a0221a689e");}

    @Override public void onEnable(){
        saveDefaultConfig();
        if(migrate(getConfig()))saveConfig();
        var legacy=Bukkit.getPluginManager().getPlugin("SmpLore");
        if(legacy!=null&&legacy.isEnabled()){getLogger().severe("Уберите старый SmpLore.jar перед запуском Z_Lor: два контроллера нельзя запускать вместе");Bukkit.getPluginManager().disablePlugin(this);return;}
        try{
            store=new StateStore(getDataFolder().toPath().resolve("state.json"),e->getLogger().log(Level.SEVERE,"Хранилище Z_Lor: изменения остановлены, исходные данные сохранены",e));
            fuelBase=state().fuelClock;fuelStarted=nanos.getAsLong();
            if(state().devices.values().stream().anyMatch(d->d.linkPending))store.change(s->{
                for(var d:s.devices.values())if(d.linkPending){if(d.peer!=null){var peer=s.devices.get(d.peer);if(peer!=null){peer.peer=null;peer.linkPending=false;}}d.peer=null;d.linkPending=false;}
            });
            roles=new Roles(this);items=new Items(this);confirmations=new Confirmations(this);voids=new Voids(this);
            tickets=new ChunkLeases(this);crystals=new CrystalRestorer(this);
            devices=new Devices(this);redstone=new Redstone(this);loot=new LootShields(this);restrictions=new Restrictions(this);
            structures=new Structures(this);dragons=new Dragons(this);visuals=new Visuals(this);menus=new Menus(this);
            audio=new Audio(this);nativeBoundary=createNativeBoundary();commands=new LorCommands(this);
            items.registerRecipes();
            for(Listener listener:List.of(this,voids,devices,redstone,loot,restrictions,structures,dragons,visuals,menus))Bukkit.getPluginManager().registerEvents(listener,this);
            Objects.requireNonNull(getCommand("lore")).setExecutor(this);getCommand("lore").setTabCompleter(this);
            Objects.requireNonNull(getCommand("scen")).setExecutor(this);getCommand("scen").setTabCompleter(this);
            for(World world:Bukkit.getWorlds()){nativeBoundary.attach(world);structures.recover(world);}
            dragons.restore();for(var device:devices.loaded())devices.refresh(device);
            for(Player player:Bukkit.getOnlinePlayers())joined(player);
            Bukkit.getScheduler().runTaskTimer(this,this::runTick,1,1);
            getLogger().info("Z_Lor "+getDescription().getVersion()+": Paper 1.21.11 / Java 21. Общий RP задаётся в server.properties.");
        }catch(Exception error){getLogger().log(Level.SEVERE,"Z_Lor не запущен; данные не сброшены",error);Bukkit.getPluginManager().disablePlugin(this);}
    }
    private void runTick(){
        if(stopping)return;
        try{
            tick++;store.pulse();audio.beginTick();voids.tick();devices.tick();redstone.tick();restrictions.tick();
            loot.tick();structures.tick();crystals.tick();dragons.tick();visuals.tick();audio.tick();confirmations.expire();
            if(tick%Math.max(20,getConfig().getInt("storage.checkpoint-ticks",200))==0&&store.idle())checkpoint();
        }catch(Exception error){getLogger().log(Level.SEVERE,"Z_Lor остановлен после ошибки игрового цикла; данные сохранены",error);Bukkit.getPluginManager().disablePlugin(this);}
    }
    public static boolean migrate(org.bukkit.configuration.file.FileConfiguration config){
        if(config.contains("config-version",true)&&config.getInt("config-version")>=2)return false;
        if(!config.contains("dragons.beam-active-ticks",true)||config.getInt("dragons.beam-active-ticks")==60)config.set("dragons.beam-active-ticks",200);
        if(!config.contains("dragons.beam-fade-ticks",true))config.set("dragons.beam-fade-ticks",60);
        config.set("config-version",2);return true;
    }
    public void checkpoint(){
        if(store==null||!store.healthy())return;
        store.change(s->{s.fuelClock=fuelNow();dragons.capture(s);});
    }
    public boolean protectedAt(Entity entity){
        if(entity instanceof Player player&&state().immune.contains(player.getUniqueId()))return true;
        return loot.protectsItem(entity)||devices.protectedAt(entity.getLocation())||(entity instanceof Player&&structures.protects(entity.getLocation()));
    }
    public boolean stopping(){return stopping;}
    public void change(Player player,String detail,Consumer<State> mutation,Consumer<State> effect){
        String key=worldKey(player.getWorld());
        store.change(s->{
            if(!player.isOnline()||!s.roles.containsKey(player.getUniqueId())||!worldKey(player.getWorld()).equals(key))throw new IllegalArgumentException("Доступ или измерение изменились");
            mutation.accept(s);roles.audit(s,player,"команда/меню",detail,key);
        },effect,e->error(player,e));
    }
    public void error(CommandSender sender,Throwable error){
        String message=error.getMessage();sender.sendMessage(Component.text(message==null?"Не удалось выполнить действие":message,net.kyori.adventure.text.format.NamedTextColor.RED));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{if(command.getName().equals("scen"))roles.command(sender,args);else commands.execute(sender,args);}
        catch(IllegalArgumentException error){error(sender,error);}catch(Exception error){getLogger().log(Level.SEVERE,"Ошибка команды Z_Lor",error);error(sender,new IllegalArgumentException("Команда не выполнена; подробности в журнале сервера"));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){return commands.complete(sender,command.getName(),args);}
    public void clearPersonal(Player player){
        if(confirmations!=null)confirmations.clear(player.getUniqueId());if(menus!=null)menus.clear(player);
        if(visuals!=null)visuals.leave(player.getUniqueId());if(voids!=null)voids.leave(player.getUniqueId());
        if(restrictions!=null)restrictions.leave(player.getUniqueId());if(devices!=null)devices.leave(player.getUniqueId());if(audio!=null){audio.stopPreview(player);audio.cancel(player);}
    }
    private void joined(Player player){
        store.change(s->s.names.put(player.getUniqueId(),player.getName()));dragons.applyMaxLoss(player);
        // Never stack a plugin offer on a server.properties pack.
        if(getConfig().getBoolean("resource-pack.enabled",false)&&Bukkit.getServer().getResourcePack().isBlank()){
            String url=getConfig().getString("resource-pack.url","");String hash=getConfig().getString("resource-pack.sha1","");
            if(!url.isBlank()&&hash.matches("[0-9a-fA-F]{40}"))player.setResourcePack(packId(),url,HexFormat.of().parseHex(hash),Component.text("Серверные текстуры и звуки"),getConfig().getBoolean("resource-pack.required",false));
        }
    }
    @EventHandler public void join(PlayerJoinEvent event){joined(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){clearPersonal(event.getPlayer());packStatuses.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent event){clearPersonal(event.getEntity());}
    @EventHandler public void respawn(PlayerRespawnEvent event){Bukkit.getScheduler().runTask(this,()->{if(event.getPlayer().isOnline())dragons.applyMaxLoss(event.getPlayer());});}
    @EventHandler public void worldChange(PlayerChangedWorldEvent event){clearPersonal(event.getPlayer());}
    @EventHandler public void pack(PlayerResourcePackStatusEvent event){packStatuses.put(event.getPlayer().getUniqueId(),event.getStatus());}
    @EventHandler public void commands(PlayerCommandSendEvent event){
        if(!roles.scen(event.getPlayer()))event.getCommands().removeIf(name->Set.of("lore","zlor","z_lor:lore","z_lor:zlor","smplore:lore").contains(name.toLowerCase(Locale.ROOT)));
        if(!roles.scen(event.getPlayer())&&!roles.admin(event.getPlayer()))event.getCommands().removeIf(name->Set.of("scen","z_lor:scen").contains(name.toLowerCase(Locale.ROOT)));
    }
    @EventHandler public void worldLoad(WorldLoadEvent event){if(nativeBoundary!=null)nativeBoundary.attach(event.getWorld());}
    @EventHandler public void worldUnload(WorldUnloadEvent event){if(nativeBoundary!=null)nativeBoundary.detach(event.getWorld());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void crafted(CraftItemEvent event){
        String id=items.id(event.getRecipe().getResult());if(id==null)return;
        if(event.getWhoClicked() instanceof Player player){var key=items.recipe(id);if(key!=null)Bukkit.getScheduler().runTask(this,()->player.discoverRecipe(key));}
    }
    public String packStatus(Player player){var status=packStatuses.get(player.getUniqueId());return status==null?"Статус ещё не получен":status.name();}
    @Override public void onDisable(){
        stopping=true;
        if(store!=null&&dragons!=null&&store.healthy()){
            State snapshot=store.copy();dragons.capture(snapshot);long clock=fuelNow();
            store.change(s->{s.fuelClock=clock;for(var entry:snapshot.dragons.entrySet()){
                var current=s.dragons.get(entry.getKey());var captured=entry.getValue();
                if(current!=null&&current.status.equals(captured.status)){current.health=captured.health;current.x=captured.x;current.y=captured.y;current.z=captured.z;current.kingQuietTicks=captured.kingQuietTicks;current.kingNextRepair=captured.kingNextRepair;current.summonRemaining=captured.summonRemaining;}
            }});store.suppressEffects();
        }
        if(nativeBoundary!=null)nativeBoundary.close();if(audio!=null)audio.close();if(visuals!=null)visuals.close();if(menus!=null)menus.close();
        if(crystals!=null)crystals.close();if(redstone!=null)redstone.close();if(tickets!=null)tickets.close();if(devices!=null)devices.close();if(loot!=null)loot.close();if(dragons!=null)dragons.close();if(items!=null)items.unregisterRecipes();
        if(store!=null&&!store.finish(Duration.ofSeconds(3)))getLogger().severe("Хранилище не завершило запись за 3 секунды или сообщило об ошибке; сохраните state.json и .bak");
    }
}
