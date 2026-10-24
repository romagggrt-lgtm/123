package dev.zpantera;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Reads the actual exported Blockbench pivots. World model front is local -Z. */
final class Rig {
    final Map<String,Vector3f> pivots=new LinkedHashMap<>();
    Rig(InputStream in) throws IOException {
        try(var reader=new InputStreamReader(Objects.requireNonNull(in,"rig.json"),StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pivots");
            for(var e:root.entrySet()) {
                var a=e.getValue().getAsJsonArray();
                pivots.put(e.getKey(),new Vector3f(a.get(0).getAsFloat()/16,a.get(1).getAsFloat()/16,a.get(2).getAsFloat()/16));
            }
        }
        if(pivots.size()!=9) throw new IOException("Expected 9 animated bones");
    }
    Map<String,Matrix4f> pose(float yaw,float headYaw,float headPitch,double phase,double seconds,double motion,double run) {
        double idle=Math.sin(seconds*1.6);
        Matrix4f root=root(yaw,phase,motion,run);
        Map<String,Matrix4f> out=new LinkedHashMap<>();
        out.put("body",new Matrix4f(root));
        // Minecraft pitch is negative when looking up; local model front is -Z.
        out.put("head",at(root,"head").rotateY(rad(-headYaw)).rotateX(rad(-headPitch+(float)(idle*1.2*(1-motion)))));
        int i=0;
        for(String bone:List.of("front_left","front_right","rear_left","rear_right")) {
            double sign=(i==0||i==3)?1:-1; i++;
            float swing=(float)(Math.sin(phase)*sign*(25+18*run)*motion);
            out.put(bone,at(root,bone).rotateX(rad(swing)));
        }
        Matrix4f chain=null;
        for(i=0;i<3;i++) {
            String bone="tail_"+(i+1);
            if(i==0) chain=at(root,bone);
            else chain=new Matrix4f(chain).translate(new Vector3f(pivots.get(bone)).sub(pivots.get("tail_"+i)));
            chain.rotateY(rad((float)(Math.sin(seconds*2-i*.55)*(7+i*3))))
                .rotateX(rad(i==0? (float)(-5-6*run): (float)(4+Math.sin(seconds*2-i*.5)*2)));
            out.put(bone,new Matrix4f(chain));
        }
        return out;
    }
    record HeadAim(float yaw,float pitch) {}
    HeadAim headAim(float bodyYaw,double phase,double motion,double run,Vector3f targetFromFeet) {
        // Aim from the real neck hinge and account for the body's current lean/bob.
        Vector3f local=at(root(bodyYaw,phase,motion,run),"head").invert()
            .transformPosition(new Vector3f(targetFromFeet));
        double horizontal=Math.hypot(local.x,local.z);
        float yaw=horizontal<.02?0:(float)Math.toDegrees(Math.atan2(local.x,-local.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(local.y,Math.max(.001,horizontal)));
        return new HeadAim(yaw,pitch);
    }
    private Matrix4f root(float yaw,double phase,double motion,double run) {
        float bob=(float)(Math.abs(Math.sin(phase*2))*.018*motion);
        return new Matrix4f().rotateY(rad(180-yaw)).translate(0,bob,0).rotateX(rad((float)(-4*run)));
    }
    static Matrix4f itemDisplayTransform(Matrix4f logicalPose) {
        // Vanilla ItemDisplayRenderer appends Y=180 degrees AFTER the supplied matrix.
        // Undo that local mesh flip for every bone. Do not rotate its world-space hinge.
        return new Matrix4f(logicalPose).rotateY((float)Math.PI);
    }
    static float heading(double dx,double dz) { return (float)Math.toDegrees(Math.atan2(-dx,dz)); }
    private Matrix4f at(Matrix4f root,String bone) { return new Matrix4f(root).translate(pivots.get(bone)); }
    static float rad(float degrees) { return degrees*(float)(Math.PI/180); }
    static float approachAngle(float current,float wanted,float max) {
        float delta=(wanted-current)%360; if(delta>180)delta-=360; if(delta< -180)delta+=360;
        return current+Math.max(-max,Math.min(max,delta));
    }
}
