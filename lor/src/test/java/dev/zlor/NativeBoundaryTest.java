package dev.zlor;

import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBoundaryTest {
    public static class TickList {
        final List<Object> entities=new ArrayList<>();
        public void add(Object entity){entities.add(entity);}
        public void remove(Object entity){entities.remove(entity);}
        public boolean contains(Object entity){return entities.contains(entity);}
        public void forEach(Consumer<Object> consumer){List.copyOf(entities).forEach(consumer);}
    }
    public static class FinalIteration {
        public final void forEach(Consumer<Object> consumer){}
    }
    @Test void wrapperPreservesOriginalListAndBracketsEachExistingNativeTick()throws Exception{
        var original=new TickList();original.add("first");var trace=new ArrayList<String>();
        var wrapper=(TickList)NativeBoundary.wrap(original,consumer->entity->{trace.add("before:"+entity);try{consumer.accept(entity);}finally{trace.add("after:"+entity);}});
        wrapper.add("second");assertTrue(original.contains("second"));assertTrue(wrapper.contains("first"));
        wrapper.forEach(e->trace.add("tick:"+e));assertEquals(List.of("before:first","tick:first","after:first","before:second","tick:second","after:second"),trace);
        wrapper.remove("first");assertFalse(original.contains("first"));
    }
    @Test void finallyRestoresWorldContextEvenWhenNativeTickThrows()throws Exception{
        var original=new TickList();original.add("bad");boolean[] restored={false};
        var wrapper=(TickList)NativeBoundary.wrap(original,consumer->entity->{try{consumer.accept(entity);}finally{restored[0]=true;}});
        assertThrows(IllegalStateException.class,()->wrapper.forEach(e->{throw new IllegalStateException("native error");}));assertTrue(restored[0]);
    }
    @Test void finalIterationCannotInstallAnEmptyIndependentList(){assertThrows(Exception.class,()->NativeBoundary.wrap(new FinalIteration(),consumer->consumer));}
}
