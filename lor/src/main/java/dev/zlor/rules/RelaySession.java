package dev.zlor.rules;

/** One pair's transmission lifecycle, independent of Minecraft and chunk loading. */
public final class RelaySession {
    public static final int RELEASE_TICKS=20,LOAD_TIMEOUT=600,RETRY_TICKS=100;
    public record Step(int output,boolean ticket,boolean timedOut){}
    private final RelaySignal signal=new RelaySignal();
    private int power,loading,grace,retry;
    public Step tick(Integer input,boolean ready){
        if(input==null){signal.clear();power=0;loading=0;}
        if(retry>0){retry--;return new Step(0,false,false);}
        if(input!=null)signal.sample(input);
        if(signal.pending()||power>0){
            if(ready){power=signal.advance();loading=0;grace=RELEASE_TICKS;return new Step(power,true,false);}
            if(++loading>LOAD_TIMEOUT){signal.clear();power=0;grace=0;loading=0;retry=RETRY_TICKS;return new Step(0,false,true);}
            return new Step(0,true,false);
        }
        signal.clear();power=0;boolean keep=grace>0;if(keep)grace--;
        return new Step(0,keep,false);
    }
    public boolean idle(){return !signal.pending()&&power==0&&grace==0&&retry==0;}
}
