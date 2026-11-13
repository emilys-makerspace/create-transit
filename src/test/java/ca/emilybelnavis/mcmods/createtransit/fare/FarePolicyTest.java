package ca.emilybelnavis.mcmods.createtransit.fare;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FarePolicyTest {
    @Test
    void progressiveBoundariesAndThirteenZoneExampleRoundOnlyAtTheEnd() {
        int[] zones = {0, 1, 2, 5, 6, 10, 11, 13};
        long[] totals = {370, 370, 555, 1110, 1233, 1727, 1819, 2004};
        for (int i = 0; i < zones.length; i++) {
            var quote = FarePolicy.DEFAULT.quote(0, zones[i]);
            assertEquals(totals[i], quote.totalCadMinor(), "zone difference " + zones[i]);
            assertEquals(370, quote.entryCadMinor());
            assertEquals(totals[i] - 370, quote.exitCadMinor());
            assertEquals(quote, FarePolicy.DEFAULT.quote(zones[i], 0));
        }
    }

    @Test
    void longerJourneysNeverBecomeCheaper() {
        long previous = 0;
        for (int zone = 0; zone <= 1000; zone++) {
            long total = FarePolicy.DEFAULT.quote(0, zone).totalCadMinor();
            assertTrue(total >= previous, "decreasing fare at " + zone);
            previous = total;
        }
    }

    @Test
    void invalidZonesAndUnrepresentableAmountsFailInsteadOfWrapping() {
        assertThrows(IllegalArgumentException.class, () -> FarePolicy.DEFAULT.quote(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FarePolicy(0, 20000));
        assertThrows(ArithmeticException.class, () -> new FarePolicy(Long.MAX_VALUE, 20000).quote(0, 2));
        assertTrue(FarePolicy.DEFAULT.quote(0, Integer.MAX_VALUE).totalCadMinor() > 0);
    }
}
