(ns entertainment.governor
  "Content and Booking Governor -- the independent compliance layer
  that earns the EntertainmentOps-LLM the right to commit. The LLM
  has no notion of jurisdictional copyright/rights-clearance law,
  whether a proposed release channel actually appears on a
  production's own recorded restricted-channel list, whether a
  production's own rights-clearance flag is still unresolved, or when
  an act stops being a draft and becomes a real-world public release,
  so this MUST be a separate system able to *reject* a proposal and
  fall back to HOLD -- the creative/arts/entertainment analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Four checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, incomplete licensing
  evidence, a release channel that appears on the production's own
  restriction list, an unresolved rights-clearance flag, or releasing
  the same production twice). The confidence/actuation gate is SOFT:
  it asks a human to look (low confidence / actuation), and the human
  may approve -- but see `entertainment.phase`: for `:stake
  :actuation/release-production` (a real public release) NO phase
  ever allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`entertainment.
                                       facts`), or invent one? Like
                                       `clinic.governor`'s/`salon.
                                       governor`'s actuation ops,
                                       `:production/release` acts
                                       directly on a pre-seeded
                                       production (see `entertainment.
                                       store`'s own docstring) --
                                       there is no 'production is
                                       missing' failure mode to guard
                                       against here.
    2. Evidence incomplete         -- for `:production/release`, has
                                       the jurisdiction actually been
                                       assessed with a full rights-
                                       clearance/licensing evidence
                                       checklist on file?
    3. Release channel restricted  -- for `:production/release`,
                                       INDEPENDENTLY recompute whether
                                       the production's own proposed
                                       release channel appears in its
                                       own restricted-channels set
                                       (`entertainment.registry/
                                       release-channel-restricted?`) --
                                       needs no proposal inspection or
                                       stored-verdict lookup at all.
                                       Reuses `clinic.governor/
                                       contraindicated-violations`'s
                                       set-membership/conflict shape
                                       (reused verbatim once already by
                                       `veterinary.governor`) for a
                                       THIRD domain.
    4. Rights clearance unresolved -- reported by THIS proposal itself
                                       (a `:rights/screen` that just
                                       found an unresolved clearance
                                       flag), or already on file for
                                       the production (`:rights/
                                       screen`/`:production/release`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       `marketadmin.governor/
                                       surveillance-flag-unresolved-
                                       violations`/`testlab.governor/
                                       calibration-not-current-
                                       violations`/`clinic.governor/
                                       credential-not-current-
                                       violations`/`registrar.governor/
                                       integrity-flag-unresolved-
                                       violations`/`wagering.governor/
                                       patron-flag-unresolved-
                                       violations`/`veterinary.
                                       governor/credential-not-current-
                                       violations`/`funeral.governor/
                                       authorization-unverified-
                                       violations`/`repairshop.
                                       governor/safety-test-not-passed-
                                       violations`/`parksafety.
                                       governor/inspection-not-passed-
                                       violations`/`eldercare.governor/
                                       incident-flag-unresolved-
                                       violations`/`museum.governor/
                                       incident-flag-unresolved-
                                       violations`/`conservation.
                                       governor/welfare-flag-
                                       unresolved-violations`/`salon.
                                       governor/allergy-flag-
                                       unresolved-violations`
                                       established -- the FOURTEENTH
                                       distinct application of this
                                       exact discipline. Like the
                                       eleven most recent siblings'
                                       equivalent checks, this is
                                       exercised in tests/demo via
                                       `:rights/screen` DIRECTLY, not
                                       via the actuation op against an
                                       unscreened production -- see
                                       this ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:production/
                                       release` (a REAL, public-facing
                                       act) -> escalate.

  One more guard, double-release prevention, is enforced but NOT
  listed as a numbered HARD check above because it needs no upstream
  comparison at all -- `already-released-violations` refuses to
  release the SAME production twice, off a dedicated `:released?`
  fact (never a `:status` value) -- the SAME 'check a dedicated
  boolean, not status' discipline `accounting.governor`'s/
  `marketadmin.governor`'s/`testlab.governor`'s/`clinic.governor`'s/
  `registrar.governor`'s/`wagering.governor`'s/`veterinary.
  governor`'s/`funeral.governor`'s/`repairshop.governor`'s/
  `parksafety.governor`'s/`eldercare.governor`'s/`museum.governor`'s/
  `conservation.governor`'s/`salon.governor`'s guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [entertainment.facts :as facts]
            [entertainment.registry :as registry]
            [entertainment.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Releasing or publishing a real production to the public is the ONE
  real-world actuation event this actor performs -- a single-member
  set, matching `cloud-itonami-isic-6511`'s/`6621`'s/`6629`'s/`6612`'s/
  `6492`'s/`7120`'s/`8620`'s/`7500`'s/`9603`'s/`9321`'s/`9602`'s
  single-actuation shape."
  #{:actuation/release-production})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:production/release`) proposal with no
  spec-basis citation is a HARD violation -- never invent a
  jurisdiction's copyright/rights-clearance requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :production/release} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:production/release`, the jurisdiction's required rights-
  clearance/licensing/rating/distribution evidence must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (= op :production/release)
    (let [p (store/production st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction p) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(権利処理記録/利用許諾契約書/レイティング記録等)が充足していない状態での公開提案"}]))))

(defn- release-channel-restricted-violations
  "For `:production/release`, INDEPENDENTLY recompute whether the
  production's own proposed release channel appears in its own
  restricted-channels set via `entertainment.registry/release-
  channel-restricted?` -- needs no proposal inspection or stored-
  verdict lookup at all, since its inputs are permanent ground-truth
  fields already on the production."
  [{:keys [op subject]} st]
  (when (= op :production/release)
    (let [p (store/production st subject)]
      (when (registry/release-channel-restricted? p)
        [{:rule :release-channel-restricted
          :detail (str subject " の提案公開経路(" (:proposed-release-channel p)
                      ")が制限経路リスト" (:restricted-channels p) "に含まれている")}]))))

(defn- rights-clearance-unresolved-violations
  "An unresolved rights-clearance flag -- reported by THIS proposal
  (e.g. a `:rights/screen` that itself just found an unresolved flag),
  or already on file in the store for the production (`:rights/
  screen`/`:production/release`) -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        production-id (when (contains? #{:rights/screen :production/release} op) subject)
        hit-on-file? (and production-id (= :unresolved (:verdict (store/rights-screening-of st production-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :rights-clearance-unresolved
        :detail "未解決の権利処理フラグが残っている作品に対する公開提案は進められない"}])))

(defn- already-released-violations
  "For `:production/release`, refuses to release the SAME production
  twice, off a dedicated `:released?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :production/release)
    (when (store/production-already-released? st subject)
      [{:rule :already-released
        :detail (str subject " は既に公開済み")}])))

(defn check
  "Censors an EntertainmentOps-LLM proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (release-channel-restricted-violations request st)
                           (rights-clearance-unresolved-violations request proposal st)
                           (already-released-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
