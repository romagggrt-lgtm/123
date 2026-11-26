package dev.ztest;

import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public final class RiftManager implements Listener {
    private final ZTestPlugin p;
    private final Map<UUID,Long> cooldown=new HashMap<>();
    private final NamespacedKey enteredRift;
    private long frame;
    private static final Particle.DustOptions PURPLE=new Particle.DustOptions(Color.fromRGB(168,50,255),1.15f);
    private static final Particle.DustOptions LIGHT=new Particle.DustOptions(Color.fromRGB(235,188,255),.85f);
    RiftManager(ZTestPlugin p){this.p=p;this.enteredRift=new NamespacedKey(p,"entry_rift");}
    void start(){Bukkit.getScheduler().runTaskTimer(p,this::draw,20L,6L);}
    public Rift create(Player player)throws IOException {
        if(player.getWorld().equals(p.purgatory))throw new IllegalArgumentException("Создавайте разлом в обычном мире или Незере");
        if(p.state.rifts().size()>=Math.max(1,Math.min(256,p.getConfig().getInt("max-rifts",32))))throw new IllegalArgumentException("Достигнут предел разломов");
        Vector direction=player.getLocation().getDirection().setY(0);if(direction.lengthSquared()<.01)direction=new Vector(0,0,1);else direction.normalize();
        Location suggested=player.getLocation().add(direction.multiply(3));
        Point source=findPortal(suggested.getWorld(),suggested.getBlockX(),suggested.getBlockY(),suggested.getBlockZ(),6);
        if(source==null)throw new IllegalArgumentException("Не нашлось безопасного места рядом: нужны твёрдый пол и свободное пространство");
        Point destination=findPortal(p.purgatory,source.x(),Terrain.GROUND+1,source.z(),24);
        if(destination==null)throw new IllegalArgumentException("В Чистилище рядом не найдено свободное место");
        var r=new Rift(UUID.randomUUID(),source,destination);p.state.addRift(r);
        cooldown.put(player.getUniqueId(),System.currentTimeMillis()+4000);return r;
    }
    private Point findPortal(World world,int x,int y,int z,int radius){
        for(int r=0;r<=radius;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(r>0&&Math.abs(dx)!=r&&Math.abs(dz)!=r)continue;
            for(int dy:new int[]{0,1,-1,2,-2}){
                int px=x+dx,py=y+dy,pz=z+dz;
                if(!safeColumn(world,px,py,pz,3))continue;
                Point point;try{point=new Point(world.getName(),px,py,pz);}catch(IllegalArgumentException ex){continue;}
                boolean overlaps=false;for(Rift other:p.state.rifts())if(distance(point,other.source())<25||distance(point,other.destination())<25){overlaps=true;break;}
                if(!overlaps)return point;
            }
        }return null;
    }
    private static double distance(Point a,Point b){if(!a.world().equals(b.world()))return Double.POSITIVE_INFINITY;long dx=(long)a.x()-b.x(),dz=(long)a.z()-b.z();return (double)dx*dx+(double)dz*dz;}
    private static boolean safeColumn(World w,int x,int y,int z,int height){
        if(y<=w.getMinHeight()||y+height>=w.getMaxHeight()||!w.getWorldBorder().isInside(new Location(w,x+.5,y,z+.5)))return false;
        Block floor=w.getBlockAt(x,y-1,z);if(!floor.getType().isSolid()||hazard(floor.getType()))return false;
        for(int k=0;k<height;k++){Block b=w.getBlockAt(x,y+k,z);if(!b.isPassable()||b.isLiquid()||hazard(b.getType()))return false;}
        return true;
    }
    private static boolean hazard(Material m){return switch(m){case LAVA,FIRE,SOUL_FIRE,MAGMA_BLOCK,CACTUS,CAMPFIRE,SOUL_CAMPFIRE,POWDER_SNOW,SWEET_BERRY_BUSH,WITHER_ROSE -> true;default -> false;};}
    private Location safeExit(Point target,Player player){
        World w=Bukkit.getWorld(target.world());if(w==null)return null;
        for(int r=2;r<=8;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.abs(dx)!=r&&Math.abs(dz)!=r)continue;
            for(int dy:new int[]{0,1,-1,2,-2})if(safeColumn(w,target.x()+dx,target.y()+dy,target.z()+dz,2))return new Location(w,target.x()+dx+.5,target.y()+dy,target.z()+dz+.5,player.getLocation().getYaw(),player.getLocation().getPitch());
        }return null;
    }
    public Rift nearest(Location location,double radius){Rift best=null;double dist=radius*radius;for(Rift r:p.state.rifts())for(Point point:List.of(r.source(),r.destination())){double d=point.distanceSquared(location);if(d<=dist){dist=d;best=r;}}return best;}
    public Point nearestEndpoint(Location location){Point best=null;double dist=Double.POSITIVE_INFINITY;for(Rift r:p.state.rifts())for(Point point:List.of(r.source(),r.destination())){double d=point.distanceSquared(location);if(d<dist){dist=d;best=point;}}return best;}
    public void remove(Rift r)throws IOException{p.state.removeRift(r.id());}
    public void emergencyReturn(Player player){
        if(!player.getWorld().equals(p.purgatory)){player.sendMessage(ZTestPlugin.msg("§cКоманда работает только в Чистилище."));return;}
        String id=player.getPersistentDataContainer().get(enteredRift,PersistentDataType.STRING);
        if(id!=null&&p.state.rifts().stream().anyMatch(r->r.id().toString().equals(id))){player.sendMessage(ZTestPlugin.msg("§eВаш разлом ещё открыт — вернитесь через него."));return;}
        World overworld=Bukkit.getWorlds().stream().filter(w->!w.equals(p.purgatory)&&w.getEnvironment()==World.Environment.NORMAL).findFirst().orElse(null);
        if(overworld==null){player.sendMessage(ZTestPlugin.msg("§cОбычный мир не загружен. Позовите администратора."));return;}
        Location spawn=overworld.getSpawnLocation();Point point=new Point(overworld.getName(),spawn.getBlockX(),spawn.getBlockY(),spawn.getBlockZ());Location exit=safeExit(point,player);
        if(exit==null||!player.teleport(exit,PlayerTeleportEvent.TeleportCause.PLUGIN)){player.sendMessage(ZTestPlugin.msg("§cНе найден безопасный выход. Позовите администратора."));return;}
        player.getPersistentDataContainer().remove(enteredRift);cooldown.put(player.getUniqueId(),System.currentTimeMillis()+5000);player.sendMessage(ZTestPlugin.msg("§dРазлом закрыт — вы вернулись к спавну."));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        Location to=e.getTo(),from=e.getFrom();if(to==null||to.getWorld()==null||!Objects.equals(from.getWorld(),to.getWorld())||from.distanceSquared(to)<.0001)return;
        Player player=e.getPlayer();long now=System.currentTimeMillis();if(cooldown.getOrDefault(player.getUniqueId(),0L)>now)return;
        for(Rift r:p.state.rifts()){
            Point target=null;if(inside(r.source(),to))target=r.destination();else if(inside(r.destination(),to))target=r.source();if(target==null)continue;
            Location exit=safeExit(target,player);cooldown.put(player.getUniqueId(),now+Math.max(2,Math.min(60,p.getConfig().getInt("portal-cooldown-seconds",5)))*1000L);
            if(exit==null){player.sendMessage(ZTestPlugin.msg("§cБезопасный выход не найден. Позовите администратора."));return;}
            if(player.teleport(exit,PlayerTeleportEvent.TeleportCause.PLUGIN)){
                if(target.world().equals(p.worldName))player.getPersistentDataContainer().set(enteredRift,PersistentDataType.STRING,r.id().toString());else player.getPersistentDataContainer().remove(enteredRift);
                player.sendMessage(ZTestPlugin.msg(target.world().equals(p.worldName)?"§dВы вошли в Чистилище.":"§dВы вышли из Чистилища."));
            }
            else player.sendMessage(ZTestPlugin.msg("§cПереход отменён. Проверьте права на телепортацию."));return;
        }
    }
    private boolean inside(Point point,Location l){if(l.getWorld()==null||!point.world().equals(l.getWorld().getName()))return false;double dx=l.getX()-(point.x()+.5),dz=l.getZ()-(point.z()+.5),dy=l.getY()-point.y();return dx*dx+dz*dz<.8*.8&&dy>=-.35&&dy<2.8;}
    @EventHandler public void quit(PlayerQuitEvent e){cooldown.remove(e.getPlayer().getUniqueId());}
    private void draw(){frame++;for(Rift r:p.state.rifts()){draw(r.source());draw(r.destination());}}
    private void draw(Point point){World w=Bukkit.getWorld(point.world());if(w==null||!w.isChunkLoaded(point.x()>>4,point.z()>>4))return;Location center=point.center();if(center==null||w.getPlayers().stream().noneMatch(pl->pl.getLocation().distanceSquared(center)<48*48))return;
        for(int i=0;i<24;i++){double angle=2*Math.PI*i/24,skew=.15*Math.sin(angle*3+frame*.28);double x=center.getX()+(.72+skew)*Math.cos(angle),y=center.getY()+1.45+1.4*Math.sin(angle);w.spawnParticle(Particle.DUST,x,y,center.getZ(),1,0,0,0,0,i%3==0?LIGHT:PURPLE);}
        if(frame%2==0)w.spawnParticle(Particle.REVERSE_PORTAL,center.getX(),center.getY()+1.35,center.getZ(),3,.35,.85,.08,.015);
    }
}
