package dev.zpantera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;

/** One physical navigator and two narrow click areas. Visuals never collide or save themselves. */
final class PanteraRuntime {
    final PanteraState state;
    final Wolf base;
    final Map<String,ItemDisplay> parts=new LinkedHashMap<>();
    final List<Entity> entities=new ArrayList<>();
    final Interaction headHit, bodyHit;
    TextDisplay label;
    Location last, progress;
    float yaw, headYaw, headPitch;
    double phase, motion, run;
    boolean moving, blink;
    long lastInteraction=Long.MIN_VALUE, nextTeleport, nextHeart;
    int stuck;
    int lastBlockLight=-1,lastSkyLight=-1;

    PanteraRuntime(ZPanteraPlugin plugin,PanteraState state,Location at) {
        this.state=state; yaw=at.getYaw(); last=at.clone(); progress=at.clone();
        // Keep a list as each spawn completes so a cancelled/failed later spawn can be rolled back.
        try {
            base=at.getWorld().spawn(at,Wolf.class,w->{
                w.setAdult(); w.setAgeLock(true); w.setBreed(false); w.setInvisible(true); w.setSilent(true);
                w.setRemoveWhenFarAway(false); w.setCanPickupItems(false); w.setCollidable(false); w.customName(null);
                w.setCustomNameVisible(false); w.setAngry(false); w.setTarget(null);
                w.getAttribute(Attribute.MAX_HEALTH).setBaseValue(plugin.settings.health());
                w.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(.30);
                // Width .78 fits the remaining opening beside an open wooden door.
                w.getAttribute(Attribute.SCALE).setBaseValue(1.3);
                w.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1.05);
                w.setHealth(Math.min(state.health,plugin.settings.health())); w.setInvulnerable(plugin.settings.invulnerable());
                plugin.mark(w,state.id); entities.add(w);
            });
            plugin.navigation.prepare(base);
            for(String bone:plugin.rig.pivots.keySet()) {
                ItemDisplay d=at.getWorld().spawn(flat(at),ItemDisplay.class,e->{
                    plugin.mark(e,state.id); entities.add(e); e.setItemStack(plugin.model(bone));
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    e.setInterpolationDuration(2); e.setTeleportDuration(2); e.setInterpolationDelay(0);
                    e.setDisplayWidth(5); e.setDisplayHeight(3); e.setViewRange(1.25f);
                    e.setShadowRadius(bone.equals("body")?.48f:0); e.setShadowStrength(.35f);
                }); parts.put(bone,d);
            }
            bodyHit=hit(plugin,at,.83f,1.15f);
            headHit=hit(plugin,at,.72f,.65f);
            updateLabel(plugin);
            frame(plugin,0);
        } catch(RuntimeException error) { for(Entity e:entities) {plugin.unindex(e);e.remove();} throw error; }
    }
    private Interaction hit(ZPanteraPlugin p,Location at,float width,float height) {
        return at.getWorld().spawn(at,Interaction.class,e->{
            p.mark(e,state.id); entities.add(e); e.setInteractionWidth(width);e.setInteractionHeight(height); e.setResponsive(true);
        });
    }
    void updateLabel(ZPanteraPlugin p) {
        if(!p.settings.nameVisible()) {
            if(label!=null) { p.unindex(label); entities.remove(label); label.remove(); label=null; }
            return;
        }
        if(label==null) {
            label=base.getWorld().spawn(flat(base.getLocation()).add(0,1.65,0),TextDisplay.class,e->{
                p.mark(e,state.id); entities.add(e); e.setBillboard(Display.Billboard.CENTER);
                e.setTeleportDuration(2);e.setViewRange(.8f); e.setShadowed(true);e.setSeeThrough(false);
                e.setDefaultBackground(false);e.setBackgroundColor(Color.fromARGB(0,0,0,0));
                e.setDisplayWidth(3);e.setDisplayHeight(.5f);
            });
        }
        label.text(Component.text(p.settings.name(),p.settings.nameColor()));
    }
    void snapshot() { if(base.isValid()&&!base.isDead()) { state.position(base.getLocation());state.health=base.getHealth(); } }
    void updateLighting(ZPanteraPlugin p) {
        int minimum=p.settings.minimumBlockLight();
        if(minimum==0) {
            if(lastBlockLight!=-2)parts.values().forEach(part->part.setBrightness(null));
            lastBlockLight=-2;return;
        }
        var block=base.getLocation().getBlock();
        int blockLight=Math.max(minimum,block.getLightFromBlocks()),skyLight=block.getLightFromSky();
        if(blockLight==lastBlockLight&&skyLight==lastSkyLight)return;
        var light=new Display.Brightness(blockLight,skyLight);
        parts.values().forEach(part->part.setBrightness(light));
        lastBlockLight=blockLight;lastSkyLight=skyLight;
    }
    void remove(ZPanteraPlugin p) { for(Entity e:entities) { p.unindex(e);e.remove(); } entities.clear(); }
    void frame(ZPanteraPlugin p,long tick) {
        // Potion/effect updates can recalculate a living entity's invisibility flag.
        // The navigator must stay hidden independently of such vanilla updates.
        if(!base.isInvisible())base.setInvisible(true);
        if(tick%10==0)updateLighting(p);
        Location at=base.getLocation(); double dx=at.getX()-last.getX(), dz=at.getZ()-last.getZ();
        double distance=Math.sqrt(dx*dx+dz*dz);
        // Teleports do not make the legs spin. Movement is measured, not inferred from owner yaw.
        double targetMotion=distance>.008&&distance<3?Math.min(1,distance/.11):0;
        motion+=(targetMotion-motion)*.30;
        double targetRun=distance>.35&&distance<3?1:0; run+=(targetRun-run)*.2;
        if(distance>.008&&distance<3) yaw=Rig.approachAngle(yaw,(float)Math.toDegrees(Math.atan2(-dx,dz)),18);
        Player owner=state.owner==null?null:Bukkit.getPlayer(state.owner);
        float wantedHead=0,wantedPitch=0;
        if(owner!=null&&owner.getWorld()==at.getWorld()) {
            double ox=owner.getX()-at.getX(),oz=owner.getZ()-at.getZ();
            float absolute=(float)Math.toDegrees(Math.atan2(-ox,oz));
            float delta=Rig.approachAngle(yaw,absolute,50)-yaw;
            wantedHead=Math.max(-50,Math.min(50,delta));
            wantedPitch=(float)-Math.toDegrees(Math.atan2(owner.getEyeLocation().getY()-(at.getY()+1.1),Math.max(.2,Math.hypot(ox,oz))));
            wantedPitch=Math.max(-18,Math.min(18,wantedPitch));
        } else wantedHead=(float)Math.sin(tick*.025)*7;
        headYaw=Rig.approachAngle(headYaw,wantedHead,5);headPitch=Rig.approachAngle(headPitch,wantedPitch,3);
        phase+=.1*(2*Math.PI/(.8-.32*run))*motion;
        var pose=p.rig.pose(yaw,headYaw,headPitch,phase,tick/20.0,motion,run);
        boolean closed=(tick+Math.floorMod(state.id.hashCode(),97))%97<4;
        if(closed!=blink) { parts.get("head").setItemStack(p.model(closed?"head_blink":"head")); blink=closed; }
        Location root=flat(at);
        for(var entry:parts.entrySet()) {
            entry.getValue().teleport(root);entry.getValue().setInterpolationDelay(0);
            entry.getValue().setTransformationMatrix(pose.get(entry.getKey()));
        }
        bodyHit.teleport(root);
        // Hitbox follows the real rotated head rather than staying at the origin.
        Vector3f head=pose.get("head").transformPosition(new Vector3f(0,-.05f,-.28f));
        headHit.teleport(root.clone().add(head.x,head.y-.30,head.z));
        if(label!=null) label.teleport(root.clone().add(0,1.65,0));
        last=at;
    }
    static Location flat(Location at) { Location p=at.clone();p.setYaw(0);p.setPitch(0);return p; }
}
