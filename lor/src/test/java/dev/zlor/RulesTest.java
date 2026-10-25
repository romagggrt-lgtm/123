package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test void numericInputAndMeterKeepAllFourDecimalDigits(){
        assertEquals(19,DimensionVoid.parse("19,0000"));assertEquals(-1.25,DimensionVoid.parse("-1.25"));
        for(String bad:List.of("NaN","Infinity","1e3","30000001","1.12345678901","1.2x"))assertThrows(IllegalArgumentException.class,()->DimensionVoid.parse(bad));
        assertEquals("19,0000",VoidForecast.number(19));assertEquals("0,0000",VoidForecast.number(-.000001));assertEquals("−1,2500".replace('−','-'),VoidForecast.number(-1.25));
    }
    @Test void speedModesReplaceAndVectorUseOneCanonicalRate(){
        var state=DimensionVoid.vanilla(-128,0).set(20,0).speed(.5,true,0);assertEquals(120,state.perFourHours());assertEquals(20.5,state.height(60_000),1e-9);
        state=state.speedVector(-.1,true,60_000);assertEquals(96,state.perFourHours(),1e-9);assertEquals(20.9,state.height(120_000),1e-9);
        var frozen=state.speed(0,false,120_000);assertEquals(20.9,frozen.height(9_000_000),1e-9);
        assertEquals(-128,DimensionVoid.vanilla(-128,120_000).height(9_000_000));
    }
    @Test void ceilingStopsOvershootRatherThanHidingIt(){var state=new DimensionVoid(true,10,240,0,true).limited(20,1_000_000);assertEquals(20,state.baseY());assertEquals(0,state.perFourHours());assertEquals(20,state.height(100_000_000));}
    @Test void sensorThresholdsAndPulsesMatchApprovedRange(){
        var sensor=new AlarmSettings(64,0);assertEquals(0,sensor.strength(64));assertFalse(sensor.warning(64));assertEquals(8,sensor.strength(32));assertEquals(15,sensor.strength(0));assertEquals(10_000,sensor.interval(64));assertEquals(200,sensor.interval(0));
        assertThrows(IllegalArgumentException.class,()->new AlarmSettings(10,10));
    }
    @Test void flightDependsOnVoidHeightAndAbsorberOnlyMultipliesInterval(){
        var f=new RestrictionSettings.Flight(true,0,60,120,true,100);assertEquals(Double.POSITIVE_INFINITY,f.interval(-1));assertEquals(120,f.interval(0));assertEquals(.2,f.interval(60));assertFalse(f.blocked(99));assertTrue(f.blocked(100));assertEquals(.8,Domes.flightInterval(f.interval(70),true));
        assertTrue(new RestrictionSettings.Gate(true,50,"portal",.5).blocks(50.0001));assertFalse(new RestrictionSettings.Gate(true,50,"portal",.5).blocks(50));
    }
    @Test void beamLatticeContainsAllThreeSizesAtOnce(){
        assertEquals(151,BeamPattern.COLUMNS.size());assertEquals(19,BeamPattern.COLUMNS.stream().filter(c->c.radius()==5).count());assertEquals(54,BeamPattern.COLUMNS.stream().filter(c->c.radius()==3).count());assertEquals(78,BeamPattern.COLUMNS.stream().filter(c->c.radius()==1.5).count());
        assertTrue(BeamPattern.hits(-.3,-.3,.3,.3,0,0));for(var c:BeamPattern.COLUMNS)assertTrue(Math.hypot(c.x(),c.z())+c.radius()<=50.00001);
    }
    @Test void beamDamageCannotKillBeforeSeventhDistinctAttack(){for(int hit=1;hit<=6;hit++){assertEquals(0,DragonRules.damage(1,hit,4));assertEquals(.5,DragonRules.damage(1.5f,hit,4));assertEquals(2,DragonRules.damage(20,hit,4));}assertEquals(2,DragonRules.damage(1,7,4));assertEquals(6,DragonRules.lost(7));}
    @Test void sharedGeometryIsGeodesicAndSphereIndexKeepsKindsSeparate(){
        assertEquals(1920,HexDomeMesh.segments(1).size());var purple=new Domes.Sphere(0,0,0,50,Domes.Kind.ABSORBER);var yellow=new Domes.Sphere(80,0,0,3,Domes.Kind.LOOT);var index=new Domes.Index(List.of(purple,yellow));
        assertEquals(0,Domes.damageFactor(index.near(0,0),0,0,0));assertEquals(.125,Domes.damageFactor(index.near(80,0),80,0,0));assertEquals(1,Domes.damageFactor(index.near(200,0),200,0,0));
    }
    @Test void boundaryDoesNotPullInsideOrBlockEnteringAndTeleports(){
        var sphere=new Domes.Sphere(0,0,0,50,Domes.Kind.ABSORBER);assertEquals(Domes.Push.NONE,DomeBoundary.drag(List.of(sphere),0,0,0,.2,0,0,.2,0,0));assertEquals(Domes.Push.NONE,DomeBoundary.drag(List.of(sphere),50,0,0,-.2,0,0,-.2,0,0));assertEquals(Domes.Push.NONE,DomeBoundary.drag(List.of(sphere),50,0,0,9,0,0,9,0,0));
        assertTrue(DomeBoundary.drag(List.of(sphere),50,0,0,.2,0,0,.2,0,0).x()<0);
    }
    @Test void lootIsNotFullProtectionOrAnExtraFlightMultiplier(){var yellow=new Domes.Sphere(0,0,0,3,Domes.Kind.LOOT);assertEquals(.125,Domes.damageFactor(List.of(yellow,yellow),0,0,0));assertEquals(0,Domes.damageFactor(List.of(yellow,new Domes.Sphere(0,0,0,50,Domes.Kind.ABSORBER)),0,0,0));}
    @Test void waveCompositionDoesNotInsertKingOrAutostart(){assertArrayEquals(new int[]{1,0},Dragons.WAVES[0]);assertArrayEquals(new int[]{1,1},Dragons.WAVES[4]);assertArrayEquals(new int[]{0,4},Dragons.WAVES[9]);assertEquals(10,Dragons.WAVES.length);assertFalse(new State.Arena().waveActive());}
    @Test void relayRetainsShortPulseAndShutdownEdgeWhileReceiverLoads(){
        var relay=new RelaySession();assertTrue(relay.tick(15,false).ticket());relay.tick(0,false);
        assertEquals(15,relay.tick(0,true).output());assertEquals(0,relay.tick(0,true).output());for(int i=0;i<25;i++)relay.tick(0,true);assertTrue(relay.idle());
    }
    @Test void entryHeightAlwaysFollowsByTenAndBothDirectionsAreAllowed(){assertEquals(30,EndStructureHeights.entryY(20));assertEquals(-10,EndStructureHeights.entryY(-20));assertEquals(20,EndStructureHeights.minimumPortalY(19,-64));}
}
