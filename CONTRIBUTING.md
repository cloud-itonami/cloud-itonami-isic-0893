# Contributing to cloud-itonami-isic-0893

Contributions should preserve the actor's scope: back-office coordination only,
with CRITICAL exclusions of direct extraction-equipment control, site safety, and
regulatory authority decisions (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: clojure -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Direct extraction-equipment control — continuous-miner operation,
  drill-and-blast sequencing, room-and-pillar excavation sequencing
  (rock-salt mining), or brine injection/extraction well-pump control,
  cavern-pressure control, evaporation-pond gate/valve control (solution
  mining / evaporation).
- Site-safety decisions (subsidence-control determinations, brine-
  containment-system overrides, cavern-integrity control decisions).
- Site-safety-authority decisions (permits, licenses, compliance enforcement).

Contributions that cross these boundaries will be rejected.
