package dev.zpantera;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityInteractEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.WolfMock;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the Paper API boundary with recording interfaces, without simulating native path search. */
class NativeNavigationTest {
    static class GoalServer extends ServerMock {
        final List<String> calls=new ArrayList<>();
        final Goal<Mob> swim=new Goal<>() {
            public boolean shouldActivate(){return false;}
            public GoalKey<Mob> getKey(){return VanillaGoal.FLOAT;}
            public EnumSet<GoalType> getTypes(){return EnumSet.of(GoalType.JUMP);}
        };
        final MobGoals goals=(MobGoals)Proxy.newProxyInstance(MobGoals.class.getClassLoader(),new Class[]{MobGoals.class},(p,m,args)->{
            calls.add(m.getName());return m.getName().equals("getGoal")?swim:null;
        });
        @Override public MobGoals getMobGoals(){return goals;}
    }
    GoalServer server;PluginTest.Ground world;Wolf wolf;
    List<String> pathCalls;Location requested;double speed;
    @BeforeEach void setup(){
        server=MockBukkit.mock(new GoalServer());world=new PluginTest.Ground("navigation");server.addWorld(world);PluginTest.floor(world);
        pathCalls=new ArrayList<>();
        Pathfinder path=(Pathfinder)Proxy.newProxyInstance(Pathfinder.class.getClassLoader(),new Class[]{Pathfinder.class},(p,m,args)->{
            pathCalls.add(m.getName());
            if(m.getName().equals("moveTo")){requested=((Location)args[0]).clone();speed=(double)args[1];return true;}
            if(m.getReturnType()==boolean.class)return false;return null;
        });
        wolf=new WolfMock(server,UUID.randomUUID()){@Override public Pathfinder getPathfinder(){return path;}};
        ((WolfMock)wolf).setLocation(new Location(world,.5,65,.5));server.registerEntity((WolfMock)wolf);
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void removesCombatGoalsButKeepsSwimmingAndConfiguresGroundPathfinder(){
        wolf.setTarget(server.addPlayer("Enemy"));new Navigation.Paper().prepare(wolf);
        assertEquals(List.of("getGoal","removeAllGoals","addGoal"),server.calls);
        assertTrue(pathCalls.containsAll(List.of("setCanFloat","setCanPassDoors","setCanOpenDoors")));assertNull(wolf.getTarget());
    }
    @Test void movementAndStopAreSentToTheNativePathfinder(){
        var nav=new Navigation.Paper();Location target=new Location(world,5,65,.5);assertTrue(nav.move(wolf,target,1.55));
        assertEquals(target,requested);assertEquals(1.55,speed);nav.stop(wolf);assertTrue(pathCalls.contains("stopPathfinding"));
    }
    @Test void opensWoodenDoorAndHonoursCancelledDoorInteraction(){
        var block=world.getBlockAt(1,65,0);block.setType(Material.OAK_DOOR);
        Door door=(Door)block.getBlockData();door.setOpen(false);block.setBlockData(door);
        var nav=new Navigation.Paper();nav.move(wolf,new Location(world,5,65,.5),1);assertTrue(((Door)block.getBlockData()).isOpen());
        door.setOpen(false);block.setBlockData(door);
        var plugin=MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvent(EntityInteractEvent.class,new Listener(){},EventPriority.NORMAL,(l,e)->((EntityInteractEvent)e).setCancelled(true),plugin);
        nav.move(wolf,new Location(world,5,65,.5),1);assertFalse(((Door)block.getBlockData()).isOpen());
        block.setType(Material.IRON_DOOR);nav.move(wolf,new Location(world,5,65,.5),1);assertFalse(((Door)block.getBlockData()).isOpen());
    }
}
