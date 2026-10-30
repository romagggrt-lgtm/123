package dev.zmorph;

import java.util.*;
import java.util.logging.Level;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZMorphPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    private record Session(EntityType type,MobDisguise disguise){}
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<String,EntityType> supported=new TreeMap<>();
    private static final LegacyComponentSerializer LEGACY=LegacyComponentSerializer.legacyAmpersand();
    @Override public void onEnable(){
        saveDefaultConfig();
        for(EntityType type:EntityType.values()){
            Class<?> cls=type.getEntityClass();
            if(cls==null||!Mob.class.isAssignableFrom(cls)||type==EntityType.PLAYER||!type.isSpawnable())continue;
            DisguiseType disguise=DisguiseType.getType(type);
            if(disguise!=null&&disguise.isMob()&&disguise.getEntityType()==type)supported.put(type.name().toLowerCase(Locale.ROOT),type);
        }
        Objects.requireNonNull(getCommand("morph")).setExecutor(this);getCommand("morph").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        getLogger().info("Доступно мобов: "+supported.size());
    }
    @Override public void onDisable(){for(Session s:new ArrayList<>(sessions.values()))try{s.disguise().removeDisguise();}catch(RuntimeException e){getLogger().log(Level.WARNING,"Не удалось снять маскировку",e);}sessions.clear();}
    private void msg(CommandSender sender,String key,String mob){
        String raw=getConfig().getString("messages.prefix","&d[Z_Morph] &r")+getConfig().getString("messages."+key,key);
        Component text=LEGACY.deserialize(raw).replaceText(b->b.matchLiteral("{mob}").replacement(Component.text(mob==null?"":mob)));sender.sendMessage(text);
    }
    private MobDisguise create(Player p,EntityType type){
        var d=new MobDisguise(DisguiseType.getType(type));
        d.setViewSelfDisguise(getConfig().getBoolean("view-self-disguise",false));
        d.setKeepDisguiseOnPlayerDeath(true);d.setModifyBoundingBox(false);
        d.getWatcher().setCustomNameVisible(false);d.getWatcher().setCustomName(null);
        if(getConfig().getBoolean("hide-equipment",true)){
            ItemStack air=new ItemStack(Material.AIR);
            d.getWatcher().setHelmet(air);d.getWatcher().setChestplate(air);d.getWatcher().setLeggings(air);d.getWatcher().setBoots(air);
            d.getWatcher().setItemInMainHand(air);d.getWatcher().setItemInOffHand(air);
        }
        d.setEntity(p);return d;
    }
    private boolean set(Player p,EntityType type){
        Session old=sessions.get(p.getUniqueId());Disguise existing=DisguiseAPI.getDisguise(p);
        if(existing!=null&&(old==null||existing!=old.disguise())){msg(p,"external",null);return false;}
        if(old!=null&&old.type()==type&&old.disguise().isDisguiseInUse()){msg(p,"already",type.name().toLowerCase(Locale.ROOT));return false;}
        MobDisguise candidate;
        try{candidate=create(p,type);}catch(RuntimeException e){getLogger().log(Level.WARNING,"Не удалось подготовить маскировку "+type,e);msg(p,"failure",null);return false;}
        try{
            if(old!=null&&old.disguise().isDisguiseInUse()&&!old.disguise().removeDisguise()){msg(p,"failure",null);return false;}
            if(!candidate.startDisguise()){
                if(old!=null&&p.isOnline())old.disguise().startDisguise();
                msg(p,"failure",null);return false;
            }
            sessions.put(p.getUniqueId(),new Session(type,candidate));msg(p,"active",type.name().toLowerCase(Locale.ROOT));return true;
        }catch(RuntimeException e){
            if(old!=null&&p.isOnline()&&!old.disguise().isDisguiseInUse())try{old.disguise().startDisguise();}catch(RuntimeException restore){getLogger().log(Level.WARNING,"Не удалось восстановить прежнюю маскировку",restore);}
            getLogger().log(Level.WARNING,"Ошибка применения маскировки",e);msg(p,"failure",null);return false;
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){msg(sender,"only-player",null);return true;}
        if(!p.hasPermission("zmorph.use")){msg(p,"no-permission",null);return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("remove")){
            Session old=sessions.get(p.getUniqueId());if(old==null){msg(p,"not-active",null);return true;}
            try{if(old.disguise().isDisguiseInUse()&&!old.disguise().removeDisguise()){msg(p,"failure",null);return true;}sessions.remove(p.getUniqueId());msg(p,"removed",null);}
            catch(RuntimeException e){getLogger().log(Level.WARNING,"Ошибка снятия маскировки",e);msg(p,"failure",null);}return true;
        }
        if(args.length==2&&args[0].equalsIgnoreCase("set")){
            EntityType type=supported.get(args[1].toLowerCase(Locale.ROOT));if(type==null){msg(p,"unknown",args[1]);return true;}set(p,type);return true;
        }
        msg(p,"usage",null);return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!(sender instanceof Player)||!sender.hasPermission("zmorph.use"))return List.of();
        if(args.length==1)return List.of("set","remove").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("set")){String prefix=args[1].toLowerCase(Locale.ROOT);return supported.keySet().stream().filter(s->s.startsWith(prefix)).toList();}
        return List.of();
    }
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){Session old=sessions.remove(e.getPlayer().getUniqueId());if(old!=null)old.disguise().removeDisguise();}
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e){
        UUID id=e.getPlayer().getUniqueId();Session old=sessions.get(id);if(old==null)return;
        Bukkit.getScheduler().runTask(this,()->{Player p=Bukkit.getPlayer(id);Session current=sessions.get(id);if(p!=null&&current==old&&!current.disguise().isDisguiseInUse()){sessions.remove(id);set(p,current.type());}});
    }
}
