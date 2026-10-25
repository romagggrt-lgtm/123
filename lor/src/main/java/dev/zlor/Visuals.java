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
public final class Visuals implements Listener,AutoCloseable {
    private record Dome(UUID id,Location center,double radius,Color color,long age,long remaining,boolean reserves){}
    private final ZLorPlugin p;
    private final Set<UUID> voidViews=new HashSet<>(),dragonViews=new HashSet<>();
    private final Map<UUID,TextDisplay> hud=new HashMap<>();
    private final Map<UUID,List<ItemDisplay>> columns=new HashMap<>();
    private final Map<UUID,Long> opened=new HashMap<>();
    private static final List<HexDomeMesh.Segment> GRID=HexDomeMesh.segments(1);
    public Visuals(ZLorPlugin p){this.p=p;}
    public void toggleVoid(Player player){p.roles.require(player);if(!voidViews.add(player.getUniqueId()))voidViews.remove(player.getUniqueId());player.sendMessage("Личная граница: "+(voidViews.contains(player.getUniqueId())?"включена":"выключена"));}
    public void toggleDragons(Player player){p.roles.require(player);if(!dragonViews.add(player.getUniqueId())){dragonViews.remove(player.getUniqueId());removeHud(player.getUniqueId());}player.sendMessage("Личный HUD драконов: "+(dragonViews.contains(player.getUniqueId())?"включён":"выключен"));}
    public void showAll(Player player){p.roles.require(player);boolean both=voidViews.contains(player.getUniqueId())&&dragonViews.contains(player.getUniqueId());if(both){voidViews.remove(player.getUniqueId());dragonViews.remove(player.getUniqueId());removeHud(player.getUniqueId());}else{voidViews.add(player.getUniqueId());dragonViews.add(player.getUniqueId());}player.sendMessage("Личные визуализации "+(both?"выключены":"включены"));}
    public void tick(){
        int interval=Math.max(2,p.getConfig().getInt("effects.interval-ticks",5));
        if(p.tick%interval!=0)return;
        Map<World,List<Dome>> domes=new HashMap<>();
        for(var d:p.devices.loaded())if(d.active(p.fuelNow())){
            long start=opened.computeIfAbsent(d.id,id->p.now());var center=p.devices.center(d);domes.computeIfAbsent(center.getWorld(),k->new ArrayList<>()).add(new Dome(d.id,center,50,Color.fromRGB(159,132,240),p.now()-start,d.remaining(p.fuelNow()),d.reserves()>0));
        }
        opened.keySet().removeIf(id->!p.state().devices.containsKey(id)||!p.state().devices.get(id).active(p.fuelNow()));
        for(var zone:p.loot.zones())domes.computeIfAbsent(zone.center().getWorld(),k->new ArrayList<>()).add(new Dome(zone.id(),zone.center(),3,Color.YELLOW,Long.MAX_VALUE,zone.expires()-p.now(),true));
        for(World world:Bukkit.getWorlds())if(world.getEnvironment()==World.Environment.THE_END){var s=p.structures.current(world);if(p.structures.entryReady(world,s)&&s.move==null)domes.computeIfAbsent(world,k->new ArrayList<>()).add(new Dome(UUID.nameUUIDFromBytes((p.worldKey(world)+"entry").getBytes(java.nio.charset.StandardCharsets.UTF_8)),new Location(world,s.entryX+.5,s.entryY+1,s.entryZ+.5),Math.sqrt(12.5)+1,Color.LIME,Long.MAX_VALUE,Long.MAX_VALUE,true));}
        int budget=Math.clamp(p.getConfig().getInt("effects.particle-budget-per-player",320),16,1024);
        double range=Math.clamp(p.getConfig().getDouble("effects.range",96),16,192);
        for(Player player:Bukkit.getOnlinePlayers()){
            List<Dome> nearby=domes.getOrDefault(player.getWorld(),List.of()).stream().filter(d->d.center.distanceSquared(player.getLocation())<=Math.pow(d.radius+range,2)).sorted(Comparator.comparingDouble(d->Math.abs(Math.sqrt(d.center.distanceSquared(player.getLocation()))-d.radius))).limit(Math.clamp(p.getConfig().getInt("effects.nearest-domes",16),1,16)).toList();
            if(!nearby.isEmpty())drawDomes(player,nearby,budget*4/10);
            if(voidViews.contains(player.getUniqueId())&&p.roles.scen(player))drawBoundary(player,budget/10);
            if(dragonViews.contains(player.getUniqueId())&&p.roles.scen(player))updateHud(player);
            drawBeams(player,budget*4/10);
            drawSummon(player,budget/10);
        }
        if(p.getConfig().getBoolean("effects.beams",true))updateColumns();
    }
    private void drawDomes(Player player,List<Dome> domes,int budget){
        int each=Math.max(1,budget/domes.size());
        for(Dome dome:domes){
            int sent=0,start=(int)((p.tick/5*997+Integer.toUnsignedLong(dome.id.hashCode()))%GRID.size());
            for(int step=0;step<GRID.size()&&sent<each;step++){
                var segment=GRID.get((start+step*13)%GRID.size());float[] q=segment.quad();double x=(q[0]+q[6])/2d,y=(q[1]+q[7])/2d,z=(q[2]+q[8])/2d;
                if(dome.radius==50&&!DomeAnimation.visible(segment.first(),segment.firstY(),dome.id.getMostSignificantBits(),dome.age,dome.remaining,!dome.reserves))continue;
                Location point=dome.center.clone().add(x*dome.radius,y*dome.radius,z*dome.radius);
                if(point.distanceSquared(player.getEyeLocation())>96*96)continue;
                if(dome.radius==50&&domes.stream().anyMatch(other->other!=dome&&other.radius==50&&other.center.distanceSquared(point)<2499.9))continue;
                player.spawnParticle(Particle.DUST,point,1,0,0,0,0,new Particle.DustOptions(dome.color,dome.radius==50?.7f:.5f));sent++;
            }
        }
    }
    private void drawBoundary(Player player,int budget){
        double y=p.voids.height(player.getWorld());int rootX=Math.floorDiv(player.getLocation().getBlockX(),4)*4,rootZ=Math.floorDiv(player.getLocation().getBlockZ(),4)*4;
        for(int i=0;i<budget;i++){int x=i%8-4,z=i/8-3;player.spawnParticle(Particle.DUST,new Location(player.getWorld(),rootX+x*4,y,rootZ+z*4),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(112,67,145),.6f));}
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
            long elapsed=p.tick-beam.started;boolean warning=elapsed<p.getConfig().getInt("dragons.beam-warning-ticks",30);int per=Math.max(1,budget/BeamPattern.COLUMNS.size()),sent=0;
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
        var active=new HashSet<UUID>();
        for(var beam:p.dragons.beams()){
            if(p.tick-beam.started<p.getConfig().getInt("dragons.beam-warning-ticks",30))continue;
            active.add(beam.id);if(columns.containsKey(beam.id)){
                for(var display:columns.get(beam.id))if(display.isValid())for(Player player:beam.world.getPlayers()){
                    boolean near=player.getLocation().distanceSquared(beam.origin)<192*192;if(near&&!player.canSee(display))player.showEntity(p,display);else if(!near&&player.canSee(display))player.hideEntity(p,display);
                }
                continue;
            }var created=new ArrayList<ItemDisplay>();
            for(var column:BeamPattern.COLUMNS){
                int x=(int)Math.floor(beam.origin.getX()+column.x()),z=(int)Math.floor(beam.origin.getZ()+column.z());if(!beam.world.isChunkLoaded(x>>4,z>>4))continue;
                var item=new ItemStack(org.bukkit.Material.PURPLE_STAINED_GLASS);String model=column.radius()==5?"beam_large":column.radius()==3?"beam_medium":"beam_small";item.editMeta(m->m.setItemModel(new NamespacedKey("z_lor",model)));
                var display=beam.world.spawn(new Location(beam.world,beam.origin.getX()+column.x(),(beam.world.getMinHeight()+beam.world.getMaxHeight())/2d,beam.origin.getZ()+column.z()),ItemDisplay.class,e->{
                    e.setItemStack(item);e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setVisibleByDefault(false);e.setBrightness(new Display.Brightness(15,15));e.setViewRange(2);
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f((float)(column.radius()*2),beam.world.getMaxHeight()-beam.world.getMinHeight(),(float)(column.radius()*2)),new Quaternionf()));
                });
                for(Player player:beam.world.getPlayers())if(player.getLocation().distanceSquared(beam.origin)<192*192)player.showEntity(p,display);created.add(display);
            }
            columns.put(beam.id,created);
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
        TextDisplay display=hud.get(player.getUniqueId());Location location=player.getEyeLocation().add(player.getLocation().getDirection().multiply(2)).add(0,-.8,0);
        if(display==null||!display.isValid()){
            display=player.getWorld().spawn(location,TextDisplay.class,e->{e.setPersistent(false);e.setVisibleByDefault(false);e.setBillboard(Display.Billboard.CENTER);e.setSeeThrough(true);e.setShadowed(true);e.setLineWidth(600);e.setViewRange(.2f);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(.012f),new Quaternionf()));e.setTeleportDuration(1);});hud.put(player.getUniqueId(),display);player.showEntity(p,display);
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
    public void leave(UUID id){voidViews.remove(id);dragonViews.remove(id);removeHud(id);}
    @Override public void close(){for(UUID id:new HashSet<>(hud.keySet()))removeHud(id);for(UUID id:new HashSet<>(columns.keySet()))stopBeam(id);voidViews.clear();dragonViews.clear();}
}
