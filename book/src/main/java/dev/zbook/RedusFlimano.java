package dev.zbook;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.inventory.ItemStack;

/** A real vanilla projectile, rendered with the three-dimensional resource-pack model. */
final class RedusFlimano implements Spell {
 public String id(){return "redus_flimano";}
 public long cooldownMillis(ZBookPlugin p){return Math.clamp(p.getConfig().getLong(configPath()+".cooldown-seconds",40),0,86400)*1000L;}
 public double range(ZBookPlugin p){return Math.clamp(p.getConfig().getDouble(configPath()+".range",60),1,128);}
 public boolean cast(Player player,ZBookPlugin plugin){
  ItemStack visual=new ItemStack(Material.FIRE_CHARGE);
  visual.editMeta(m->m.setItemModel(new NamespacedKey("zbook","redus_flimano_projectile")));
  SmallFireball fireball=player.launchProjectile(SmallFireball.class,player.getEyeLocation().getDirection().normalize().multiply(1.2));
  if(!fireball.isValid())return false;
  fireball.setDisplayItem(visual);
  fireball.setIsIncendiary(plugin.getConfig().getBoolean(configPath()+".incendiary",false));
  player.playSound(player.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.8f,0.9f);
  // Bound projectile lifetime to avoid accumulating projectiles in unloaded voids.
  long ticks=Math.clamp((long)Math.ceil(range(plugin)/1.2),2L,200L);
  org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,()->{if(fireball.isValid())fireball.remove();},ticks);
  return true;
 }
}
