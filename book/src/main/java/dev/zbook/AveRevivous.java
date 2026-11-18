package dev.zbook;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/** Instant self-healing, with the usual server regain-health event. */
final class AveRevivous implements Spell {
 public String id(){return "ave_revivous";}
 public long cooldownMillis(ZBookPlugin plugin){return Math.clamp(plugin.getConfig().getLong(configPath()+".cooldown-seconds",30),0,86400)*1000L;}
 public double range(ZBookPlugin plugin){return 0;}
 public boolean cast(Player player,ZBookPlugin plugin){
  var attribute=player.getAttribute(Attribute.MAX_HEALTH);if(attribute==null||player.isDead())return false;
  double before=player.getHealth(),missing=attribute.getValue()-before;if(missing<=0.0001){plugin.text().send(player,"health-full");return false;}
  player.heal(missing,EntityRegainHealthEvent.RegainReason.MAGIC);
  if(player.getHealth()<=before){plugin.text().send(player,"heal-blocked");return false;}
  player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,player.getLocation().add(0,1,0),24,.35,.6,.35,.05);
  player.getWorld().spawnParticle(Particle.HEART,player.getLocation().add(0,1.1,0),4,.3,.5,.3,0);
  player.getWorld().playSound(player.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,.65f,1.4f);return true;
 }
}
