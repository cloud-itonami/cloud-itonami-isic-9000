# ADR-0001: cloud-itonami-isic-9000 -- EntertainmentOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602` ADR-0001s
  (the pattern this ADR ports); ADR-2607071250/ADR-2607071320/
  ADR-2607071351/ADR-2607071618/ADR-2607071640/ADR-2607071654/
  ADR-2607071717/ADR-2607071732/ADR-2607071752/ADR-2607071819/
  ADR-2607071849/ADR-2607071922/ADR-2607072715/ADR-2607072730/
  ADR-2607072745/ADR-2607072800 (`6612`/`6492`/`6920`/`6611`/`7120`/
  `8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/
  `9103`/`9602`, the sixteen verticals built outside ADR-2607032000's
  original insurance/real-estate batch -- this is the seventeenth)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `9602`, this ADR deepens `cloud-itonami-
  isic-9000` (creative, arts and entertainment activities) from
  `:blueprint` to `:implemented`, the twenty-fifth actor in this fleet
  -- the FIRST creative/arts/entertainment vertical (ISIC division
  90).

## Problem

A theatre/production company's production-release workflow bundles
several distinct concerns under one governed workflow:

1. **Jurisdiction copyright/rights-clearance correctness** -- an
   official spec-basis citation from a real regulator (文化庁/the
   U.S. Copyright Office/the UK Intellectual Property Office/GEMA),
   never fabricated.
2. **Release-channel restriction conflict** -- does a production's
   own proposed release channel appear on its own recorded
   restricted-channel list (e.g. an exclusivity-window clause
   forbidding day-and-date streaming alongside a theatrical release)?
   Reuses the set-membership/conflict discipline `clinic.registry/
   treatment-contraindicated?` originally established (reused
   verbatim once already by `veterinary.registry`) for a THIRD domain.
3. **Rights-clearance resolution verification** -- has a production's
   own rights-clearance flag actually been resolved before release?
   The entertainment-specific reuse of the unconditional-evaluation
   screening discipline this fleet's `casualty.governor/sanctions-
   violations` originally established -- a FOURTEENTH distinct
   grounding.
4. **Real, high-stakes actuation, once** -- releasing or publishing a
   real production to the public is a single actuation event with
   direct reputational/legal stakes.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a theatre/production company with an
LLM" but "seal the LLM inside a trust boundary and layer evidence-
sufficiency, release-channel-restriction verification, rights-
clearance-resolution verification, audit and human-approval on top of
it, while structurally fixing the one real actuation event as
human-only."

## Decision

### 1. EntertainmentOps-LLM is sealed into the bottom node; it never releases a production directly

`entertainment.entertainmentopsllm` returns exactly four kinds of
proposal: intake normalization, jurisdiction copyright/rights-
clearance checklist, rights-clearance screening, and production-
release draft. No proposal writes the SSoT or commits a real public
release directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 entertainment operation

`entertainment.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `release-channel-restricted?` is the THIRD instance of the set-membership/conflict family

`clinic.registry/treatment-contraindicated?` established this fleet's
FIRST set-membership/conflict check (a proposed treatment must not
appear in a patient's own contraindication set); `veterinary.registry/
treatment-contraindicated?` reused it verbatim for the identical
clinical-safety concept. `release-channel-restricted?` is the THIRD
instance, applied to a genuinely different real-world concern: a
production's own proposed release channel must not appear in its own
recorded restricted-channel set (a contractual exclusivity/windowing
conflict, not a clinical-safety conflict).

### 4. Rights-clearance-unresolved screening reuses the unconditional-evaluation discipline for a fourteenth distinct grounding

`rights-clearance-unresolved-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for `:rights/screen` AND `:production/release` -- the
FOURTEENTH distinct application of this exact discipline in this
fleet.

### 5. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson already recorded by `parksafety`/`eldercare`/`museum`/`conservation`/`salon`

`rights-clearance-unresolved-is-held-and-unoverridable` calls `:rights/
screen` directly against `production-4` (an unresolved rights-
clearance flag), NOT `:production/release` against an un-screened
production -- because a failing screen is itself a HARD hold whose
payload never persists to the store, so the actuation op alone could
never discover the bad ground-truth flag through this check family
without the screening op having actually been run first. This build
applied that lesson PROACTIVELY for a fifth consecutive vertical
(after `eldercare`, `museum`, `conservation` and `salon`), further
reinforcing that lessons recorded in this fleet's ADRs transfer
forward reliably.

### 6. Single actuation, matching `6511`/`6621`/`6629`/`6612`/`6492`/`7120`/`8620`/`7500`/`9603`/`9321`/`9602`'s shape

`entertainment.governor`'s `high-stakes` set has exactly one member
(`:actuation/release-production`) -- this domain has ONE distinct
real-world, reputationally-critical act (releasing a production to the
public), not several independently-gated acts, matching the blueprint's
own stated scope.

### 7. Double-release guard checks a dedicated boolean, not `:status`

`already-released-violations` checks `:released?`, a dedicated boolean
set once and never cleared, rather than a `:status` value that could
legitimately advance past a checked state (the exact trap `cloud-
itonami-isic-6492`'s ADR-0001 documents in detail, explicitly avoided
BY DESIGN in every sibling actor's equivalent guard since). This
actor's `:status` never needs to encode "has this actuation already
happened" at all -- a deliberate architectural choice applied here for
a fifteenth consecutive time.

### 8. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
`8730`/`9102`/`9103`/`9602`, and unlike most other actors in this
fleet, this vertical's production records are practice-specific rather
than a shared cross-operator data contract -- `entertainment.*` runs
on the generic identity/forms/dmn/bpmn/audit-ledger stack only, per
the blueprint's own explicit statement.

## Consequences

- (+) Creative/arts/entertainment gets the same governed, auditable-
  actor treatment as the twenty-four prior actors, extending the
  pattern to a genuinely different economic sector (creative/arts/
  entertainment, ISIC division 90) for the first time.
- (+) `release-channel-restricted?` is a genuine structural
  contribution: the third instance of the set-membership/conflict
  family, extending it from clinical-safety concerns to contractual
  release-channel conflicts.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/entertainment/phase_test.clj`'s
  `production-release-never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/
  entertainment/store_contract_test.clj`, the same `:db-api`-driven
  swap pattern every sibling actor uses.
- (+) The rights-clearance-unresolved test/demo again correctly
  applied the established SCREENING-op-directly pattern for a fifth
  consecutive vertical after `eldercare`, `museum`, `conservation` and
  `salon` -- further evidence that lessons recorded in this fleet's
  ADRs continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `entertainment.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) `release-channel-restricted?` models only a single restricted-
  channel-membership check, not a full rights-management/clearance
  database (territory-by-territory licensing carve-outs, windowing-
  schedule engine, royalty/collections accounting are out of scope --
  see that fn's own docstring); real production-management-system
  integration and ongoing rehearsal/scheduling workflows are all out
  of scope for this OSS actor -- each operator's responsibility (see
  README's coverage table).
- 30 tests / 127 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All sixteen of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`; mixing a different ISIC division (90, distinct from all of those sixteen's divisions) into any would blur scope boundaries |
| Keep `cloud-itonami-isic-9000` at `:blueprint` only | ❌ | The standing direction continues past `9602`; creative/arts/entertainment is a natural, well-precedented next domain, further diversifying this fleet into a sector not yet touched |
| Model `release-channel-restricted?` as a NEW check family rather than a third instance of the set-membership/conflict shape `clinic`/`veterinary` established | ❌ | The actual comparison is identical in shape (is a proposed value a member of a forbidden set?); honestly framing this as a third reuse, not a new family, keeps the fleet's check-family taxonomy accurate |
| Test `rights-clearance-unresolved-violations` via the actuation op against an un-screened production (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s ADR-2607071922 Decision 5 and reconfirmed by `eldercare`'s, `museum`'s, `conservation`'s and `salon`'s ADR-0001s -- a failing screen never persists its payload to the store, so the actuation op alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/entertainment`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning established by every "no bespoke capability lib" sibling's ADR |
