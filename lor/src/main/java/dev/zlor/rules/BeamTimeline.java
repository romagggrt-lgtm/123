package dev.zlor.rules;

/** One clock for warning, damaging fire and harmless visual fade. */
public record BeamTimeline(int warning,int active,int fade) {
    public BeamTimeline {if(warning<1||active<1||fade<1)throw new IllegalArgumentException("Некорректные фазы луча");}
    public boolean firing(long age){return age>=warning&&age<warning+(long)active;}
    public boolean pulse(long age){return firing(age)&&(age-warning)%20==0;}
    public boolean finished(long age){return age>=warning+(long)active+fade;}
    public double opacity(long age){return age<warning?0:age<warning+(long)active?1:1-BeamFlow.smooth((age-warning-active)/(double)fade);}
}
