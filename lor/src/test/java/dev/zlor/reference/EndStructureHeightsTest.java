package dev.zlor.reference;
import dev.zlor.rules.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndStructureHeightsTest {
    @Test void endVoidNineteenAllowsPortalTwentyAndPlacesEntryThirty() {
        assertEquals(20, EndStructureHeights.minimumPortalY(19, 0));
        assertEquals(30, EndStructureHeights.entryY(20));
        assertEquals(238, EndStructureHeights.maximumPortalY(256));
    }

    @Test void aFractionalBoundaryAndWorldEdgesRemainOutsideTheStructures() {
        assertEquals(21, EndStructureHeights.minimumPortalY(20.01, 0));
        assertEquals(2, EndStructureHeights.minimumPortalY(-10, 0));
        assertEquals(248, EndStructureHeights.entryY(EndStructureHeights.maximumPortalY(256)));
    }
}
