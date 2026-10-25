package dev.zlor.rules;

import java.util.*;

/** A flight attempt has its own timer; landing, exit and disabled rules clear it. */
public final class FlightFailures {
    private final Map<UUID,Long> starts=new HashMap<>();
    public boolean fold(UUID id,boolean eligible,double seconds,long now){
        if(!eligible||!Double.isFinite(seconds)){starts.remove(id);return false;}
        EndSettings.range(seconds,0.2,14400,"интервал складывания");
        long start=starts.computeIfAbsent(id,key->now);
        if(now-start<seconds*1000)return false;
        starts.remove(id);return true;
    }
    public double remaining(UUID id,double interval,long now){
        var start=starts.get(id);
        return start==null||!Double.isFinite(interval)?Double.POSITIVE_INFINITY:Math.max(0,interval-(now-start)/1000d);
    }
    public void remove(UUID id){starts.remove(id);}
    public void clear(){starts.clear();}
}
