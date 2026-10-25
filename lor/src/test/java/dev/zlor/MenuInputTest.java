package dev.zlor;

import io.papermc.paper.dialog.DialogResponseView;
import java.util.*;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuInputTest {
    record Response(Map<String,Object> data) implements DialogResponseView {
        public BinaryTagHolder payload(){return null;}
        public String getText(String key){Object value=data.get(key);return value==null?null:String.valueOf(value);}
        public Boolean getBoolean(String key){return (Boolean)data.get(key);}
        public Float getFloat(String key){return (Float)data.get(key);}
    }
    @Test void volumeIsARealBoundedPercentageRange(){assertEquals(0,Menus.slider(new Response(Map.of("volume",0f)),"volume",0,100,1));assertEquals(100,Menus.slider(new Response(Map.of("volume",100f)),"volume",0,100,1));assertEquals(50,Menus.slider(new Response(Map.of("volume",50f)),"volume",0,100,1));}
    @Test void missingAndNonFiniteSliderPayloadsAreRejected(){for(Float value:List.of(Float.NaN,Float.POSITIVE_INFINITY,-1f,101f,.5f))assertThrows(IllegalArgumentException.class,()->Menus.slider(new Response(Map.of("volume",value)),"volume",0,100,1));assertThrows(IllegalArgumentException.class,()->Menus.slider(new Response(Map.of()),"volume",0,100,1));}
    @Test void sirenRangeCannotUseOutOfStepOrOversizedClientValues(){assertEquals(32,Menus.slider(new Response(Map.of("range",32f)),"range",8,128,8));for(float invalid:new float[]{0,7,9,129,2000})assertThrows(IllegalArgumentException.class,()->Menus.slider(new Response(Map.of("range",invalid)),"range",8,128,8));}
    @Test void sirenTrackIsValidatedBeforePreviewStarts(){for(int index=0;index<8;index++)assertEquals(index,Menus.track(new Response(Map.of("track",String.valueOf(index)))));for(String bad:List.of("-1","8","999","NaN","","6.0"))assertThrows(IllegalArgumentException.class,()->Menus.track(new Response(Map.of("track",bad))));assertThrows(IllegalArgumentException.class,()->Menus.track(new Response(Map.of())));}
    @Test void rootNavigationHasAllFivePermanentLabels(){assertEquals(List.of("Войд","Блокировки","Текущее состояние","Драконы","Показать всё"),Menus.ROOT_LABELS);}
}
