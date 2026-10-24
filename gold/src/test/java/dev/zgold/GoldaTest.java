package dev.zgold;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoldaTest {
    private Golda gold() { return new Golda(UUID.randomUUID(), UUID.randomUUID(), .5, 65, .5, 0, 2.3, .055, 50, 90, 5, 3); }
    @Test void riseIsMonotonicAndMeetsHoverWithoutJump() {
        Golda g = gold(); double previous = g.centerY();
        assertEquals(65.32, previous, 1e-8);
        for (int i = 1; i <= 50; i++) {
            g.age = i; assertTrue(g.centerY() >= previous); assertTrue(g.centerY() - previous < .061); previous = g.centerY();
        }
        assertEquals(67.3, previous, 1e-8); g.age++; assertTrue(g.centerY() - previous < .001);
    }
    @Test void hoverStaysInNarrowBand() {
        Golda g = gold();
        for (int i = 50; i < 1000; i++) { g.age = i; assertTrue(g.centerY() >= 67.3 && g.centerY() <= 67.355 + 1e-8); }
    }
    @Test void onlyThreeClaimsAndPersonalSingleUseAnswer() {
        Golda g = gold(); g.age = 50; UUID p = UUID.randomUUID();
        for (int i = 0; i < 3; i++) { assertTrue(g.canClick(i * 6, 6)); g.claim(p, i * 6); assertFalse(g.canClick(i * 6, 6)); }
        assertEquals(15, g.clicks * g.batch); assertTrue(g.waiting()); assertFalse(g.canClick(1000, 6));
        assertTrue(g.accepts(p, g.token)); assertFalse(g.accepts(UUID.randomUUID(), g.token)); assertFalse(g.accepts(p, UUID.randomUUID()));
        g.removing = true; assertFalse(g.accepts(p, g.token)); assertThrows(IllegalStateException.class, () -> g.claim(p, 1000));
    }
    @Test void cannotRewardDuringAppearance() {
        Golda g = gold(); assertFalse(g.canClick(100, 6)); assertThrows(IllegalStateException.class, () -> g.claim(UUID.randomUUID(), 100));
    }
}
