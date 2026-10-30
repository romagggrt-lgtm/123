package dev.zlor;

import io.papermc.paper.potion.PotionMix;
import java.util.*;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import dev.zlor.rules.DragonRules;

/** Native Paper brewing recipes; custom effects do not change beam attack counts. */
public class Brewing implements Listener,AutoCloseable {
    private enum Kind { LEVITATION, LEVITATION_LONG, EROSION_1, EROSION_2, RESTORATION }
    private record Active(int level,long until,long next){}
    private final ZLorPlugin p;
    private final NamespacedKey key=new NamespacedKey("z_lor","brew"),cloudKey=new NamespacedKey("z_lor","brew_cloud");
    private final List<NamespacedKey> recipes=new ArrayList<>();
    private final Map<UUID,Active> active=new HashMap<>();
    private final Map<UUID,Integer> damaging=new HashMap<>();
    private static final List<Material> FORMS=List.of(Material.POTION,Material.SPLASH_POTION,Material.LINGERING_POTION);
    public Brewing(ZLorPlugin p){this.p=p;}
    private int seconds(String name,int fallback,int max){return Math.clamp(p.getConfig().getInt("brewing."+name,fallback),1,max);}
    private int levitation(Kind k){return seconds(k==Kind.LEVITATION_LONG?"levitation-long-seconds":"levitation-seconds",k==Kind.LEVITATION_LONG?30:10,600);}
    private Kind kind(ItemStack item){
        if(item==null||!FORMS.contains(item.getType())||!(item.getItemMeta() instanceof PotionMeta meta))return null;
        String id=meta.getPersistentDataContainer().get(key,PersistentDataType.STRING);if(id==null)return null;
        try{return Kind.valueOf(id);}catch(IllegalArgumentException bad){return null;}
    }
    public ItemStack potion(String id,Material form){return potion(Kind.valueOf(id),form);}
    private ItemStack potion(Kind kind,Material form){
        if(!FORMS.contains(form))throw new IllegalArgumentException("Unknown potion form");
        ItemStack result=new ItemStack(form);PotionMeta meta=(PotionMeta)result.getItemMeta();meta.setBasePotionType(PotionType.MUNDANE);
        String base=switch(kind){case LEVITATION,LEVITATION_LONG->"левитации";case EROSION_1->"эрозии I";case EROSION_2->"эрозии II";case RESTORATION->"восстановления";};
        String prefix=switch(form){case SPLASH_POTION->"Взрывное зелье ";case LINGERING_POTION->"Оседающее зелье ";default->"Зелье ";};
        meta.displayName(Component.text(prefix+base,NamedTextColor.LIGHT_PURPLE));
        meta.setColor(Color.fromRGB(switch(kind){case LEVITATION,LEVITATION_LONG->0xC6A8EC;case EROSION_1->0x4A145E;case EROSION_2->0x1B0824;case RESTORATION->0xE4B8E7;}));
        meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,kind.name());meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if(kind==Kind.LEVITATION||kind==Kind.LEVITATION_LONG){
            int duration=levitation(kind);meta.addCustomEffect(new PotionEffect(PotionEffectType.LEVITATION,duration*20,0),true);
            meta.lore(List.of(Component.text("Левитация I ("+duration+" сек.)",NamedTextColor.GRAY)));
        }else if(kind==Kind.RESTORATION)meta.lore(List.of(Component.text("Возвращает одно потерянное сердце",NamedTextColor.GRAY),Component.text("максимального здоровья от луча.",NamedTextColor.GRAY)));
        else meta.lore(List.of(Component.text("Эрозия "+(kind==Kind.EROSION_1?"I":"II")+" (20 сек.)",NamedTextColor.GRAY),Component.text(kind==Kind.EROSION_1?"1 HP каждые 2 секунды; не убивает.":"2 HP каждые 2 секунды; может убить.",NamedTextColor.DARK_PURPLE)));
        result.setItemMeta(meta);return result;
    }
    private void add(String id,ItemStack result,Predicate<ItemStack> in,Predicate<ItemStack> ingredient){
        NamespacedKey recipe=new NamespacedKey("z_lor","brew_"+id);
        addMix(new PotionMix(recipe,result,PotionMix.createPredicateChoice(in),PotionMix.createPredicateChoice(ingredient)));recipes.add(recipe);
    }
    protected void addMix(PotionMix mix){Bukkit.getPotionBrewer().removePotionMix(mix.getKey());Bukkit.getPotionBrewer().addPotionMix(mix);}
    protected void removeMix(NamespacedKey recipe){Bukkit.getPotionBrewer().removePotionMix(recipe);}
    private static boolean ingredient(ItemStack item,Material material){return item!=null&&item.getType()==material;}
    private static boolean awkward(ItemStack item){
        return item!=null&&item.getType()==Material.POTION&&item.getItemMeta() instanceof PotionMeta m&&m.getBasePotionType()==PotionType.AWKWARD&&!m.hasCustomEffects();
    }
    public void register(){
        add("levitation",potion(Kind.LEVITATION,Material.POTION),Brewing::awkward,i->ingredient(i,Material.SHULKER_SHELL));
        add("erosion",potion(Kind.EROSION_1,Material.POTION),Brewing::awkward,i->p.items.is(i,"void_breath"));
        for(Material form:FORMS){
            String suffix=form.name().toLowerCase(Locale.ROOT);
            add("strong_"+suffix,potion(Kind.EROSION_2,form),i->i!=null&&i.getType()==form&&kind(i)==Kind.EROSION_1,i->ingredient(i,Material.GLOWSTONE_DUST));
            add("restore_"+suffix,potion(Kind.RESTORATION,form),i->i!=null&&i.getType()==form&&kind(i)==Kind.EROSION_1,i->ingredient(i,Material.GHAST_TEAR));
            add("long_"+suffix,potion(Kind.LEVITATION_LONG,form),i->i!=null&&i.getType()==form&&kind(i)==Kind.LEVITATION,i->ingredient(i,Material.REDSTONE));
        }
        for(Kind k:Kind.values()){
            add("splash_"+k.name().toLowerCase(Locale.ROOT),potion(k,Material.SPLASH_POTION),i->i!=null&&i.getType()==Material.POTION&&kind(i)==k,i->ingredient(i,Material.GUNPOWDER));
            add("lingering_"+k.name().toLowerCase(Locale.ROOT),potion(k,Material.LINGERING_POTION),i->i!=null&&i.getType()==Material.SPLASH_POTION&&kind(i)==k,i->ingredient(i,Material.DRAGON_BREATH));
        }
    }
    @Override public void close(){for(NamespacedKey k:recipes)removeMix(k);recipes.clear();active.clear();damaging.clear();}
    private void effect(LivingEntity entity,Kind kind){
        if(kind==Kind.RESTORATION){if(entity instanceof Player player)restore(player);return;}
        if(kind!=Kind.EROSION_1&&kind!=Kind.EROSION_2)return;
        int level=kind==Kind.EROSION_2?2:1;Active old=active.get(entity.getUniqueId());long next=p.tick+40;
        if(old!=null&&old.until>=p.tick){level=Math.max(level,old.level);next=Math.max(p.tick+1,old.next);}
        active.put(entity.getUniqueId(),new Active(level,p.tick+400,next));
    }
    private void restore(Player player){
        UUID id=player.getUniqueId();
        p.store.change(s->{int loss=DragonRules.lost(s.beamHits.getOrDefault(id,0)),old=s.beamRestored.getOrDefault(id,0);s.beamRestored.put(id,Math.min(loss,old+2));},
            state->{Player live=Bukkit.getPlayer(id);if(live!=null)p.dragons.applyMaxLoss(live);},
            error->p.getLogger().warning("Не удалось сохранить восстановление сердца: "+error));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void consume(PlayerItemConsumeEvent event){Kind k=kind(event.getItem());if(k!=null)effect(event.getPlayer(),k);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void splash(PotionSplashEvent event){Kind k=kind(event.getPotion().getItem());if(k==null)return;
        for(LivingEntity entity:event.getAffectedEntities())if(event.getIntensity(entity)>0)effect(entity,k);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void lingering(LingeringPotionSplashEvent event){Kind k=kind(event.getEntity().getItem());if(k==null)return;
        var cloud=event.getAreaEffectCloud();cloud.getPersistentDataContainer().set(cloudKey,PersistentDataType.STRING,k.name());
        if(k!=Kind.LEVITATION&&k!=Kind.LEVITATION_LONG){
            // A one-tick hidden marker makes vanilla run its normal cloud application
            // loop; vanilla scales it to zero, so it does not apply a side effect.
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.UNLUCK,1,0,false,false,false),true);
        }
        event.allowsEmptyCreation(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void cloud(AreaEffectCloudApplyEvent event){String name=event.getEntity().getPersistentDataContainer().get(cloudKey,PersistentDataType.STRING);if(name==null)return;
        try{Kind k=Kind.valueOf(name);for(LivingEntity entity:event.getAffectedEntities())effect(entity,k);}catch(IllegalArgumentException ignored){}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void erosionDamage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof LivingEntity entity)||damaging.getOrDefault(entity.getUniqueId(),0)!=1)return;
        if(event.getFinalDamage()>Math.max(0,entity.getHealth()-1)+entity.getAbsorptionAmount())event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void death(PlayerDeathEvent event){if(damaging.getOrDefault(event.getEntity().getUniqueId(),0)==2)p.loot.protectErosionDrops(event);active.remove(event.getEntity().getUniqueId());}
    protected void deliverDamage(LivingEntity victim,double damage){victim.damage(damage,DamageSource.builder(DamageType.MAGIC).build());}
    public void tick(){
        for(var entry:new ArrayList<>(active.entrySet())){
            UUID id=entry.getKey();Active state=entry.getValue();Entity entity=Bukkit.getEntity(id);
            if(!(entity instanceof LivingEntity victim)||victim.isDead()){active.remove(id);continue;}
            if(p.tick>=state.next&&state.next<=state.until){
                double damage=state.level==1?Math.min(1,Math.max(0,victim.getHealth()-1)+victim.getAbsorptionAmount()):2;
                if(damage>0){damaging.put(id,state.level);try{deliverDamage(victim,damage);}finally{damaging.remove(id);}}
                if(!victim.isDead()&&active.containsKey(id))active.put(id,new Active(state.level,state.until,state.next+40));
            }
            if(p.tick>=state.until)active.remove(id);
        }
    }
}
