# Business Model: Creative, arts and entertainment activities

## Classification

- Repository: `cloud-itonami-isic-9000`
- ISIC Rev.5: `9000`
- Activity: creative, arts and entertainment activities -- live performance, theatrical production and other creative arts presentation
- Social impact: cultural/recreational access, data sovereignty, transparent audit

## Customer

- independent theatre/performance companies
- cooperative arts collectives
- community entertainment venues

## Offer

- production/booking intake
- program/schedule proposal
- release/publication proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per venue/company
- support: monthly retainer with SLA
- migration: import from an incumbent booking system
- per-production fee

## Trust Controls

- no production is released/published to the public without human
  sign-off (a rights-holder/producer)
- a fabricated jurisdiction citation, incomplete rights-clearance
  evidence, a release channel that appears on the production's own
  restricted-channel list, or an unresolved rights-clearance flag --
  each forces a hold, not an override
- a production cannot be released twice: a double-release attempt is
  held off this actor's own production facts alone, with no upstream
  comparison needed
- every intake, assessment, screening and release path is auditable
- performer/artist personal data stays outside Git
- emergency manual override paths remain outside LLM control
