package dev.zlor.rules;

import java.util.ArrayDeque;

/** Tick-duration FIFO: chunk loading delays a whole pulse, never swallows its end. */
public final class RelaySignal {
    private static final class Segment {final int power;int ticks;Segment(int power){this.power=power;ticks=1;}}
    private final ArrayDeque<Segment> queue=new ArrayDeque<>();
    private int poweredTicks;
    public void sample(int power){
        if(power<0||power>15)throw new IllegalArgumentException("Сила 0–15");
        if(power==0&&queue.isEmpty())return;
        if(!queue.isEmpty()&&queue.getLast().power==power)queue.getLast().ticks++;
        else queue.add(new Segment(power));
        if(power>0)poweredTicks++;
    }
    public boolean pending(){return poweredTicks>0;}
    public int advance(){
        if(queue.isEmpty())return 0;var first=queue.getFirst();int value=first.power;if(value>0)poweredTicks--;
        if(--first.ticks==0)queue.removeFirst();return value;
    }
    public void clear(){queue.clear();poweredTicks=0;}
}
