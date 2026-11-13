package ca.emilybelnavis.mcmods.createtransit.card;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Cosmetic designs only. No design changes fares, acceptance, or the world-wide system. */
public enum CardDesign {
    OMNY, METROCARD, PRESTO, OPUS, CLIPPER, NAVIGO, OCTOPUS, OYSTER, OPAL;

    private static final CardDesign[] DESIGNS = values();

    /** Call once at issuance and persist the selected design with the credential. */
    public static CardDesign choose(RandomGenerator random) {
        return DESIGNS[Objects.requireNonNull(random).nextInt(DESIGNS.length)];
    }
}
