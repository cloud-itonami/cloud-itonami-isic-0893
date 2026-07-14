# cloud-itonami-isic-0893

Open Business Blueprint for **ISIC Rev.4 0893**: Extraction of salt — an
ISIC Wave 3 (production/mining) operations-coordination actor per
ADR-2607121000. Back-office and coordination workflow for salt-
extraction sites, modeled closely on `cloud-itonami-isic-0520`'s
(Mining of lignite) governed-actor discipline.

**Maturity: `:implemented`** — SaltOpsAdvisor ⊣ SaltExtractionGovernor
as a langgraph-clj StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt). All source `.cljc` (portable
to JVM / ClojureScript / GraalVM), no JVM-only interop.

## Scope: both ISIC-0893 extraction methods

ISIC 0893 covers more than one extraction method, and this actor
coordinates the back office of either kind of site:

- **Rock-salt (dry/underground) mining** — conventional room-and-pillar
  excavation of a salt deposit.
- **Solution mining / evaporation** — brine extraction via injection/
  extraction wells, pumped to solar-evaporation ponds or vacuum-pan
  evaporators.

The actor is deliberately method-agnostic at the coordination layer
(production logging, maintenance scheduling, safety-concern flagging,
shipment coordination apply to either method); its governor's scope
exclusions cover both methods' equipment-control territory explicitly
(see below).

## CRITICAL: Scope Exclusions

This actor **DOES NOT** and **NEVER WILL**:

- **Direct extraction-equipment control** — continuous-miner operation,
  drill-and-blast sequencing, room-and-pillar excavation sequencing
  (rock-salt mining) — or brine injection/extraction well-pump control,
  cavern-pressure control, evaporation-pond gate/valve control,
  harvester operation (solution mining / evaporation)
- **Site-safety decisions** — subsidence-control determinations, brine-
  containment-system overrides, cavern-integrity control decisions
- **Site-safety-authority decisions** — permit issuance, license
  suspension, or compliance enforcement

This actor **only** coordinates back-office operations: production-
record logging (output/tonnage/purity), maintenance scheduling, safety-
concern flagging (subsidence for rock-salt sites, brine-containment for
solution-mining sites — always routed to a human), and outbound salt-
shipment coordination. Every proposal the advisor drafts carries
`:effect :propose` — never a direct actuation — and
`saltops.governor` independently re-scans every proposal's content for
the excluded scope areas above, regardless of op or confidence.

## Operations

Closed proposal-op allowlist (`saltops.governor/allowed-ops`), all
`:effect :propose`:

- `:log-production-record` — output/tonnage/purity data logging
- `:schedule-maintenance` — equipment/pond maintenance scheduling proposal
- `:flag-safety-concern` — surface a site-safety concern (subsidence for
  rock-salt sites, brine-containment for solution-mining sites) —
  **ALWAYS escalates**
- `:coordinate-shipment` — outbound salt shipment coordination

**HARD invariants** (always `:hold`, never human-overridable):

1. **Site unverified** — the target site record must exist AND be
   independently `:registered?`/`:verified?` in the store before any
   proposal for it may commit or even escalate.
2. **Effect not `:propose`** — any proposal whose `:effect` is not
   `:propose` is, by construction, a claim to directly actuate outside
   governance.
3. **Scope exclusion** — any proposal (regardless of op) outside the
   closed allowlist, or whose rationale/summary/citations/value touches
   extraction-equipment-control (either method family) or site-safety
   (-authority) territory, is a permanent, un-overridable block.
   Evaluated unconditionally on every proposal.

**ESCALATE** (always human sign-off, when the governor is otherwise clean):

- `:flag-safety-concern` — always, regardless of confidence.
- Low advisor confidence (`< 0.6`).

## Rollout phases (`saltops.phase`)

Phase 0 (read-only) → 1 (production logging, approval-gated) → 2 (adds
maintenance + shipment coordination, approval-gated) → 3 (supervised
auto: production-record/maintenance/shipment may auto-commit when
governor-clean and confident). `:flag-safety-concern` is deliberately
absent from every phase's `:auto` set — a permanent structural fact,
not a rollout milestone still to come — matching `saltops.governor`'s
own `always-escalate-ops` independently.

## Development

```bash
clojure -M:test   # run the full suite
clojure -M:run    # walk the demo scenarios (saltops.sim)
clojure -M:lint    # clj-kondo
```

AGPL-3.0-or-later, forkable by any qualified operator. Part of cloud-itonami.
