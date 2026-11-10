package dev.zbook;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class EkspanorFanfiction implements Spell {
 public String id(){return "ekspanor_fanfiction";}
 public long cooldownMillis(ZBookPlugin plugin){return Math.max(0,Math.min(86400,plugin.getConfig().getLong(configPath()+".cooldown-seconds",45)))*1000L;}
 public double range(ZBookPlugin plugin){return Math.clamp(plugin.getConfig().getDouble(configPath()+".range",60),1,128);}
 public boolean cast(Player p,ZBookPlugin plugin){
  Location origin=p.getEyeLocation();Vector direction=origin.getDirection().normalize();double range=range(plugin);
  RayTraceResult hit=p.getWorld().rayTrace(origin,direction,range,FluidCollisionMode.NEVER,true,0.2,e->!e.getUniqueId().equals(p.getUniqueId()));
  if(hit==null){plugin.text().send(p,"too-far");return false;}
  Location target=hit.getHitPosition().toLocation(p.getWorld());
  LightningStrike bolt=plugin.getConfig().getBoolean(configPath()+".lightning.real",true)?p.getWorld().strikeLightning(target):p.getWorld().strikeLightningEffect(target);
  if(bolt==null||!bolt.isValid()){plugin.text().send(p,"blocked");return false;}
  bolt.setCausingPlayer(p);
  if(!plugin.getConfig().getBoolean(configPath()+".lightning.affect-caster",false))bolt.getPersistentDataContainer().set(plugin.protectedCasterKey(),PersistentDataType.STRING,p.getUniqueId().toString());
  double length=Math.min(range,origin.distance(target));for(double d=1;d<length;d+=1.5)p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,origin.clone().add(direction.clone().multiply(d)),1,0.03,0.03,0.03,0);
  p.playSound(p.getLocation(),Sound.ENTITY_LIGHTNING_BOLT_THUNDER,0.8f,1.1f);return true;
 }
}
