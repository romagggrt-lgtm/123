package dev.zpantera;

import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Vanilla appends a local Y=180 rotation after the server's ItemDisplay matrix. */
class RenderingTest {
    Rig rig()throws Exception{return new Rig(getClass().getResourceAsStream("/rig.json"));}
    static Matrix4f clientMatrix(Matrix4f sent){return new Matrix4f(sent).rotateY((float)Math.PI);}
    @Test void allRenderedBonesMatchLogicalGeometryWithoutMovingTheirHinges()throws Exception{
        Rig rig=rig();
        for(float yaw:new float[]{0,90,180,-90,179,-179,540})for(float head:new float[]{-35,0,35})
            for(float pitch:new float[]{-15,0,15})for(double run:new double[]{0,1}) {
                var poses=rig.pose(yaw,head,pitch,.37,1.1,1,run);
                for(var entry:poses.entrySet()) {
                    Matrix4f original=new Matrix4f(entry.getValue()),sent=Rig.itemDisplayTransform(entry.getValue());
                    assertEquals(original,entry.getValue(),"Rendering must not mutate the hitbox pose");
                    assertTrue(original.getTranslation(new Vector3f()).distance(sent.getTranslation(new Vector3f()))<1e-6);
                    Matrix4f visible=clientMatrix(sent);
                    for(Vector3f point:List.of(new Vector3f(),new Vector3f(.2f,-.4f,-.6f),new Vector3f(-.3f,.2f,.5f))) {
                        Vector3f expected=original.transformPosition(new Vector3f(point));
                        Vector3f actual=visible.transformPosition(new Vector3f(point));
                        assertTrue(expected.distance(actual)<1e-5,entry.getKey());
                    }
                }
            }
    }
    @Test void visibleBodyFacesTheMinecraftHeadingAndReproducesTheOldBackwardBug()throws Exception{
        Rig rig=rig();float[] headings={0,90,180,-90};
        Vector3f[] compass={new Vector3f(0,0,1),new Vector3f(-1,0,0),new Vector3f(0,0,-1),new Vector3f(1,0,0)};
        for(int i=0;i<headings.length;i++) {
            Matrix4f logical=rig.pose(headings[i],0,0,0,0,0,0).get("body");
            Vector3f actual=clientMatrix(Rig.itemDisplayTransform(logical)).transformDirection(new Vector3f(0,0,-1));
            assertTrue(actual.distance(compass[i])<1e-5);
            Vector3f old=clientMatrix(logical).transformDirection(new Vector3f(0,0,-1));
            assertTrue(old.dot(compass[i])<-.999,"The uncorrected 1.0.1 matrix renders backward");
        }
    }
    @Test void lookingUpAndTurningLeftHaveTheCorrectSignsAfterTheClientRotation()throws Exception{
        Rig rig=rig();
        Vector3f up=clientMatrix(Rig.itemDisplayTransform(rig.pose(0,30,-15,0,0,0,0).get("head")))
            .transformDirection(new Vector3f(0,0,-1));
        assertTrue(up.y>0);assertTrue(up.x<0);assertTrue(up.z>0);
        Vector3f down=clientMatrix(Rig.itemDisplayTransform(rig.pose(0,-30,15,0,0,0,0).get("head")))
            .transformDirection(new Vector3f(0,0,-1));
        assertTrue(down.y<0);assertTrue(down.x>0);assertTrue(down.z>0);
    }
    @Test void aimUsesTheActualHeadHingeInsteadOfTheFootPosition()throws Exception{
        Rig rig=rig();float expected=(float)-Math.toDegrees(Math.atan2(1.62-17.0/16,3-7.0/16));
        var south=rig.headAim(0,0,0,0,new Vector3f(0,1.62f,3));
        var west=rig.headAim(90,0,0,0,new Vector3f(-3,1.62f,0));
        assertEquals(0,south.yaw(),1e-4);assertEquals(expected,south.pitch(),1e-4);
        assertEquals(0,west.yaw(),1e-4);assertEquals(expected,west.pitch(),1e-4);
    }
    @Test void sideTargetsTurnTheHeadTowardTheirActualSide()throws Exception{
        Rig rig=rig();
        assertTrue(rig.headAim(0,0,0,0,new Vector3f(-2,1.62f,2)).yaw()>0);
        assertTrue(rig.headAim(0,0,0,0,new Vector3f(2,1.62f,2)).yaw()<0);
    }
}
