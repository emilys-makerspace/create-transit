package ca.emilybelnavis.mcmods.createtransit.economy;

import static org.junit.jupiter.api.Assertions.*;
import ca.emilybelnavis.mcmods.economy.api.EconomyCurrency;
import ca.emilybelnavis.mcmods.economy.api.Identifiers.*;
import ca.emilybelnavis.mcmods.economy.api.Money;
import ca.emilybelnavis.mcmods.economy.api.PartyRef;
import ca.emilybelnavis.mcmods.economy.overdraft.Overdrafts;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyBridgeTest {
    @Test
    void recoveryReconstructsTheExactOriginalOverrideRequest() {
        var owner = new PartyRef(new WorldId(UUID.randomUUID()), new PartyRef.Player(new PlayerId(UUID.randomUUID())));
        var invoice = new EconomyBridge.Debit(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()),
                new AccountId(UUID.randomUUID()), new AccountId(UUID.randomUUID()), new Money(EconomyCurrency.CAD, 20000));
        var original = Overdrafts.debitRequest(owner, invoice.source().value(), invoice.destination().value(),
                invoice.amount(), Overdrafts.DebitKind.INTEGRATION_OVERRIDE);
        var reconstructed = new TreeMap<>(EconomyBridge.debitLookupRequest(owner, invoice).fields());
        assertFalse(reconstructed.containsKey("economy:owner"));
        reconstructed.put("economy:owner", original.fields().get("economy:owner"));
        assertEquals(original.fields(), reconstructed);
        assertNotEquals(Overdrafts.debitRequest(owner, invoice.source().value(), invoice.destination().value(),
                invoice.amount(), Overdrafts.DebitKind.NORMAL).fields(), reconstructed);
    }
}
