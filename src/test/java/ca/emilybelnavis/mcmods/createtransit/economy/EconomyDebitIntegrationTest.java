package ca.emilybelnavis.mcmods.createtransit.economy;

import static org.junit.jupiter.api.Assertions.*;
import ca.emilybelnavis.mcmods.economy.api.*;
import ca.emilybelnavis.mcmods.economy.api.Identifiers.*;
import ca.emilybelnavis.mcmods.economy.fx.FxService;
import ca.emilybelnavis.mcmods.economy.journal.*;
import ca.emilybelnavis.mcmods.economy.overdraft.Overdrafts;
import ca.emilybelnavis.mcmods.economy.storage.SqliteStorage;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EconomyDebitIntegrationTest {
    @TempDir Path world;

    @Test
    void overrideCanChargeEvasionFareAndRecoverOriginalWithoutChargingAgain() throws Exception {
        var player = new PlayerId(UUID.randomUUID());
        var source = new AccountId(UUID.randomUUID());
        var recipient = new AccountId(UUID.randomUUID());
        var now = Instant.parse("2026-11-12T12:00:00Z");
        Journal.Intent paid;
        var store = new SqliteStorage();
        try {
            store.initialize(world.toRealPath());
            var owner = new PartyRef(store.snapshot().worldId(), new PartyRef.Player(player));
            var opening = new JournalEngine(store, (actor, kind) -> actor.equals(player),
                    Map.of(FxService.OPEN, FxService.opening(Clock.fixed(now, ZoneOffset.UTC))));
            assertEquals(Journal.Status.COMMITTED, opening.submit(intent(player, FxService.OPEN,
                    new Journal.CanonicalRequest(1, Map.of("currency", "CAD")))).status());
            store.transaction(tx -> {
                tx.execute("INSERT INTO financial_calendar(singleton,version,origin_ms,observed_wall_ms,last_effective_ms,completed_effects) VALUES(1,1,?,?,?,0)",
                        List.of("" + now.toEpochMilli(), "" + now.toEpochMilli(), "" + now.toEpochMilli()));
                tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','CAD','OPEN',1,1)",
                        List.of(source.value().toString(), player.value().toString()));
                tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','CAD','OPEN',1,1)",
                        List.of(recipient.value().toString(), UUID.randomUUID().toString()));
                return null;
            });
            var amount = new Money(EconomyCurrency.CAD, 20000);
            var ordinary = new JournalEngine(store, (actor, kind) -> actor.equals(player), Map.of(Overdrafts.DEBIT,
                    Overdrafts.debit(owner, source.value(), recipient.value(), amount, Overdrafts.DebitKind.NORMAL, now::toEpochMilli)));
            assertEquals("INSUFFICIENT_FUNDS", ordinary.submit(intent(player, Overdrafts.DEBIT,
                    Overdrafts.debitRequest(owner, source.value(), recipient.value(), amount, Overdrafts.DebitKind.NORMAL))).result().code());

            var invoice = new EconomyBridge.Debit(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()),
                    source, recipient, amount);
            var canonical = new TreeMap<>(EconomyBridge.debitLookupRequest(owner, invoice).fields());
            canonical.put("economy:owner", Journal.actorKey(player));
            paid = new Journal.Intent(invoice.operation(), invoice.attempt(), player, Overdrafts.DEBIT,
                    new Journal.CanonicalRequest(1, canonical), null, null);
            var engine = new JournalEngine(store, (actor, kind) -> actor.equals(player), Map.of(Overdrafts.DEBIT,
                    Overdrafts.debit(owner, source.value(), recipient.value(), amount,
                            Overdrafts.DebitKind.INTEGRATION_OVERRIDE, now::toEpochMilli)));
            assertEquals(Journal.Status.COMMITTED, engine.submit(paid).status());
            assertEquals(-20000, posted(store, source));
            assertEquals(20000, posted(store, recipient));
        } finally {
            store.close();
        }
        var reopened = new SqliteStorage();
        try {
            reopened.initialize(world.toRealPath());
            var recovery = new JournalEngine(reopened, (actor, kind) -> actor.equals(player),
                    Map.of(Overdrafts.DEBIT, new JournalEngine.Registration(1, (i, reads) -> {
                        throw new AssertionError("Original outcome recovery must not prepare another debit");
                    })));
            assertEquals(Journal.Status.COMMITTED, recovery.lookup(paid).orElseThrow().status());
            assertEquals(Journal.Status.COMMITTED, recovery.submit(paid).status());
            assertEquals(-20000, posted(reopened, source));
            assertEquals(20000, posted(reopened, recipient));
        } finally {
            reopened.close();
        }
    }

    private static Journal.Intent intent(PlayerId actor, Journal.Kind kind, Journal.CanonicalRequest request) {
        return new Journal.Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()),
                actor, kind, request, null, null);
    }

    private static long posted(SqliteStorage store, AccountId account) {
        return store.transaction(tx -> Settlement.posted(tx::query, account.value(), EconomyCurrency.CAD));
    }
}
