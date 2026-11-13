# Economy 0.6.0-alpha integration

Source contracts: [integration guide](https://github.com/emilys-makerspace/mc-economy/blob/main/docs/INTEGRATION_API.md), [API reference](https://github.com/emilys-makerspace/mc-economy/blob/main/docs/API_REFERENCE.md), and [BankingIntegration](https://github.com/emilys-makerspace/mc-economy/blob/main/src/main/java/ca/emilybelnavis/mcmods/economy/api/BankingIntegration.java).

## Foundation implemented

`EconomyBridge` requires a live authenticated player, the server thread, Economy READY, and banking API 3. Fresh grants derive from the actual player and current financial world; no packet-supplied actor or grant is accepted.

Presented-card resolution checks exactly the clicked hand, asks Economy for current issued-token access, and rechecks the live player/session/hand/design on the server executor before returning. An item marker is only a reference, not authority. Future payment admission must revalidate the credential at commit; resolving a card does not by itself authorize a later debit.

The trusted low-level same-currency debit adapter always selects `Overdrafts.DebitKind.INTEGRATION_OVERRIDE`. Its immutable invoice includes the original operation/attempt IDs, source, destination, currency and amount. Callers must first durably save and validate that invoice and the presented debit token; this milestone exposes no payment packet or gameplay action. Economy still checks account ownership, open status and currency. A decline or exceptional completion is not payment success.

Original-outcome lookup reconstructs the same canonical request and never submits money movement. Economy restores its reserved owner field from the fresh grant. Retain the invoice across restart and reconnect; outcome absence is not permission to automatically submit a new invoice.

## Required next integration work

1. Durable transit operation storage, associating payment and item/journey effects, with reconciliation before replay. The low-level adapter does not implement cross-store atomicity or value issuance.
2. Recipient provisioning/configuration, matching the source currency. The v3 debit operation is a same-currency BANK transfer, not an FX purchase API.
3. Atomic debit-token validation at financial commit, not just a prior UI check. The public debit call itself does not accept a sealed card proof.
4. Currency-equivalent fare capture using Economy publications and its settlement rules. Exchange quotes can include service fees and expiry; they are not automatically an immutable journey-long transit fare quote.
5. Credit admission for transit hardware. `CreditDrawAdmission.capture` currently requires an Economy `BankingMenu`, REGISTER access for purchases, terminal policy and matching register price. `creditCheckout` is documented for the existing register COMMIT composer. Transit gates cannot manufacture a proof or call cash advance as a replacement for a fare purchase. Establish a supported hardware adapter or an upstream additive contract before enabling credit fares.

## Local artifact metadata discrepancy

The initial local `economy-0.6.0-alpha.jar` declares `version="0.4.0-alpha"` in its NeoForge descriptor, despite containing the v3 API. The import script explicitly corrects only this descriptor in the development copy, retaining the original class/resources and recording both original and development SHA-256 digests. Original upstream files are never modified. Extracted SQLite is included on the development runtime classpath.

Create Transit's release metadata still requires exactly `economy [0.6.0-alpha]`. A public release needs an official Economy artifact with matching metadata; the development correction is not a dependency override and is not bundled into Create Transit.

The neighboring project's original build artifact became unavailable during setup. Development therefore uses an isolated source build, with its source fingerprint recorded in `config/economy-source-snapshot.json` and a minimal build recipe in `config/economy-snapshot.gradle`. `tools/prepare-economy-snapshot.py` reproduces that input from matching sources without writing to the source project.

## Verification scope

The SQLite-backed integration test submits a real CAD 200 debit from a zero-balance account, demonstrates that NORMAL authority refuses it while INTEGRATION_OVERRIDE commits it, closes and reopens storage, then resolves/replays the original operation without another posting. This verifies Economy journal semantics and the adapter's canonical recovery request, not a connected player's gate interaction or cross-store transit-value delivery.

Fare tests cover tier boundaries, symmetry, monotonicity, exact final rounding and arithmetic bounds. The opt-in client startup probe verifies loaded mod versions, Registrate/SQLite availability and reaching the title screen. Credit payment, foreign-currency settlement and multiplayer gate acceptance remain unverified and unimplemented.
