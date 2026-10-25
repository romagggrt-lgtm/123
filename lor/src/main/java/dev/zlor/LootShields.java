package dev.zlor;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Marks the native death drops; never recreates an inventory or its item stacks. */
public final class LootShields implements Listener,AutoCloseable {
    public record Zone(UUID id,Location center,long expires){}
    private record Death(Zone zone,long tick,List<ItemStack> expected){}
    private final ZLorPlugin p;
    private final Map<UUID,Zone> zones=new HashMap<>();
    private final Map<UUID,Item> items=new HashMap<>();
    private final List<Death> deaths=new ArrayList<>();
    private final NamespacedKey expiry=new NamespacedKey("z_lor","loot_expiry"),zoneKey=new NamespacedKey("z_lor","loot_zone"),
            origin=new NamespacedKey("z_lor","loot_origin"),gravity=new NamespacedKey("z_lor","loot_gravity"),invulnerable=new NamespacedKey("z_lor","loot_invulnerable");
    public LootShields(ZLorPlugin p){this.p=p;}
    public List<Zone> zones(){return zones.values().stream().filter(z->z.expires>p.now()).toList();}
    public boolean protectsItem(Entity entity){return entity instanceof Item&&entity.getPersistentDataContainer().getOrDefault(expiry,PersistentDataType.LONG,0L)>p.now();}
    public boolean inShield(Location at){return zones.values().stream().anyMatch(z->z.expires>p.now()&&z.center.getWorld().equals(at.getWorld())&&z.center.distanceSquared(at)<=9);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void resurrect(EntityResurrectEvent event){
        if(!(event.getEntity() instanceof Player player)||player.getGameMode()!=GameMode.SURVIVAL||player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY)==Boolean.TRUE)return;
        var damage=player.getLastDamageCause();if(damage!=null&&damage.getCause()==EntityDamageEvent.DamageCause.VOID)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event){
        Player player=event.getEntity();var damage=player.getLastDamageCause();
        if(damage==null||damage.getCause()!=EntityDamageEvent.DamageCause.VOID||event.getKeepInventory()||player.getGameMode()!=GameMode.SURVIVAL)return;
        ItemStack totem=event.getDrops().stream().filter(i->i.getType()==Material.TOTEM_OF_UNDYING&&i.getAmount()>0).findFirst().orElse(null);
        if(totem==null)return;totem.subtract(1);event.getDrops().removeIf(ItemStack::isEmpty);
        var zone=new Zone(UUID.randomUUID(),player.getLocation().clone(),p.now()+300_000);zones.put(zone.id,zone);
        deaths.add(new Death(zone,p.tick,event.getDrops().stream().map(ItemStack::clone).toList()));
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        player.getWorld().playSound(player.getLocation(),Sound.ITEM_TOTEM_USE,SoundCategory.PLAYERS,1,1);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawn(ItemSpawnEvent event){
        Item item=event.getEntity();
        for(var death:deaths){
            if(p.tick-death.tick>1||!death.zone.center.getWorld().equals(item.getWorld())||death.zone.center.distanceSquared(item.getLocation())>9)continue;
            for(var expected:death.expected)if(expected.isSimilar(item.getItemStack())&&expected.getAmount()>=item.getItemStack().getAmount()){
                expected.subtract(item.getItemStack().getAmount());mark(item,death.zone);return;
            }
        }
        track(item);
    }
    private void mark(Item item,Zone zone){
        var data=item.getPersistentDataContainer();data.set(expiry,PersistentDataType.LONG,zone.expires);
        data.set(zoneKey,PersistentDataType.STRING,zone.id.toString());
        data.set(origin,PersistentDataType.LONG_ARRAY,new long[]{Double.doubleToLongBits(zone.center.getX()),Double.doubleToLongBits(zone.center.getY()),Double.doubleToLongBits(zone.center.getZ())});
        data.set(gravity,PersistentDataType.BYTE,(byte)(item.hasGravity()?1:0));data.set(invulnerable,PersistentDataType.BYTE,(byte)(item.isInvulnerable()?1:0));
        item.setGravity(false);item.setInvulnerable(true);item.setVelocity(new Vector());items.put(item.getUniqueId(),item);
    }
    public void track(Item item){
        var data=item.getPersistentDataContainer();long until=data.getOrDefault(expiry,PersistentDataType.LONG,0L);if(until==0)return;
        if(until<=p.now()){restore(item,true);return;}
        try{
            UUID id=UUID.fromString(data.get(zoneKey,PersistentDataType.STRING));long[] coordinates=data.get(origin,PersistentDataType.LONG_ARRAY);
            if(coordinates==null||coordinates.length!=3)throw new IllegalArgumentException();
            zones.putIfAbsent(id,new Zone(id,new Location(item.getWorld(),Double.longBitsToDouble(coordinates[0]),Double.longBitsToDouble(coordinates[1]),Double.longBitsToDouble(coordinates[2])),until));
            item.setGravity(false);item.setInvulnerable(true);items.put(item.getUniqueId(),item);
        }catch(RuntimeException error){restore(item,true);}
    }
    private void restore(Item item,boolean clear){
        var data=item.getPersistentDataContainer();
        item.setGravity(data.getOrDefault(gravity,PersistentDataType.BYTE,(byte)1)!=0);
        item.setInvulnerable(data.getOrDefault(invulnerable,PersistentDataType.BYTE,(byte)0)!=0);
        if(clear)for(var key:List.of(expiry,zoneKey,origin,gravity,invulnerable))data.remove(key);
        items.remove(item.getUniqueId());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void merge(ItemMergeEvent event){
        boolean from=protectsItem(event.getEntity()),to=protectsItem(event.getTarget());
        if(from!=to||from&&!Objects.equals(event.getEntity().getPersistentDataContainer().get(zoneKey,PersistentDataType.STRING),event.getTarget().getPersistentDataContainer().get(zoneKey,PersistentDataType.STRING)))event.setCancelled(true);
    }
    @EventHandler public void load(ChunkLoadEvent event){for(Entity entity:event.getChunk().getEntities())if(entity instanceof Item item)track(item);}
    public void tick(){
        deaths.removeIf(d->p.tick-d.tick>1);zones.values().removeIf(z->z.expires<=p.now());
        for(Item item:new ArrayList<>(items.values())){
            if(!item.isValid()){items.remove(item.getUniqueId());continue;}
            if(!protectsItem(item)){restore(item,true);continue;}
            item.setGravity(false);item.setVelocity(new Vector());
        }
    }
    @Override public void close(){for(Item item:new ArrayList<>(items.values()))if(item.isValid())restore(item,false);items.clear();zones.clear();}
}
