# cloud-itonami-isic-9000

Open Business Blueprint for **ISIC Rev.5 9000**: Creative, arts and
entertainment activities. This repository publishes a creative/arts/
entertainment actor -- production intake, jurisdiction assessment,
rights-clearance screening and production release -- as an OSS
business that any qualified theatre/production company can fork,
deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602)) --
the first creative/arts/entertainment vertical (ISIC division 90) in
this fleet. Here it is **EntertainmentOps-LLM ⊣ Content and Booking
Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> production-intake summary, normalizing records, and checking whether
> a proposed release channel actually appears on a production's own
> recorded restricted-channel list -- but it has **no notion of which
> jurisdiction's copyright/rights-clearance requirements are official,
> no license to release or publish a real production to the public,
> and no way to know on its own whether a production's own rights-
> clearance flag is still unresolved**. Letting it release a
> production directly invites fabricated jurisdiction citations, a
> release on a contractually-restricted channel, and an unresolved
> rights conflict being quietly signed off -- and liability, and
> reputational risk, for whoever runs it. This project seals the
> EntertainmentOps-LLM into a single node and wraps it with an
> independent **Content and Booking Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers production intake through jurisdiction assessment,
rights-clearance screening and production release. It does **not**,
by itself, hold any license required to operate a theatre/production
company in a given jurisdiction, and it does not claim to. It also
does **not** model a full rights-management/clearance database -- no
territory-by-territory licensing carve-outs, no windowing-schedule
engine, no royalty/collections accounting (see `entertainment.
registry/release-channel-restricted?`'s own docstring for the honest
simplification this makes: a single restricted-channel-membership
check, not a full rights-management system). Whoever deploys and
operates a live instance (a theatre/production company) supplies any
jurisdiction-specific license, the real rights-management/licensing
expertise and the real production-management-system integrations, and
bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that operator does
not have to build the compliance layer from scratch for every new
market.

### Actuation

**Releasing or publishing a real production to the public is never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`entertainment.governor`'s `:actuation/release-
production` high-stakes gate and `entertainment.phase`'s phase table,
which never puts `:production/release` in any phase's `:auto` set) --
see `entertainment.phase`'s docstring and `test/entertainment/
phase_test.clj`'s `production-release-never-auto-at-any-phase`. The
actor may draft, check and recommend; a human rights-holder/producer
is always the one who actually releases a production. Like `6511`/
`6621`/`6629`/`6612`/`6492`/`7120`/`8620`/`7500`/`9603`/`9321`/`9602`,
this actor has ONE actuation event.

## The core contract

```
production intake + jurisdiction facts (entertainment.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Entertainment│ ─────────────▶ │ Content and                 │  (independent system)
   │ Ops-LLM      │  + citations    │ Booking Governor: spec-     │
   │ (sealed)     │                 │ basis · evidence-incomplete ·│
   └──────────────┘         commit ◀────┼──────────▶ hold │ release-channel-
                                 │             │           │ restricted (set-
                           record + ledger  escalate ─▶ human   membership) ·
                                             (ALWAYS for         rights-clearance-unresolved
                                              :production/           (unconditional) ·
                                              release)                already-released
```

**The EntertainmentOps-LLM never releases a production the Content
and Booking Governor would reject, and never does so without a human
sign-off.** Hard violations (fabricated jurisdiction requirements;
unsupported rights-clearance evidence; a release channel that appears
on the production's own restricted-channel list; an unresolved rights-
clearance flag; a double release) force **hold** and *cannot* be
approved past; a clean release proposal still always routes to a
human.

## Run

```bash
clojure -M:dev:run     # walk one clean lifecycle (production release) + four HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a stage/rigging robot
assists physical set and equipment handling, under the actor, gated by
the independent **Content and Booking Governor**. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Content and Booking Governor, production-release draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9000`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/
`9321`/`8730`/`9102`/`9103`/`9602`, this vertical's production records
are practice-specific rather than a shared cross-operator data
contract, so `entertainment.*` runs on the generic identity/forms/dmn/
bpmn/audit-ledger stack only -- no bespoke domain capability lib to
reference at all.

## Layout

| File | Role |
|---|---|
| `src/entertainment/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + production-release history. No dynamically-filed sub-record -- the actuation op acts directly on a pre-seeded production, and the double-release guard checks a dedicated `:released?` boolean rather than a `:status` value |
| `src/entertainment/registry.cljc` | Production-release draft records, plus `release-channel-restricted?` -- reuses `clinic.registry/treatment-contraindicated?`'s set-membership/conflict shape (previously reused verbatim once by `veterinary.registry`) for a THIRD domain |
| `src/entertainment/facts.cljc` | Per-jurisdiction copyright/rights-clearance catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/entertainment/entertainmentopsllm.cljc` | **EntertainmentOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/rights-screening/production-release proposals |
| `src/entertainment/governor.cljc` | **Content and Booking Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · release-channel-restricted, pure ground-truth set-membership recompute · rights-clearance-unresolved, unconditional evaluation, the FOURTEENTH grounding of this discipline) + already-released guard + 1 soft (confidence/actuation gate) |
| `src/entertainment/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (release always human; production intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/entertainment/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/entertainment/sim.cljc` | demo driver |
| `test/entertainment/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers production intake through jurisdiction assessment,
rights-clearance screening and production release -- the core
governed lifecycle this blueprint's own `docs/business-model.md` names
as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Production intake + per-jurisdiction copyright/rights-clearance checklisting, HARD-gated on an official spec-basis citation (`:production/intake`/`:jurisdiction/assess`) | A full rights-management/clearance database (territory-by-territory licensing carve-outs, windowing-schedule engine, royalty/collections accounting -- see `release-channel-restricted?`'s docstring) |
| Rights-clearance screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:rights/screen`) | Real production-management-system integration, marketing/box-office workflows |
| Production release, HARD-gated on the proposed release channel not appearing on the production's own restricted-channel list and a double-release guard (`:production/release`) | Ongoing rehearsal/scheduling workflows themselves |
| Immutable audit ledger for every intake/assessment/screening/release decision | |

Extending coverage is additive: add the next gate (e.g. a talent-
contract-compliance check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor re-
verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`entertainment.facts/coverage` reports how many requested
jurisdictions actually have an official spec-basis in `entertainment.
facts/catalog` -- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194
jurisdictions worldwide. This is a starting catalog to prove the
governor contract end-to-end, not a claim of global coverage. Adding a
jurisdiction is additive: one map entry in `entertainment.facts/
catalog`, citing a real official source -- never fabricate a
jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `EntertainmentOps-LLM` + `Content and Booking
Governor` run as real, tested code (see `Run` above), promoted from
the originally-published `:blueprint`-tier scaffold, modeled closely on
the twenty-four prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
