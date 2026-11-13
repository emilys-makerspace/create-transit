# Create Transit — agreed specification

## Platform

- Minecraft 1.21.1, Java 21, NeoForge 21.1.252.
- Required Create version: exactly 6.0.10.
- Required Economy version: exactly 0.6.0-alpha; banking API 3.
- Working Gradle runClient and runServer tasks, with this mod registered and all required runtime dependencies present.

## One shared fare system

The entire server save, including its dimensions, shares one transit fare system. Lines, stations, platforms and segments belong to that system. Card designs are cosmetic and never represent separate networks or alter acceptance or fares. Paid-area transfers retain the active journey and charge no additional base fare.

Bind transit stations/platforms to Create station identities, not names alone. Lines contain ordered stations and connecting segments. Gates/readers can specify their zone and line/segment context. All line zones use a shared numbering scheme. Endpoint zones determine fares; tracking detours is not in the initial scope.

## Cards and tickets

Transit cards hold a currency-denominated balance. Obtain and reload them at any transit kiosk. Present them at fare gates or standalone readers; they have no ordinary use-in-air action. Balance readers and kiosks can display their balance. No fare inspectors are included.

Nine cosmetic inspirations: OMNY, MetroCard, PRESTO, OPUS, Clipper, Navigo, Octopus, Oyster and Opal. A purchased card receives one uniformly random design at issuance (1/9 chance each); persist that selection through reloads and use.

Tickets contain 1, 2, 5, 7 or 10 trips. A successful tap-on consumes one trip, covering the complete zoned journey. Exit consumes no additional trip and charges no surcharge. A ticket with zero remaining trips can complete the journey that consumed its last trip.

Items reference unique server-persisted credentials. Item duplication does not duplicate money or trip allowance. Persist balances, trips and journey state across normal restarts; losing a machine does not lose financial records.

## Progressive fares

Base fare B = CAD 3.70. Zone difference d = abs(start_zone - end_zone).

```
total = B
      + B/2 * min(max(d-1, 0), 4)
      + B/3 * min(max(d-5, 0), 5)
      + B/4 * max(d-10, 0)
```

Differences 0 and 1 incur only the base fare. Charge the base at entry and the additional amount at exit. Calculate precisely and round once to the settlement currency's minor unit. A difference of 13 costs CAD 20.04: CAD 3.70 on entry and CAD 16.34 on exit.

Use authoritative Economy FX for currency equivalents. Capture fare policy and conversion terms for each journey rather than repricing it mid-trip. Define the durable FX capture/settlement contract before enabling foreign-currency payments.

An exit tap without a corresponding tap-on incurs CAD 200.00 or its currency equivalent. Prevent duplicate fines for repeated interactions with the same incident. Immediate replay of a completed exit must return its original outcome rather than create an evasion charge.

## Gates and readers

- Gates: entry, exit or both.
- Standalone readers: tap-on, tap-off or combined.
- Separate balance reader.
- Configure station/zone and applicable line/segment context.
- Accept actual presented transit cards, transit tickets, Economy bank/debit cards and credit cards.
- A player right-clicks with the payment item in the clicked hand; never silently select a different inventory card or funding account.
- Gates configured only by their placing player, using sneak-right-click with an empty hand to open the GUI.
- Persist the placing player's UUID; enforce ownership on the server both when opening and when saving the GUI. No operator exception was requested.
- Breaking and replacing a gate assigns ownership to the new placer.
- Successful payment authorizes one directional passage for the tapping player.

The Economy integration override is permitted to exceed bank overdraft limits. Select INTEGRATION_OVERRIDE in trusted server code, never from packet values. This permission does not bypass token, ownership, account, currency or other Economy checks. Do not assume it also authorizes overriding contractual credit limits.

## Transit kiosk / dispenser

One machine, 2 wide × 2 high × 1 deep, providing:

1. Issue a randomly designed transit card.
2. Reload a transit card.
3. Purchase a ticket.
4. Check transit card balance.
5. Check remaining ticket trips.

Use one controller and four coordinated block positions, check placement space, and drop the machine exactly once on removal. Show purchase/reload amount, currency and funding source before confirmation. Track payment separately from delivery so full inventories, disconnects or interruption cannot duplicate charges or lose purchases.

## Still configurable / undecided

- Card issuance fee and ticket pack prices.
- Transit revenue recipient and provisioning of same-currency recipient accounts.
- Unpaid exit policy and settlement of insufficient transit-card balances.
- Journey expiry/abandoned-entry policy; no automatic fine or expiry is implied.
- Whether physical cash is accepted at kiosks (not requested).

## Implementation sequence

1. Runnable development foundation, pinned dependencies, Economy contract investigation and initial adapter.
2. Durable credentials, shared system configuration and journey state; atomic/recoverable payment and transit-value settlement.
3. Kiosk issuance, reloads, ticket purchases and checks.
4. Standalone readers, balance readers, owner-configured directional gates and evasion handling.
5. Create station/platform, line and segment bindings; transfers.
6. Assets, recipes, localization and multiplayer/restart validation.

The foundation is not a playable transit release. Complete the Economy credit and FX integration contracts before advertising those payment paths as functional.
