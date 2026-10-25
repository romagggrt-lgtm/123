package dev.zlor.reference;
import dev.zlor.rules.*;

import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EncounterRulesTest {
    @TempDir Path root;
    @Test void exactWaveComposition(){assertEquals(List.of(1,1,2,2,2,2,3,3,3,4),Encounters.WAVES.stream().map(List::size).toList());assertEquals(List.of(Encounters.Type.ENRICHED),Encounters.WAVES.get(1));assertTrue(Encounters.WAVES.stream().flatMap(List::stream).noneMatch(t->t==Encounters.Type.VOID_KING));}
    @Test void onlyTrackedDeathWinsAndPersists()throws Exception{var store=new Encounters.Store(root.resolve("wave"));var run=Encounters.start(Encounters.Run.empty(),7,false);store.save(run);assertEquals(run,store.load());assertSame(run,run.death(UUID.randomUUID()));for(var id:run.members().keySet())run=run.death(id);assertEquals(Encounters.Mode.WON,run.mode());assertFalse(run.active());assertThrows(IllegalArgumentException.class,()->Encounters.start(Encounters.start(Encounters.Run.empty(),1,false),2,false));}
    @Test void malformedFilePreserved()throws Exception{var path=root.resolve("bad");Files.writeString(path,"schema=9");assertThrows(java.io.IOException.class,()->new Encounters.Store(path).load());assertEquals("schema=9",Files.readString(path));}
    @Test void recoveryAndReset(){var timer=new KingRecovery(0);for(int i=0;i<1199;i++)assertFalse(timer.tick());assertTrue(timer.tick());for(int i=0;i<599;i++)assertFalse(timer.tick());assertTrue(timer.tick());timer.damaged();assertEquals(1200,timer.remaining());}
    @Test void shieldsDoNotStack(){var loot=new Domes.Sphere(0,0,0,3,Domes.Kind.LOOT);assertEquals(.125,Domes.damageFactor(List.of(loot,loot),0,0,0));assertEquals(0,Domes.damageFactor(List.of(loot,new Domes.Sphere(0,0,0,50,Domes.Kind.ABSORBER)),0,0,0));assertEquals(1,Domes.damageFactor(List.of(loot),4,0,0));assertEquals(.8,Domes.flightInterval(.2,true));}
    @Test void pushOnlyAtExternalExit(){var sphere=new Domes.Sphere(0,0,0,3,Domes.Kind.LOOT);assertEquals(Domes.Push.NONE,Domes.push(List.of(sphere),0,0,0,.1,0,0));assertTrue(Domes.push(List.of(sphere),2.9,0,0,.2,0,0).x()<0);assertEquals(Domes.Push.NONE,Domes.push(List.of(sphere),3,0,0,-.1,0,0));assertEquals(Domes.Push.NONE,Domes.push(List.of(sphere,new Domes.Sphere(4,0,0,3,Domes.Kind.ENTRY)),2.9,0,0,.2,0,0));}
    @Test void indexKeepsBorderCandidates(){var sphere=new Domes.Sphere(-64,0,0,50,Domes.Kind.ABSORBER);var index=new Domes.Index(List.of(sphere));assertTrue(index.near(-114,0).contains(sphere));assertTrue(index.near(-14,0).contains(sphere));assertTrue(index.near(200,0).isEmpty());}
}
