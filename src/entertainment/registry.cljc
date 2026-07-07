(ns entertainment.registry
  "Pure-function production-release record construction -- an
  append-only production book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a production-release
  reference number -- every venue/company/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `entertainment.facts` uses.

  `release-channel-restricted?` reuses `clinic.registry/treatment-
  contraindicated?`'s set-membership/conflict shape (established
  first for a clinical-safety concept, reused verbatim by `veterinary.
  registry/treatment-contraindicated?` for the identical concept) for
  a THIRD, genuinely different real-world concern: a production's own
  proposed release channel must not appear in its own recorded set of
  contractually-restricted channels (e.g. an exclusivity window that
  forbids day-and-date streaming alongside a theatrical release, or a
  festival-exclusivity clause). See its own docstring for the honest
  simplification this makes vs. a full rights-management/clearance
  database.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real production-management system. It builds the RECORD
  a venue/company would keep, not the act of releasing the production
  itself (that is `entertainment.operation`'s `:production/release`,
  always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  venue/company's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn release-channel-restricted?
  "Does `production`'s own `:proposed-release-channel` appear in its
  own `:restricted-channels` set? A pure ground-truth check against
  the production's own permanent fields -- reuses `clinic.registry/
  treatment-contraindicated?`'s set-membership/conflict shape for a
  third domain (this R0 does not model a full rights-management/
  clearance database, licensing-territory carve-outs, or windowing
  schedules -- only whether the proposed release channel itself is a
  member of the production's own recorded restricted-channel set)."
  [{:keys [proposed-release-channel restricted-channels]}]
  (contains? (set restricted-channels) proposed-release-channel))

(defn register-production-release
  "Validate + construct the PRODUCTION-RELEASE registration DRAFT --
  the venue/company's own legal act of releasing or publishing a real
  production to the public. Pure function -- does not touch any real
  production-management system; it builds the RECORD a venue/company
  would keep. `entertainment.governor` independently re-verifies the
  production's own release-channel restrictions and rights-clearance
  status, and blocks a double-release of the same production, before
  this is ever allowed to commit."
  [production-id jurisdiction sequence]
  (when-not (and production-id (not= production-id ""))
    (throw (ex-info "production-release: production_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "production-release: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "production-release: sequence must be >= 0" {})))
  (let [release-number (str (str/upper-case jurisdiction) "-REL-" (zero-pad sequence 6))
        record {"record_id" release-number
                "kind" "production-release-draft"
                "production_id" production-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "release_number" release-number
     "certificate" (unsigned-certificate "ProductionRelease" release-number release-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
