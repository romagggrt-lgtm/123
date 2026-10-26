package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bounded vanilla presentation; the Fabric surface shader is not emulated. */
public class Visuals implements Listener,AutoCloseable {
    public record Dome(UUID id,Location center,double radius,Color color,long age,long remaining,boolean reserves){}
    private final ZLorPlugin p;
    private final Set<UUID> voidViews=new HashSet<>(),dragonViews=new HashSet<>();
    private final Map<UUID,TextDisplay> hud=new HashMap<>();
    private final Map<UUID,List<ItemDisplay>> columns=new HashMap<>();
    private final Map<UUID,ItemDisplay> boundaries=new HashMap<>();
    private final DomeDisplays meshes;
    private final Map<UUID,Long> opened=new HashMap<>();
    public Visuals(ZLorPlugin p){this.p=p;meshes=new DomeDisplays(p);}
    public int domeCount(){return meshes.size();}
    public boolean viewingVoid(UUID id){return voidViews.contains(id);}
    public boolean viewingDragons(UUID id){return dragonViews.contains(id);}
    public void toggleVoid(Player player){p.roles.require(player);if(!voidViews.add(player.getUniqueId())){voidViews.remove(player.getUniqueId());removeBoundary(player.getUniqueId());}player.sendMessage("Личная граница: "+(voidViews.contains(player.getUniqueId())?"включена":"выключена"));}
    public void toggleDragons(Player player){p.roles.require(player);if(!dragonViews.add(player.getUniqueId())){dragonViews.remove(player.getUniqueId());removeHud(player.getUniqueId());}player.sendMessage("Личный HUD драконов: "+(dragonViews.contains(player.getUniqueId())?"включён":"выключен"));}
    public void showAll(Player player){p.roles.require(player);boolean both=voidViews.contains(player.getUniqueId())&&dragonViews.contains(player.getUniqueId());if(both)leave(player.getUniqueId());else{voidViews.add(player.getUniqueId());dragonViews.add(player.getUniqueId());}player.sendMessage("Личные визуализации "+(both?"выключены":"включены"));}
    public void command(Player player,String[] args){
        if(args.length==0||args.length==1&&args[0].equals("all")){showAll(player);return;}
        if(args.length!=1)throw new IllegalArgumentException("/lore view [all|void|dragons|off]");
        switch(args[0]){case "void"->toggleVoid(player);case "dragons"->toggleDragons(player);case "off"->{leave(player.getUniqueId());player.sendMessage("Личные визуализации выключены");}default->throw new IllegalArgumentException("/lore view [all|void|dragons|off]");}
    }
    public void tick(){
        if(p.tick%2==0)for(Player player:Bukkit.getOnlinePlayers())if(p.roles.scen(player)){
            if(dragonViews.contains(player.getUniqueId()))updateHud(player);
            if((voidViews.contains(player.getUniqueId())||dragonViews.contains(player.getUniqueId()))&&!p.voids.feedbackActive(player.getUniqueId())){
                String text=voidViews.contains(player.getUniqueId())?"Войд Y="+VoidForecast.number(p.voids.height(player.getWorld()))+" · "+VoidForecast.input(p.voids.state(player.getWorld()).perFourHours()/240)+" бл/мин":"Драконы: "+p.dragons.bodies().stream().filter(body->body.entity.getWorld().equals(player.getWorld())).count();
                player.sendActionBar(Component.text(text,NamedTextColor.LIGHT_PURPLE));
            }
        }
        int interval=Math.max(2,p.getConfig().getInt("effects.interval-ticks",4));
        if(p.tick%interval!=0)return;
        Map<World,List<Dome>> domes=new HashMap<>();
        for(var d:p.devices.loaded())if(d.active(p.fuelNow())){
            long start=opened.computeIfAbsent(d.id,id->p.now());var center=p.devices.center(d);domes.computeIfAbsent(center.getWorld(),k->new ArrayList<>()).add(new Dome(d.id,center,50,Color.fromRGB(159,132,240),p.now()-start,d.remaining(p.fuelNow()),d.reserves()>0));
        }
        for(var zone:p.loot.zones()){long start=opened.computeIfAbsent(zone.id(),id->p.now());domes.computeIfAbsent(zone.center().getWorld(),k->new ArrayList<>()).add(new Dome(zone.id(),zone.center(),3,Color.YELLOW,p.now()-start,zone.expires()-p.now(),false));}
        for(World world:Bukkit.getWorlds())if(world.getEnvironment()==World.Environment.THE_END){var s=p.structures.current(world);if(p.structures.entryReady(world,s)&&s.move==null){UUID id=UUID.nameUUIDFromBytes((p.worldKey(world)+"entry").getBytes(java.nio.charset.StandardCharsets.UTF_8));long start=opened.computeIfAbsent(id,key->p.now());domes.computeIfAbsent(world,k->new ArrayList<>()).add(new Dome(id,new Location(world,s.entryX+.5,s.entryY+1,s.entryZ+.5),Math.sqrt(12.5)+1,Color.LIME,p.now()-start,Long.MAX_VALUE,true));}}
        var allDomes=domes.values().stream().flatMap(Collection::stream).toList();meshes.sync(allDomes);
        var ids=new HashSet<UUID>();allDomes.forEach(d->ids.add(d.id));opened.keySet().retainAll(ids);
        int budget=Math.clamp(p.getConfig().getInt("effects.particle-budget-per-player",320),16,1024);
        double range=Math.clamp(p.getConfig().getDouble("effects.range",96),16,192);
        for(Player player:Bukkit.getOnlinePlayers()){
            if(voidViews.contains(player.getUniqueId())&&p.roles.scen(player))drawBoundary(player,budget/10);
            drawBeams(player,budget*4/10);
            drawSummon(player,budget/10);
        }
        if(p.getConfig().getBoolean("effects.beams",true))updateColumns();
    }
    private void drawBoundary(Player player,int budget){
        double y=p.voids.height(player.getWorld());double x=Math.floor(player.getX()/16)*16+8,z=Math.floor(player.getZ()/16)*16+8;
        ItemDisplay display=boundaries.get(player.getUniqueId());Location at=new Location(player.getWorld(),x,y+.015,z);
        if(display==null||!display.isValid()){
            var item=new ItemStack(Material.PAPER);item.editMeta(meta->meta.setItemModel(new NamespacedKey("z_lor","void_grid")));
            display=player.getWorld().spawn(at,ItemDisplay.class,e->{e.setItemStack(item);e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setVisibleByDefault(false);e.setBrightness(new Display.Brightness(15,15));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setViewRange(4);e.setDisplayWidth(96);e.setDisplayHeight(1);e.setTeleportDuration(4);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateX((float)-Math.PI/2),new Vector3f(96,96,1),new Quaternionf()));});
            boundaries.put(player.getUniqueId(),display);player.showEntity(p,display);
        }else if(display.getLocation().distanceSquared(at)>.00001)display.teleport(at);
    }
    private void drawSummon(Player player,int count){
        for(var d:p.state().dragons.values())if(d.world.equals(p.worldKey(player.getWorld()))&&d.status.equals("SUMMONING")&&!d.kind.equals("default")){
            Location target=new Location(player.getWorld(),d.x,d.y,d.z);if(target.distanceSquared(player.getLocation())>128*128)continue;
            for(int i=0;i<count;i++){double a=(i*2.399963+p.tick*.06),r=(i%4+1)*2;double y=d.y-24+Math.floorMod(p.tick+i*7,160)/160d*26;player.spawnParticle(Particle.DUST,new Location(player.getWorld(),d.x+Math.cos(a)*r,y,d.z+Math.sin(a)*r),1,0,0,0,0,new Particle.DustOptions(i%2==0?Color.fromRGB(231,186,205):Color.fromRGB(33,18,47),1));}
            break;
        }
    }
    private void drawBeams(Player player,int budget){
        for(var beam:p.dragons.beams())if(beam.world.equals(player.getWorld())&&beam.origin.distanceSquared(player.getLocation())<192*192){
            long elapsed=p.tick-beam.started;var timeline=p.dragons.timeline();boolean warning=elapsed<timeline.warning();int per=Math.max(1,budget/BeamPattern.COLUMNS.size()),sent=0;
            if(!warning&&!timeline.firing(elapsed))continue;
            int offset=(int)(p.tick/5*17%BeamPattern.COLUMNS.size());
            for(int index=0;index<BeamPattern.COLUMNS.size();index++){
                var column=BeamPattern.COLUMNS.get((index+offset)%BeamPattern.COLUMNS.size());
                if(sent>=budget)break;double x=beam.origin.getX()+column.x(),z=beam.origin.getZ()+column.z();
                if(warning){
                    for(int n=0;n<per&&sent<budget;n++){double angle=(p.tick+n*12+column.x())*.3;double dx=x+Math.cos(angle)*column.radius(),dz=z+Math.sin(angle)*column.radius();if(!beam.world.isChunkLoaded(((int)Math.floor(dx))>>4,((int)Math.floor(dz))>>4))continue;int y=beam.world.getHighestBlockYAt((int)Math.floor(dx),(int)Math.floor(dz))+1;
                        player.spawnParticle(Particle.DUST,new Location(beam.world,dx,y+.05,dz),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(column.edge()),.7f));sent++;}
                }else{
                    double a=p.tick*.4+column.x(),radius=column.radius()*.65;
                    double y=player.getY()+48-Math.floorMod(p.tick*3+(int)(column.z()*5),96);
                    player.spawnParticle(Particle.DUST,new Location(beam.world,x+Math.cos(a)*radius,y,z+Math.sin(a)*radius),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(column.edge()),.8f));sent++;
                }
            }
        }
    }
    private void updateColumns(){
        var active=new HashSet<UUID>();var timeline=p.dragons.timeline();
        for(var beam:p.dragons.beams()){
            long age=p.tick-beam.started;if(timeline.finished(age))continue;active.add(beam.id);
            boolean warning=age<timeline.warning();double opacity=timeline.opacity(age);
            var group=columns.computeIfAbsent(beam.id,key->new ArrayList<>());
            if(group.isEmpty())for(var column:BeamPattern.COLUMNS){
                double x=beam.origin.getX()+column.x(),z=beam.origin.getZ()+column.z();if(!beam.world.isChunkLoaded(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4))continue;
                int ground=beam.world.getHighestBlockYAt((int)Math.floor(x),(int)Math.floor(z))+1;
                var display=beam.world.spawn(new Location(beam.world,x,ground+.035,z),ItemDisplay.class,e->{
                    e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setVisibleByDefault(false);e.setBrightness(new Display.Brightness(15,15));e.setViewRange(4);e.setDisplayWidth((float)(column.radius()*2.2));e.setDisplayHeight(beam.world.getMaxHeight()-beam.world.getMinHeight());e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setInterpolationDelay(0);e.setInterpolationDuration(4);e.setTeleportDuration(4);
                    e.getPersistentDataContainer().set(new NamespacedKey("z_lor","beam_column"),org.bukkit.persistence.PersistentDataType.INTEGER,BeamPattern.COLUMNS.indexOf(column));
                    e.getPersistentDataContainer().set(new NamespacedKey("z_lor","beam_ground"),org.bukkit.persistence.PersistentDataType.INTEGER,ground);
                });group.add(display);
            }
            for(var display:group){
                if(!display.isValid())continue;int index=display.getPersistentDataContainer().get(new NamespacedKey("z_lor","beam_column"),org.bukkit.persistence.PersistentDataType.INTEGER);var column=BeamPattern.COLUMNS.get(index);
                double height=(beam.world.getMaxHeight()-beam.world.getMinHeight())*opacity;
                int ground=display.getPersistentDataContainer().get(new NamespacedKey("z_lor","beam_ground"),org.bukkit.persistence.PersistentDataType.INTEGER);
                Location target=new Location(beam.world,beam.origin.getX()+column.x(),warning?ground+.035:beam.world.getMinHeight()+height/2,beam.origin.getZ()+column.z());
                String model=warning?"beam_warning":column.radius()==5?"beam_large":column.radius()==3?"beam_medium":"beam_small";
                var item=new ItemStack(Material.PAPER);item.editMeta(meta->{meta.setItemModel(new NamespacedKey("z_lor",model));var data=meta.getCustomModelDataComponent();data.setFloats(List.of((float)opacity,(float)((p.tick/4)%8)));meta.setCustomModelDataComponent(data);});
                if(!item.equals(display.getItemStack()))display.setItemStack(item);
                // A native coloured outline keeps every damaging column readable
                // against the dark End sky; the colour fades with the surface.
                display.setGlowing(!warning&&opacity>.035);
                var edge=Color.fromRGB(column.edge());double glow=Math.sqrt(Math.clamp(opacity,0,1));
                display.setGlowColorOverride(Color.fromRGB((int)(edge.getRed()*glow),(int)(edge.getGreen()*glow),(int)(edge.getBlue()*glow)));
                float diameter=(float)(column.radius()*2*(warning?1:.8+.2*opacity));
                var transform=new Transformation(new Vector3f(),warning?new Quaternionf().rotateX((float)-Math.PI/2):new Quaternionf(),warning?new Vector3f(diameter,diameter,1):new Vector3f(diameter,(float)Math.max(.001,height),diameter),new Quaternionf());
                if(!display.getTransformation().equals(transform))display.setTransformation(transform);
                if(display.getLocation().distanceSquared(target)>.00001)display.teleport(target);
                for(Player player:beam.world.getPlayers()){
                    boolean near=player.getLocation().distanceSquared(beam.origin)<192*192;if(near&&!player.canSee(display))player.showEntity(p,display);else if(!near&&player.canSee(display))player.hideEntity(p,display);
                }
            }
        }
        for(UUID id:new HashSet<>(columns.keySet()))if(!active.contains(id))stopBeam(id);
    }
    public void stopBeam(UUID id){var group=columns.remove(id);if(group!=null)group.forEach(Entity::remove);}
    private void updateHud(Player player){
        UUID leader=p.dragons.leader(player.getWorld());
        var list=p.dragons.bodies().stream().filter(b->b.entity.getWorld().equals(player.getWorld())).sorted(Comparator.comparing((Dragons.Body b)->b.id.equals(leader)).thenComparing(b->b.kind).thenComparing(b->b.id)).limit(64).toList();
        Component text=Component.empty();int lines=0;
        for(var body:list){if(lines++>=8){text=text.append(Component.text("\nЕщё "+(list.size()-8)+": /lore dragons"));break;}
            if(lines>1)text=text.append(Component.newline());text=text.append(Component.text((body.id.equals(leader)?"★ ":"")+Dragons.name(body.kind)+" "+VoidForecast.input(p.dragons.health(body)/2)+"/"+VoidForecast.input(body.max()/2)+" ",NamedTextColor.WHITE))
                    .append(Component.text("\uE120").font(Key.key("z_lor:icons"))).append(Component.text(" · "+p.dragons.phase(body)+" · "+body.entity.getLocation().getBlockX()+" "+body.entity.getLocation().getBlockY()+" "+body.entity.getLocation().getBlockZ()));
        }
        if(list.isEmpty())text=Component.text("Драконы: нет активных");
        TextDisplay display=hud.get(player.getUniqueId());Location location=player.getEyeLocation().add(player.getLocation().getDirection().multiply(3.2)).add(0,-.8,0);
        if(display==null||!display.isValid()){
            display=player.getWorld().spawn(location,TextDisplay.class,e->{e.setPersistent(false);e.setVisibleByDefault(false);e.setBillboard(Display.Billboard.CENTER);e.setSeeThrough(true);e.setShadowed(true);e.setBackgroundColor(Color.fromARGB(155,16,16,26));e.setBrightness(new Display.Brightness(15,15));e.setLineWidth(500);e.setViewRange(1);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(.35f),new Quaternionf()));e.setTeleportDuration(2);});hud.put(player.getUniqueId(),display);player.showEntity(p,display);
        }
        display.teleport(location);display.text(text);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void move(PlayerMoveEvent event){
        if(event.getTo()==null||!event.hasChangedPosition()||!event.getFrom().getWorld().equals(event.getTo().getWorld()))return;
        Player player=event.getPlayer();var from=event.getFrom();var to=event.getTo();double mx=to.getX()-from.getX(),my=to.getY()-from.getY(),mz=to.getZ()-from.getZ();
        var push=DomeBoundary.drag(p.devices.nearSpheres(to),to.getX(),to.getY(),to.getZ(),mx,my,mz,mx,my,mz,player.isSprinting()||player.isGliding());
        if(push.equals(Domes.Push.NONE))return;
        event.setTo(to.clone().add(push.x(),push.y(),push.z()));player.setVelocity(player.getVelocity().add(new Vector(push.x(),push.y(),push.z())));
    }
    private void removeHud(UUID id){var display=hud.remove(id);if(display!=null)display.remove();}
    private void removeBoundary(UUID id){var display=boundaries.remove(id);if(display!=null)display.remove();}
    public void leave(UUID id){voidViews.remove(id);dragonViews.remove(id);removeHud(id);removeBoundary(id);}
    @Override public void close(){meshes.close();for(UUID id:new HashSet<>(hud.keySet()))removeHud(id);for(UUID id:new HashSet<>(boundaries.keySet()))removeBoundary(id);for(UUID id:new HashSet<>(columns.keySet()))stopBeam(id);voidViews.clear();dragonViews.clear();}
}
