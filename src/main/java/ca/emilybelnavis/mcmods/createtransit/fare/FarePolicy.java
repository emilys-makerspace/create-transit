package ca.emilybelnavis.mcmods.createtransit.fare;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** One world-wide progressive fare policy. Amounts are integer CAD cents. */
public record FarePolicy(long baseCadMinor, long evasionCadMinor) {
    public static final FarePolicy DEFAULT = new FarePolicy(370, 20_000);
    private static final BigInteger TWELVE = BigInteger.valueOf(12);

    public FarePolicy {
        if (baseCadMinor <= 0 || evasionCadMinor <= 0) {
            throw new IllegalArgumentException("Fares must be positive");
        }
    }

    public Quote quote(int startZone, int endZone) {
        if (startZone < 0 || endZone < 0) throw new IllegalArgumentException("Zones must be nonnegative");
        long distance = Math.abs((long) startZone - endZone);
        // Work in twelfths of a cent so thirds and quarters remain exact until final settlement.
        long weight = Math.addExact(12,
                Math.addExact(6 * Math.min(Math.max(distance - 1, 0), 4),
                        Math.addExact(4 * Math.min(Math.max(distance - 5, 0), 5),
                                3 * Math.max(distance - 10, 0))));
        var numerator = BigInteger.valueOf(baseCadMinor).multiply(BigInteger.valueOf(weight));
        long total = new BigDecimal(numerator).divide(new BigDecimal(TWELVE), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Quote(distance, baseCadMinor, total - baseCadMinor, total);
    }

    public record Quote(long zoneDifference, long entryCadMinor, long exitCadMinor, long totalCadMinor) {
        public BigDecimal totalCad() {
            return BigDecimal.valueOf(totalCadMinor, 2);
        }
    }
}
