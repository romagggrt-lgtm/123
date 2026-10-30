package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.boss.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

public final class Dragons implements Listener,AutoCloseable {
    public static final int[][] WAVES={{1,0},{0,1},{2,0},{2,0},{1,1},{0,2},{2,1},{1,2},{0,3},{0,4}};
    public static final class Body {
        public final UUID id;public final EnderDragon entity;public final String kind;
        public double health;public long quiet,nextRepair=1200,sitting,lastBeam,lastDive;public final BossBar bar;
        Body(State.Dragon data,EnderDragon entity){id=data.id;this.entity=entity;kind=data.kind;health=data.health;quiet=data.kingQuietTicks;nextRepair=data.kingNextRepair;bar=Bukkit.createBossBar("Эндер-дракон",BarColor.PINK,BarStyle.SOLID);}
        public double max(){return kind.equals("void_king")?1500:kind.equals("enriched")?500:200;}
    }
    public static final class Beam {
        public final UUID id=UUID.randomUUID(),owner;public final World world;public final Location origin,anchor;
        public final long started;public final Set<UUID> counted=new HashSet<>(),pending=new HashSet<>();public final Map<UUID,Integer> ordinals=new HashMap<>();
        Beam(Body body,Location origin,long tick){owner=body.id;world=body.entity.getWorld();this.origin=origin.clone();anchor=body.entity.getLocation();started=tick;}
    }
    private final ZLorPlugin p;
    private final Map<UUID,Body> bodies=new HashMap<>();
    private final Map<String,Beam> beams=new HashMap<>();
    private final Map<UUID,Long> summoning=new HashMap<>(),glowUntil=new HashMap<>();
    private final Map<String,Long> lastCrystalHeal=new HashMap<>();
    private final Set<UUID> processing=new HashSet<>(),failed=new HashSet<>();
    private final Set<String> upgrading=new HashSet<>(),stopped=new HashSet<>();
    private final NamespacedKey kindKey=new NamespacedKey("z_lor","dragon_kind"),ownedKey=new NamespacedKey("z_lor","dragon_id"),lossKey=new NamespacedKey("z_lor","beam_erosion"),cloudKey=new NamespacedKey("z_lor","enriched_cloud");
    private boolean ownSpawn,stopping;
    public Dragons(ZLorPlugin p){this.p=p;}
    public static String name(String kind){return switch(kind){case "enriched"->"Обогащённый дракон Края";case "void_king"->"Царь Войда";default->"Дракон Края";};}
    public Collection<Body> bodies(){return Collections.unmodifiableCollection(bodies.values());}
    public Collection<Beam> beams(){return Collections.unmodifiableCollection(beams.values());}
    private boolean enrichedAbilities(Body body){return body.kind.equals("enriched")||(body.kind.equals("void_king")&&p.getConfig().getBoolean("dragons.king-enriched-abilities",false));}
    public boolean active(World world){String key=p.worldKey(world);return p.state().dragons.values().stream().anyMatch(d->key.equals(d.world)&&(d.status.equals("ACTIVE")||d.status.equals("SUMMONING")));}
    public void restore(){
        for(var entry:p.state().arenas.entrySet())if(entry.getValue().phase.equals("STOPPED")&&p.state().dragons.values().stream().noneMatch(d->entry.getKey().equals(d.world)&&(d.status.equals("ACTIVE")||d.status.equals("SUMMONING"))))stopped.add(entry.getKey());
        for(var data:p.state().dragons.values()){
            if(data.status.equals("SUMMONING"))summoning.put(data.id,p.tick+data.summonRemaining);
            Entity existing=Bukkit.getEntity(data.id);if(existing instanceof EnderDragon dragon)reconcile(dragon);
        }
        for(World world:Bukkit.getWorlds())if(world.getEnvironment()==World.Environment.THE_END){
            var battle=world.getEnderDragonBattle();if(battle!=null&&battle.getEnderDragon()!=null)adopt(battle.getEnderDragon());
        }
    }
    public void command(Player player,String[] args){
        if(player.getWorld().getEnvironment()!=World.Environment.THE_END)throw new IllegalArgumentException("Драконий бой управляется в Энде");
        if(args.length==0){status(player);return;}
        if(args.length==1&&args[0].equals("view")){p.visuals.toggleDragons(player);return;}
        if(args[0].equals("spawn")&&args.length==2){if(args[1].equals("stop"))stop(player);else spawn(player,args[1]);return;}
        if(args[0].equals("star")&&args.length>=2){
            if(args.length==2&&(args[1].equals("enable")||args[1].equals("disable"))){boolean enabled=args[1].equals("enable");p.change(player,"Правило ★: "+enabled,s->{var arena=s.arenas.computeIfAbsent(p.worldKey(player.getWorld()),k->new State.Arena());arena.star=enabled;if(!enabled)arena.leader=null;},s->player.sendMessage("Правило ★ "+(enabled?"включено":"выключено")));return;}
            if(args.length==3&&args[1].equals("set")){UUID id=UUID.fromString(args[2]);var body=bodies.get(id);if(body==null||!body.entity.getWorld().equals(player.getWorld()))throw new IllegalArgumentException("Этот дракон не загружен в текущем измерении");
                p.change(player,"Выбран главный ★ "+id,s->{var arena=s.arenas.computeIfAbsent(p.worldKey(player.getWorld()),k->new State.Arena());arena.star=true;arena.leader=id;},s->player.sendMessage("Главный дракон выбран"));return;}
        }
        if(args[0].equals("wave")){
            if(args.length==3&&args[1].equals("start")){startWave(player,Integer.parseInt(args[2]));return;}
            if(args.length==2&&args[1].equals("status")){status(player);return;}
            if(args.length==2&&args[1].equals("stop")){stop(player);return;}
        }
        if(args[0].equals("attack")){
            if(args.length==2&&args[1].equals("stop")){stopAttacks(player.getWorld());player.sendMessage("Текущий луч и пикирование остановлены; бой продолжается");return;}
            if(args.length==3){UUID id=UUID.fromString(args[2]);Body body=bodies.get(id);if(body==null||!body.entity.getWorld().equals(player.getWorld()))throw new IllegalArgumentException("Дракон не найден");
                if(args[1].equals("beam")){if(!enrichedAbilities(body))throw new IllegalArgumentException("Луч доступен обогащённому; наследование Царём отдельно включается в config.yml");if(!startBeam(body))throw new IllegalArgumentException("Арена уже занята лучом");player.sendMessage("Запущен реальный Луч");return;}
                if(args[1].equals("dive")&&body.kind.equals("void_king")){dive(body,player);player.sendMessage("Запущено пикирование Царя");return;}
            }
        }
        throw new IllegalArgumentException("/lore dragons [spawn default|enriched|void_king|stop; view; star; wave; attack]");
    }
    public void spawn(Player player,String kind){
        if(!Set.of("default","enriched","void_king").contains(kind))throw new IllegalArgumentException("Тип: default, enriched, void_king");
        if(kind.equals("void_king")&&!p.getConfig().getBoolean("dragons.king-enabled",true))throw new IllegalArgumentException("Царь отключён в config.yml");
        World world=player.getWorld();String key=p.worldKey(world);stopped.remove(key);
        var data=planned(world,kind,0);
        p.change(player,"Призыв: "+name(kind),s->{
            long count=s.dragons.values().stream().filter(d->key.equals(d.world)&&(d.status.equals("ACTIVE")||d.status.equals("SUMMONING"))).count();
            if(count>=p.getConfig().getInt("safety.max-dragons-per-world",64))throw new IllegalArgumentException("Достигнут лимит драконов арены");
            s.dragons.put(data.id,data);s.crystalRepairs.add(key);
        },s->{summoning.put(data.id,p.tick+data.summonRemaining);p.structures.hidePortal(world,true);player.sendMessage("Призыв начат: "+name(kind));});
    }
    private State.Dragon planned(World world,String kind,int index){
        var data=new State.Dragon();data.world=p.worldKey(world);data.kind=kind;data.health=data.maxHealth();
        var origin=p.structures.origin(world);double angle=index*Math.PI/2;data.x=origin.getX()+Math.cos(angle)*(index==0?0:12);data.z=origin.getZ()+Math.sin(angle)*(index==0?0:12);
        int island=world.getHighestBlockYAt((int)data.x,(int)data.z);data.y=DragonRules.spawnY(island,p.voids.height(world),world.getMinHeight(),world.getMaxHeight());
        data.summonRemaining=kind.equals("default")?0:p.getConfig().getInt("dragons.summon-ticks",160);return data;
    }
    public void startWave(Player player,int number){
        if(number<1||number>10)throw new IllegalArgumentException("Номер волны: 1–10");
        World world=player.getWorld();String key=p.worldKey(world);if(active(world))throw new IllegalArgumentException("Сначала завершите или остановите текущий бой");
        var participants=new ArrayList<State.Dragon>();var composition=WAVES[number-1];UUID run=UUID.randomUUID();int index=0;
        for(int i=0;i<composition[0]+composition[1];i++){var d=planned(world,i<composition[0]?"default":"enriched",index++);d.run=run;participants.add(d);}
        p.change(player,"Запущена волна "+number,s->{
            if(s.dragons.values().stream().anyMatch(d->key.equals(d.world)&&(d.status.equals("ACTIVE")||d.status.equals("SUMMONING"))))throw new IllegalArgumentException("На арене уже начался бой");
            var arena=s.arenas.computeIfAbsent(key,k->new State.Arena());if(arena.waveActive())throw new IllegalArgumentException("Волна уже запущена");
            arena.run=run;arena.wave=number;arena.phase="SPAWNING";arena.members.clear();arena.dead.clear();arena.previousStar=arena.star;arena.waveStarApplied=number>=5;
            if(arena.waveStarApplied)arena.star=true;arena.leader=null;
            for(var d:participants){arena.members.add(d.id);s.dragons.put(d.id,d);}s.crystalRepairs.add(key);
        },s->{stopped.remove(key);participants.forEach(d->summoning.put(d.id,p.tick+d.summonRemaining));p.structures.hidePortal(world,true);player.sendMessage("Волна "+number+": "+composition[0]+" обычных, "+composition[1]+" обогащённых");});
    }
    private void trySpawn(State.Dragon data){
        if(processing.contains(data.id)||failed.contains(data.id)||stopping)return;
        World world=p.world(data.world);if(world==null)return;
        if(!world.isChunkLoaded(((int)data.x)>>4,((int)data.z)>>4)){
            processing.add(data.id);world.getChunkAtAsync(((int)data.x)>>4,((int)data.z)>>4,true).whenComplete((chunk,error)->p.getServer().getScheduler().runTask(p,()->processing.remove(data.id)));return;
        }
        Entity existing=Bukkit.getEntity(data.id);
        if(existing instanceof EnderDragon dragon){attach(data,dragon);markActive(data.id);return;}
        EnderDragon created=null;
        try{
            ownSpawn=true;
            var dragon=world.spawn(new Location(world,data.x,data.y,data.z),EnderDragon.class,e->{
                try{NativeSupport.identity(e,data.id);NativeSupport.detach(e);}catch(ReflectiveOperationException error){if(e.getClass().getName().contains("craftbukkit"))throw new IllegalStateException("Нативная идентичность дракона не подготовлена",error);}
                e.getPersistentDataContainer().set(kindKey,PersistentDataType.STRING,data.kind);e.getPersistentDataContainer().set(ownedKey,PersistentDataType.STRING,data.id.toString());
                e.customName(null);e.setPersistent(true);e.setRemoveWhenFarAway(false);e.setPhase(EnderDragon.Phase.CIRCLING);
            });
            created=dragon;
            if(!dragon.getUniqueId().equals(data.id))throw new IllegalStateException("UUID дракона не совпал с журналом призыва");
            attach(data,dragon);markActive(data.id);
        }catch(Exception error){if(created!=null){bodies.remove(data.id);created.remove();}failed.add(data.id);p.getLogger().log(java.util.logging.Level.SEVERE,"Призыв приостановлен, запись сохранена: "+data.id,error);}
        finally{ownSpawn=false;}
    }
    private void markActive(UUID id){
        if(!processing.add(id))return;
        p.store.change(s->{var d=s.dragons.get(id);if(d!=null){d.status="ACTIVE";d.summonRemaining=0;}},s->{processing.remove(id);summoning.remove(id);},e->processing.remove(id));
    }
    private void attach(State.Dragon data,EnderDragon dragon){
        if(bodies.containsKey(data.id))return;
        try{
            var max=dragon.getAttribute(Attribute.MAX_HEALTH);if(max==null){dragon.registerAttribute(Attribute.MAX_HEALTH);max=dragon.getAttribute(Attribute.MAX_HEALTH);}
            max.setBaseValue(data.kind.equals("void_king")?1024:data.maxHealth());
            var scale=dragon.getAttribute(Attribute.SCALE);if(scale==null){dragon.registerAttribute(Attribute.SCALE);scale=dragon.getAttribute(Attribute.SCALE);}if(scale!=null)scale.setBaseValue(data.kind.equals("void_king")?2.3:data.kind.equals("enriched")?1.15:1);
            dragon.setHealth(Math.min(max.getValue(),data.kind.equals("void_king")?data.health/1500*max.getValue():Math.max(.01,data.health)));
            dragon.customName(null);dragon.setCustomNameVisible(false);dragon.setPodium(p.structures.origin(dragon.getWorld()));
            if(dragon.getPhase()==EnderDragon.Phase.HOVER)dragon.setPhase(EnderDragon.Phase.CIRCLING);
            dragon.getPersistentDataContainer().set(kindKey,PersistentDataType.STRING,data.kind);dragon.getPersistentDataContainer().set(ownedKey,PersistentDataType.STRING,data.id.toString());
            if(dragon.getBossBar()!=null)dragon.getBossBar().setVisible(false);
            var body=new Body(data,dragon);body.lastBeam=p.tick;body.lastDive=p.tick;bodies.put(data.id,body);
        }catch(Exception error){failed.add(data.id);p.getLogger().log(java.util.logging.Level.SEVERE,"Дракон не подготовлен: "+data.id,error);}
    }
    private void adopt(EnderDragon dragon){
        if(p.state().dragons.containsKey(dragon.getUniqueId())){reconcile(dragon);return;}
        var data=new State.Dragon();data.id=dragon.getUniqueId();data.kind="default";data.health=dragon.getHealth();data.status="ACTIVE";data.world=p.worldKey(dragon.getWorld());data.x=dragon.getX();data.y=dragon.getY();data.z=dragon.getZ();
        p.store.change(s->{s.dragons.put(data.id,data);s.crystalRepairs.add(data.world);},s->{attach(data,dragon);p.structures.hidePortal(dragon.getWorld(),true);},e->p.getLogger().warning("Нативный дракон не добавлен в журнал: "+e));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent event){
        if(!(event.getEntity() instanceof EnderDragon dragon)||ownSpawn||dragon.getWorld().getEnvironment()!=World.Environment.THE_END)return;
        if(upgrading.contains(p.worldKey(dragon.getWorld()))||stopped.contains(p.worldKey(dragon.getWorld()))){event.setCancelled(true);return;}
        p.getServer().getScheduler().runTask(p,()->{if(dragon.isValid())adopt(dragon);});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        EnderDragon dragon=root(event.getEntity());if(dragon==null)return;Body body=bodies.get(dragon.getUniqueId());if(body==null)return;
        if(event instanceof EntityDamageByEntityEvent by){
            EnderDragon attacker=root(by.getDamager());if(attacker!=null&&bodies.containsKey(attacker.getUniqueId())){event.setCancelled(true);return;}
            if(enrichedAbilities(body)&&by.getDamager() instanceof AbstractArrow){event.setCancelled(true);return;}
        }
        var arena=p.state().arenas.get(p.worldKey(dragon.getWorld()));UUID leader=leader(dragon.getWorld());
        if(arena!=null&&arena.star&&leader!=null&&!leader.equals(body.id)){
            event.setDamage(event.getDamage()/3);
            if(event instanceof EntityDamageByEntityEvent by){Player player=by.getDamager() instanceof Player actor?actor:by.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player actor?actor:null;
                if(player!=null){player.sendActionBar(Component.text("Защита ★: главный дракон снижает урон союзникам"));Body main=bodies.get(leader);if(main!=null&&!main.entity.isGlowing()){main.entity.setGlowing(true);glowUntil.put(main.id,p.tick+30);}}
            }
        }
        if(event.getFinalDamage()>0){body.quiet=0;body.nextRepair=1200;}
        if(body.kind.equals("void_king")){
            double damage=event.getFinalDamage();event.setCancelled(true);if(damage<=0)return;
            body.health=Math.max(0,body.health-damage);syncKing(body);dragon.playEffect(EntityEffect.HURT);
        }
    }
    public static EnderDragon root(Entity entity){return entity instanceof EnderDragon d?d:entity instanceof EnderDragonPart part?part.getParent():null;}
    private void syncKing(Body body){var attribute=body.entity.getAttribute(Attribute.MAX_HEALTH);double nativeMax=attribute==null?1024:attribute.getValue();body.entity.setHealth(body.health<=0?0:Math.max(.001,Math.min(nativeMax,body.health/1500*nativeMax)));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void heal(EntityRegainHealthEvent event){
        EnderDragon dragon=root(event.getEntity());if(dragon==null)return;Body body=bodies.get(dragon.getUniqueId());if(body==null)return;
        boolean crystal=event.getRegainReason().name().equals("ENDER_CRYSTAL");
        if(crystal){
            String key=p.worldKey(dragon.getWorld());
            if(p.tick-lastCrystalHeal.getOrDefault(key,-20L)<10){event.setCancelled(true);return;}
            Body king=bodies.values().stream().filter(b->b.entity.getWorld().equals(dragon.getWorld())&&b.kind.equals("void_king")&&b.health<b.max()).findFirst().orElse(null);
            lastCrystalHeal.put(key,p.tick);
            if(king!=null){event.setCancelled(true);king.health=Math.min(1500,king.health+event.getAmount());syncKing(king);return;}
        }
        if(body.kind.equals("void_king")){event.setCancelled(true);body.health=Math.min(1500,body.health+event.getAmount());syncKing(body);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void contact(EntityDamageByEntityEvent event){
        EnderDragon dragon=root(event.getDamager());if(dragon==null)return;Body body=bodies.get(dragon.getUniqueId());if(body==null||!enrichedAbilities(body))return;
        if(root(event.getEntity())!=null){event.setCancelled(true);return;}
        if(event.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK){
            Entity target=event.getEntity();p.getServer().getScheduler().runTask(p,()->{if(target.isValid())target.setVelocity(target.getVelocity().multiply(2));});
        }
        if(event.getEntity() instanceof Enderman enderman)p.getServer().getScheduler().runTask(p,()->{if(enderman.isValid()&&dragon.isValid())enderman.setTarget(dragon);});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void cloud(AreaEffectCloudApplyEvent event){
        var cloud=event.getEntity();if(!cloud.getPersistentDataContainer().has(cloudKey,PersistentDataType.BYTE))return;
        for(LivingEntity entity:event.getAffectedEntities())entity.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,200,0));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void cloudSpawn(EntitySpawnEvent event){
        if(!(event.getEntity() instanceof AreaEffectCloud cloud))return;
        if(!(cloud.getSource() instanceof EnderDragon dragon))return;Body body=bodies.get(dragon.getUniqueId());if(body==null||!enrichedAbilities(body))return;
        cloud.getPersistentDataContainer().set(cloudKey,PersistentDataType.BYTE,(byte)1);cloud.setRadius(cloud.getRadius()*2);cloud.setRadiusPerTick(cloud.getRadiusPerTick()*2);
        cloud.setParticle(Particle.DUST,new Particle.DustOptions(Color.fromRGB(231,186,205),1));
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.LEVITATION,200,0),true);
        try{NativeSupport.duration(cloud);}catch(ReflectiveOperationException error){p.getLogger().warning("Не установлен potionDurationScale=1: "+error.getMessage());}
    }
    public boolean collectBreath(Player player,EquipmentSlot hand){
        var eye=player.getEyeLocation();var attribute=player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);double reach=attribute==null?3:attribute.getValue();
        AreaEffectCloud target=player.getWorld().getNearbyEntities(eye,reach+4,reach+4,reach+4,e->e instanceof AreaEffectCloud).stream().map(e->(AreaEffectCloud)e)
                .filter(e->e.getRadius()>.5&&e.getPersistentDataContainer().has(cloudKey,PersistentDataType.BYTE))
                .filter(e->Math.max(0,eye.distance(e.getLocation())-e.getRadius())<=reach).min(Comparator.comparingDouble(e->eye.distanceSquared(e.getLocation()))).orElse(null);
        if(target==null)return false;
        Vector to=target.getLocation().toVector().subtract(eye.toVector());double distance=Math.max(0,to.length()-target.getRadius());
        if(distance>0&&player.getWorld().rayTraceBlocks(eye,to.normalize(),distance,FluidCollisionMode.NEVER,true)!=null)return false;
        if(!p.voids.giveBottle(player,hand,p.items.make("void_breath")))return false;target.setRadius(target.getRadius()-.5f);return true;
    }
    public boolean startBeam(Body body){
        if(!enrichedAbilities(body))return false;
        String key=p.worldKey(body.entity.getWorld());if(beams.containsKey(key)||body.entity.isDead())return false;
        var beam=new Beam(body,p.structures.origin(body.entity.getWorld()),p.tick);beams.put(key,beam);body.lastBeam=p.tick;
        body.entity.setPhase(EnderDragon.Phase.HOVER);return true;
    }
    public void stopBeam(World world){var beam=beams.remove(p.worldKey(world));if(beam==null)return;p.visuals.stopBeam(beam.id);Body body=bodies.get(beam.owner);if(body!=null&&body.entity.isValid()&&!body.entity.isDead()){body.lastBeam=p.tick;body.entity.setPhase(EnderDragon.Phase.CIRCLING);}}
    public void stopAttacks(World world){
        stopBeam(world);
        for(Body body:bodies.values())if(body.entity.getWorld().equals(world)&&body.entity.isValid()&&!body.entity.isDead()){
            if(body.entity.getPhase()==EnderDragon.Phase.CHARGE_PLAYER){body.entity.setPhase(EnderDragon.Phase.CIRCLING);body.entity.setVelocity(new Vector());}
            body.lastBeam=p.tick;body.lastDive=p.tick;
        }
    }
    public BeamTimeline timeline(){return new BeamTimeline(Math.clamp(p.getConfig().getInt("dragons.beam-warning-ticks",30),1,1200),Math.clamp(p.getConfig().getInt("dragons.beam-active-ticks",200),20,1200),Math.clamp(p.getConfig().getInt("dragons.beam-fade-ticks",60),1,200));}
    private void beamsTick(){
        var timeline=timeline();
        for(var beam:new ArrayList<>(beams.values())){
            Body body=bodies.get(beam.owner);long elapsed=p.tick-beam.started;
            if(body==null||!body.entity.isValid()||body.entity.isDead()||timeline.finished(elapsed)){stopBeam(beam.world);continue;}
            if(body.entity.getLocation().distanceSquared(beam.anchor)>.01)body.entity.teleport(beam.anchor);body.entity.setVelocity(new Vector());
            if(!timeline.pulse(elapsed))continue;
            for(Entity entity:beam.world.getNearbyEntities(beam.origin.clone().set(beam.origin.getX(),(beam.world.getMinHeight()+beam.world.getMaxHeight())/2d,beam.origin.getZ()),55,(beam.world.getMaxHeight()-beam.world.getMinHeight())/2d+32,55)){
                var box=entity.getBoundingBox();if(!BeamPattern.hits(box.getMinX(),box.getMinZ(),box.getMaxX(),box.getMaxZ(),beam.origin.getX(),beam.origin.getZ()))continue;
                if(entity instanceof Player player&&player.getGameMode()!=GameMode.CREATIVE&&player.getGameMode()!=GameMode.SPECTATOR&&!player.isDead())hit(beam,body,player);
                else if(entity instanceof Enderman enderman){enderman.damage(2,body.entity);enderman.setTarget(body.entity);}
            }
        }
    }
    private void hit(Beam beam,Body source,Player player){
        if(!timeline().firing(p.tick-beam.started)||player.isDead()||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR)return;
        UUID id=player.getUniqueId();boolean first=!beam.ordinals.containsKey(id);
        int count=first?DragonRules.hits(p.state().beamHits.getOrDefault(id,0)):beam.ordinals.get(id);
        double amount=DragonRules.damage((float)player.getHealth(),count,4);
        double health=player.getHealth(),absorption=player.getAbsorptionAmount();
        // Ownerless MAGIC: a living dragon cause would add difficulty scaling.
        // Deliver each pulse immediately; disk acknowledgement must not swallow it.
        if(amount>0)player.damage(amount,DamageSource.builder(DamageType.MAGIC).build());
        boolean accepted=amount<=0||player.getHealth()<health||player.getAbsorptionAmount()<absorption;
        if(!first||!accepted)return;
        beam.ordinals.put(id,count);beam.counted.add(id);beam.pending.add(id);
        p.store.change(s->s.beamHits.put(id,Math.max(count,s.beamHits.getOrDefault(id,0))),s->{beam.pending.remove(id);if(player.isOnline())applyMaxLoss(player);},error->{beam.pending.remove(id);p.getLogger().warning("Попадание луча не сохранено: "+error);});
    }
    public void applyMaxLoss(Player player){
        var attribute=player.getAttribute(Attribute.MAX_HEALTH);if(attribute==null)return;
        attribute.removeModifier(lossKey);double loss=Math.min(Math.max(0,DragonRules.lost(p.state().beamHits.getOrDefault(player.getUniqueId(),0))-p.state().beamRestored.getOrDefault(player.getUniqueId(),0)),Math.max(0,attribute.getValue()-1));
        if(loss>0)attribute.addTransientModifier(new AttributeModifier(lossKey,-loss,AttributeModifier.Operation.ADD_NUMBER));
        if(player.getHealth()>attribute.getValue())player.setHealth(attribute.getValue());
    }
    public UUID leader(World world){
        var arena=p.state().arenas.get(p.worldKey(world));if(arena==null||!arena.star)return null;
        if(arena.leader!=null&&bodies.containsKey(arena.leader)&&bodies.get(arena.leader).entity.isValid())return arena.leader;
        return bodies.values().stream().filter(b->b.entity.getWorld().equals(world)&&b.entity.isValid()&&!b.entity.isDead()&&!b.kind.equals("void_king"))
                .sorted(Comparator.comparingInt((Body b)->b.kind.equals("enriched")?0:1).thenComparing(Comparator.comparingDouble(this::health).reversed()).thenComparing(b->b.id)).map(b->b.id).findFirst().orElse(null);
    }
    public double health(Body body){return body.kind.equals("void_king")?body.health:body.entity.getHealth();}
    public String phase(Body body){
        if(beams.values().stream().anyMatch(b->b.owner.equals(body.id)))return "Луч";
        return switch(body.entity.getPhase()){
            case STRAFING->"Плевок";case BREATH_ATTACK->"Дыхание";case CHARGE_PLAYER->"Таран";case LEAVE_PORTAL->"Взлёт";case FLY_TO_PORTAL->"Заход на посадку";case LAND_ON_PORTAL->"Посадка";case DYING->"Гибель";case SEARCH_FOR_BREATH_ATTACK_TARGET->"Поиск цели";case ROAR_BEFORE_ATTACK->"Подготовка дыхания";default->"Полёт";
        };
    }
    private void dive(Body body,Player player){
        Vector direction=player.getLocation().add(0,2,0).toVector().subtract(body.entity.getLocation().toVector());
        if(direction.lengthSquared()<1)return;body.lastDive=p.tick;
        body.entity.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
        try{NativeSupport.charge(body.entity,player.getLocation().add(0,2,0));}
        catch(ReflectiveOperationException error){p.getLogger().warning("Не задана нативная цель пикирования: "+error.getMessage());body.entity.setPhase(EnderDragon.Phase.CIRCLING);return;}
        body.entity.setVelocity(direction.normalize().multiply(p.getConfig().getDouble("dragons.king-dive-speed",1.2)));body.lastDive=p.tick;
    }
    private void repair(Body body){
        int result=p.crystals.one(body.entity.getWorld());body.nextRepair=body.quiet+(result<0?20:600);
    }
    public void tick(){
        for(var data:p.state().dragons.values())if(data.status.equals("SUMMONING")&&!failed.contains(data.id)){
            long due=summoning.computeIfAbsent(data.id,id->p.tick+data.summonRemaining);if(p.tick>=due)trySpawn(data);
        }
        for(Body body:new ArrayList<>(bodies.values())){
            if(!body.entity.isValid()||body.entity.isDead()){body.bar.removeAll();bodies.remove(body.id);continue;}
            body.quiet++;
            if(body.kind.equals("void_king")){
                if(body.quiet>=body.nextRepair)repair(body);
                if(p.tick-body.lastDive>=p.getConfig().getInt("dragons.king-dive-cooldown-ticks",400)&&!beams.containsKey(p.worldKey(body.entity.getWorld()))){
                    Player target=body.entity.getWorld().getPlayers().stream().filter(player->player.getGameMode()==GameMode.SURVIVAL&&!player.isDead()).min(Comparator.comparingDouble(player->player.getLocation().distanceSquared(body.entity.getLocation()))).orElse(null);if(target!=null)dive(body,target);
                }
            }
            if(enrichedAbilities(body)){
                var phase=body.entity.getPhase();boolean sitting=phase==EnderDragon.Phase.BREATH_ATTACK||phase==EnderDragon.Phase.ROAR_BEFORE_ATTACK||phase==EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET;
                body.sitting=sitting?body.sitting+1:0;if(body.sitting>160){body.entity.setPhase(EnderDragon.Phase.LEAVE_PORTAL);body.sitting=0;}
                if(p.tick-body.lastBeam>=p.getConfig().getInt("dragons.beam-cooldown-ticks",300)&&body.entity.getWorld().getPlayers().stream().anyMatch(player->!player.isDead()&&player.getGameMode()!=GameMode.CREATIVE&&player.getGameMode()!=GameMode.SPECTATOR&&Math.hypot(player.getX()-p.structures.origin(player.getWorld()).getX(),player.getZ()-p.structures.origin(player.getWorld()).getZ())<=55))startBeam(body);
            }
            if(p.tick%10==0){
                double health=health(body);body.bar.setProgress(Math.clamp(health/body.max(),0,1));body.bar.setTitle("Эндер-дракон");
                var visible=new HashSet<UUID>();for(Player player:body.entity.getWorld().getPlayers())if(player.getLocation().distanceSquared(body.entity.getLocation())<=256*256){body.bar.addPlayer(player);visible.add(player.getUniqueId());}
                for(Player player:List.copyOf(body.bar.getPlayers()))if(!visible.contains(player.getUniqueId()))body.bar.removePlayer(player);
            }
        }
        for(var entry:new ArrayList<>(glowUntil.entrySet()))if(entry.getValue()<=p.tick){var body=bodies.get(entry.getKey());if(body!=null)body.entity.setGlowing(false);glowUntil.remove(entry.getKey());}
        beamsTick();
        if(p.tick%10==0){rituals();advanceWaves();}
    }
    private void rituals(){
        for(World world:Bukkit.getWorlds())if(world.getEnvironment()==World.Environment.THE_END&&!active(world)&&!upgrading.contains(p.worldKey(world))){
            var battle=world.getEnderDragonBattle();if(battle==null||battle.getRespawnPhase()==DragonBattle.RespawnPhase.NONE)continue;
            Location origin=p.structures.origin(world);var crystals=new LinkedHashSet<EnderCrystal>();boolean all=true;
            for(int[] offset:DragonRules.CRYSTALS){
                Location coordinate=origin.clone().add(offset[0],1,offset[1]);
                var crystal=world.getNearbyEntities(coordinate,.8,2,.8,e->e instanceof EnderCrystal).stream().map(e->(EnderCrystal)e).findFirst().orElse(null);
                if(crystal==null){all=false;break;}crystals.add(crystal);
            }
            if(!all||crystals.size()!=8)continue;
            String key=p.worldKey(world);upgrading.add(key);var data=planned(world,"enriched",0);crystals.forEach(e->data.ritualCrystals.add(e.getUniqueId()));
            p.store.change(s->{s.dragons.put(data.id,data);s.crystalRepairs.add(key);},s->{
                try{NativeSupport.stopNativeFight(world);}catch(ReflectiveOperationException error){failed.add(data.id);p.getLogger().severe("Нативный ритуал не остановлен: "+error);return;}
                crystals.forEach(Entity::remove);summoning.put(data.id,p.tick+data.summonRemaining);upgrading.remove(key);p.structures.hidePortal(world,true);
            },e->upgrading.remove(key));
        }
    }
    private void advanceWaves(){
        for(var entry:p.state().arenas.entrySet()){
            var arena=entry.getValue();if(!arena.phase.equals("SPAWNING"))continue;
            boolean ready=arena.members.stream().allMatch(id->{var data=p.state().dragons.get(id);return data!=null&&(data.status.equals("ACTIVE")||data.status.equals("DEAD"));});
            if(ready){String world=entry.getKey();p.store.change(s->{var current=s.arenas.get(world);if(current!=null&&current.phase.equals("SPAWNING"))current.phase="ACTIVE";});}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(EntityDeathEvent event){
        EnderDragon dragon=root(event.getEntity());if(dragon==null)return;UUID id=dragon.getUniqueId();var data=p.state().dragons.get(id);if(data==null)return;
        var body=bodies.remove(id);if(body!=null)body.bar.removeAll();stopBeam(dragon.getWorld());
        p.store.change(s->{var current=s.dragons.get(id);if(current==null)return;current.status="DEAD";current.health=0;
            var arena=s.arenas.get(current.world);if(arena!=null&&arena.waveActive()&&arena.members.contains(id)){
                arena.dead.add(id);if(arena.dead.containsAll(arena.members)){arena.phase="WON";if(arena.waveStarApplied){arena.star=arena.previousStar;arena.waveStarApplied=false;}arena.leader=null;}
            }
        },s->{if(!active(dragon.getWorld()))p.structures.hidePortal(dragon.getWorld(),false);},e->p.getLogger().warning("Смерть дракона не записана: "+e));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void egg(io.papermc.paper.event.block.DragonEggFormEvent event){event.setCancelled(true);}
    private void reconcile(EnderDragon dragon){
        var data=p.state().dragons.get(dragon.getUniqueId());if(data==null)return;
        if(data.status.equals("STOPPED")||data.status.equals("DEAD")){dragon.remove();return;}
        attach(data,dragon);
    }
    @EventHandler public void loaded(EntitiesLoadEvent event){for(Entity entity:event.getEntities())if(entity instanceof EnderDragon dragon)reconcile(dragon);}
    @EventHandler public void unloaded(EntitiesUnloadEvent event){for(Entity entity:event.getEntities())if(entity instanceof EnderDragon dragon){
        Body body=bodies.remove(dragon.getUniqueId());if(body!=null){
            double hp=health(body),x=dragon.getX(),y=dragon.getY(),z=dragon.getZ();long quiet=body.quiet,next=body.nextRepair;
            p.store.change(s->{var data=s.dragons.get(body.id);if(data!=null&&data.status.equals("ACTIVE")){data.health=hp;data.x=x;data.y=y;data.z=z;data.kingQuietTicks=quiet;data.kingNextRepair=next;}});
            body.bar.removeAll();
        }stopBeam(event.getWorld());
    }}
    public void stop(Player player){
        World world=player.getWorld();String key=p.worldKey(world);var ids=p.state().dragons.values().stream().filter(d->d.world.equals(key)&&!d.status.equals("DEAD")).map(d->d.id).toList();
        p.change(player,"Остановлен драконий бой",s->{for(UUID id:ids){var data=s.dragons.get(id);if(data!=null)data.status="STOPPED";}var arena=s.arenas.get(key);s.crystalRepairs.remove(key);if(arena!=null){arena.phase="STOPPED";arena.leader=null;if(arena.waveStarApplied){arena.star=arena.previousStar;arena.waveStarApplied=false;}}},s->{
            stopped.add(key);stopBeam(world);for(UUID id:ids){summoning.remove(id);processing.remove(id);var body=bodies.remove(id);if(body!=null){body.bar.removeAll();body.entity.remove();}else{var entity=Bukkit.getEntity(id);if(entity!=null)entity.remove();}}
            try{NativeSupport.stopNativeFight(world);}catch(ReflectiveOperationException error){p.getLogger().warning("Не сброшен нативный ритуал: "+error);}
            upgrading.remove(key);p.structures.hidePortal(world,false);player.sendMessage("Бой остановлен без победы, дропа и яйца");
        });
    }
    public void capture(State state){
        for(Body body:bodies.values()){
            var data=state.dragons.get(body.id);if(data==null||!data.status.equals("ACTIVE"))continue;
            data.health=health(body);data.x=body.entity.getX();data.y=body.entity.getY();data.z=body.entity.getZ();data.kingQuietTicks=body.quiet;data.kingNextRepair=body.nextRepair;
        }
        summoning.forEach((id,due)->{var data=state.dragons.get(id);if(data!=null&&data.status.equals("SUMMONING"))data.summonRemaining=Math.max(0,due-p.tick);});
    }
    public void moved(World world,Location origin){for(Body body:bodies.values())if(body.entity.getWorld().equals(world))body.entity.setPodium(origin);}
    public void status(Player player){
        var arena=p.state().arenas.get(p.worldKey(player.getWorld()));player.sendMessage("Драконы · ★ "+(arena!=null&&arena.star?"включена":"выключена")+(arena==null?"":" · волна "+arena.wave+": "+arena.phase));
        for(Body body:bodies.values())if(body.entity.getWorld().equals(player.getWorld()))player.sendMessage(name(body.kind)+" · "+VoidForecast.input(health(body))+"/"+VoidForecast.input(body.max())+" HP · "+phase(body)+" · "+body.id);
    }
    @Override public void close(){stopping=true;for(var beam:new ArrayList<>(beams.values()))stopBeam(beam.world);bodies.values().forEach(body->body.bar.removeAll());bodies.clear();}
}
