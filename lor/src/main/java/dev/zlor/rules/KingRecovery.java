package dev.zlor.rules;

/** Active combat ticks; first recovery at 60s, then every 30s. Damage resets the full grace period. */
public final class KingRecovery {
    private int idle;
    public KingRecovery(int idle){if(idle<0||idle>1200)throw new IllegalArgumentException();this.idle=idle;}
    public void damaged(){idle=0;}
    public boolean tick(){if(++idle<1200)return false;idle=600;return true;}
    public int idle(){return idle;}
    public int remaining(){return 1200-idle;}
}
