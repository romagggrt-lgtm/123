package dev.zlor.reference;
import dev.zlor.rules.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DomeAnimationTest {
  @Test
  void openingRunsFromTopToBottom() {
    assertEquals(0, DomeAnimation.openingDelay(1));
    assertEquals(1_500, DomeAnimation.openingDelay(0));
    assertEquals(3_000, DomeAnimation.openingDelay(-1));
    assertTrue(DomeAnimation.visible(1, 1, 10, 100, 100_000, false));
    assertFalse(DomeAnimation.visible(1, -1, 10, 100, 100_000, false));
  }

  @Test
  void cellOrderIsStableAndRefillingRestoresEveryCell() {
    int vanished = 0;
    for (int i = 0; i < 642; i++) {
      long threshold = DomeAnimation.disappearanceAt(i, 37);
      assertEquals(threshold, DomeAnimation.disappearanceAt(i, 37));
      assertTrue(threshold >= 0 && threshold < 30_000);
      if (!DomeAnimation.visible(i, 0, 37, 4_000, 15_000, true)) vanished++;
      assertTrue(DomeAnimation.visible(i, 0, 37, 4_000, 15_000, false));
    }
    assertTrue(vanished > 200 && vanished < 440);
    assertEquals(22, DomeAnimation.surfaceAlpha(60_000, true));
    assertEquals(11, DomeAnimation.surfaceAlpha(15_000, true));
    assertEquals(0, DomeAnimation.surfaceAlpha(0, true));
  }
}
