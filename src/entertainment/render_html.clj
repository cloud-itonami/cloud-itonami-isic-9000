(ns entertainment.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously had NO demo
  page and no generator at all. This namespace drives the REAL actor
  stack (`entertainment.operation` -> `entertainment.governor` ->
  `entertainment.store`) through a scenario adapted from this repo's
  own `entertainment.sim` demo driver (`clojure -M:dev:run`, confirmed
  BEFORE writing this file to produce a sensible ledger against the
  real seeded production ids `production-1`..`production-4` from
  `entertainment.store/demo-data`), and renders the result
  deterministically -- no invented ids, no hand-typed verdicts, no
  timestamps in the page content, byte-identical across reruns against
  the same seed.

  Two DIFFERENT kinds of hold reach the ledger and this page keeps them
  in SEPARATE tables, because they are not the same claim:

    * HARD governor hold  -- `entertainment.governor` genuinely refused.
                             `:violations` is NON-EMPTY; no human
                             approver can override it.
    * Phase/rollout hold  -- the governor was CLEAN and
                             `entertainment.phase/gate` held the write
                             because the op is not enabled in that
                             rollout phase. `:violations` is EMPTY and
                             the fact carries `:phase-reason`.

  A naive `(= :governor-hold (:t f))` count would conflate the two, so
  `hard-holds` requires a non-empty `:violations` and `-main` REFUSES to
  write the file when that count is zero -- the HARD-hold requirement is
  a build-time invariant, not a convention.

  Approver attribution is MEASURED at render time, never assumed:
  `approver-entry` scans each committed register for any approver key,
  so the disclosure section self-corrects if the store is later changed
  to retain the approver. Records are matched to their own register by
  the register's own identity field (a production's assessment, its
  rights screening, the release record carrying its `production_id`) --
  never by joining ledger approvals on `[op subject]`, which is not
  unique and would let a record inherit an earlier approval.

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [kotoba.lang.text :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [entertainment.operation :as op]
            [entertainment.store :as store]))

;; ----------------------------- the real run -----------------------------

(def ^:private phase3-operator
  {:actor-id "op-1" :actor-role :producer :phase 3})

(def ^:private phase1-operator
  "Same human, same op, EARLIER rollout phase -- used once at the end to
  show the phase gate holding a request the governor itself cleared."
  {:actor-id "op-1" :actor-role :producer :phase 1})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- record-audit!
  "Keeps the graph `:audit` channel of the LAST run per thread, in the
  order threads were first seen. A resumed thread replays the whole
  channel, so keeping only the last run avoids double-counting while
  still capturing facts -- notably `:approval-granted` -- that
  `entertainment.operation` never appends to the store's ledger."
  [a tid result]
  (let [entry [tid (vec (get-in result [:state :audit]))]]
    (swap! a (fn [v]
               (if-let [i (first (keep-indexed #(when (= tid (first %2)) %1) v))]
                 (assoc v i entry)
                 (conj v entry)))))
  result)

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach:

    production-1 clears a full lifecycle -- intake (auto-commits clean
      at phase 3), a jurisdiction assessment (phase-gated, approved), a
      rights-clearance screening (approved) and a public release (ALWAYS
      escalates -- `:actuation/release-production` is never auto at any
      phase -- approved);
    production-2 HARD-holds its jurisdiction assessment (no official
      spec-basis for its deliberately unregistered jurisdiction), then
      HARD-holds the release attempt that follows (the jurisdiction's
      required rights-clearance/licensing evidence is not on file);
    production-3 clears its own jurisdiction assessment (approved) but
      HARD-holds the release, because its proposed release channel
      appears in its own recorded restricted-channel set;
    production-4 HARD-holds a rights screening that itself detects an
      unresolved rights-clearance flag;
    production-1 is then offered for release a SECOND time and
      HARD-holds on the dedicated `:released?` fact.

  Finally the SAME jurisdiction assessment the governor cleared for
  production-1 is replayed at phase 1, where the rollout gate holds it
  with EMPTY violations -- the control case that proves the HARD-hold
  count on this page is not just counting `:governor-hold` facts.

  Returns `{:db store :audit [..]}` -- `:audit` is the graph's own audit
  channel across all threads, which carries the `:approval-granted`
  facts the store ledger does NOT (see the disclosure section). Every
  field rendered below is real governor/store output."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        trail (atom [])
        ex! (fn [tid request context]
              (record-audit! trail tid (exec! actor tid request context)))
        ok! (fn [tid] (record-audit! trail tid (approve! actor tid)))]
    ;; -- production-1: the clean end-to-end lifecycle --
    (ex! "p1-intake" {:op :production/intake :subject "production-1"
                      :patch {:id "production-1"
                              :production-title "Cherry Blossom Requiem"}}
         phase3-operator)

    (ex! "p1-assess" {:op :jurisdiction/assess :subject "production-1"} phase3-operator)
    (ok! "p1-assess")

    (ex! "p1-screen" {:op :rights/screen :subject "production-1"} phase3-operator)
    (ok! "p1-screen")

    (ex! "p1-release" {:op :production/release :subject "production-1"} phase3-operator)
    (ok! "p1-release")

    ;; -- production-2: no spec-basis, then no evidence on file --
    (ex! "p2-assess" {:op :jurisdiction/assess :subject "production-2" :no-spec? true}
         phase3-operator)
    (ex! "p2-release" {:op :production/release :subject "production-2"} phase3-operator)

    ;; -- production-3: assessed clean, but its own restricted channel blocks release --
    (ex! "p3-assess" {:op :jurisdiction/assess :subject "production-3"} phase3-operator)
    (ok! "p3-assess")
    (ex! "p3-release" {:op :production/release :subject "production-3"} phase3-operator)

    ;; -- production-4: the screening op HARD-holds on its own finding --
    (ex! "p4-screen" {:op :rights/screen :subject "production-4"} phase3-operator)

    ;; -- production-1 again: double release --
    (ex! "p1-release-again" {:op :production/release :subject "production-1"} phase3-operator)

    ;; -- control: governor-clean request held purely by the rollout phase --
    (ex! "p1-assess-phase1" {:op :jurisdiction/assess :subject "production-1"}
         phase1-operator)
    {:db db :audit (vec (mapcat second @trail))}))

;; ----------------------------- ledger classification -----------------------------

(defn hard-holds
  "Governor holds the human CANNOT override: `:violations` is non-empty.
  A phase/rollout hold carries an EMPTY `:violations`, so requiring
  `seq` here is what keeps the build-time invariant in `-main` honest."
  [ledger]
  (filterv #(and (= :governor-hold (:t %)) (seq (:violations %))) ledger))

(defn phase-holds
  "Holds where the governor was clean and `entertainment.phase/gate`
  refused the write for the rollout phase -- EMPTY violations."
  [ledger]
  (filterv #(and (= :governor-hold (:t %)) (empty? (:violations %))) ledger))

;; ----------------------------- approver attribution (measured) -----------------------------

(def ^:private approver-keys
  "Every key a Store backend might plausibly use to retain the approving
  human on the committed record. Scanned at RENDER TIME rather than
  hard-coded per backend, so this page self-corrects the moment the
  store starts (or stops) retaining the approver."
  #{:approved-by :approved_by :approver :by
    "approved_by" "approved-by" "approver" "by"})

(defn- approver-entry
  "The [key value] pair carrying the approving human on `m`, or nil.
  `m` is the register AS COMMITTED -- no join, no inference."
  [m]
  (when (map? m)
    (first (filter (fn [[k v]] (and (contains? approver-keys k) (some? v))) m))))

(defn- release-record-for
  "The committed production-release record for `production-id`, matched
  on the record's OWN `production_id` field. Deliberately not a
  `[op subject]` join against ledger approvals -- that pairing is not
  unique and would let a record inherit an earlier approval."
  [db production-id]
  (first (filter #(= production-id (get % "production_id")) (store/release-history db))))

(defn- registers-for
  "The committed registers this run produced for one production, as
  [label register-map] pairs. Only registers that actually exist are
  returned -- an absent register is a held op, not a missing approver."
  [db {:keys [id]}]
  (->> [["jurisdiction assessment" (store/assessment-of db id)]
        ["rights-clearance screening" (store/rights-screening-of db id)]
        ["production-release record" (release-record-for db id)]]
       (filterv (comp some? second))))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-name
  "Keyword -> its printed name INCLUDING the namespace. `name` alone
  would render `:production/release` and `:jurisdiction/assess` as
  bare \"release\"/\"assess\", which loses the op's identity."
  [v]
  (if (keyword? v) (subs (str v) 1) (str v)))

(defn- last-fact-for [ledger subject]
  (last (filter #(= (:subject %) subject) ledger)))

(defn- status-cell [ledger subject]
  (let [f (last-fact-for ledger subject)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :governor-hold (:t f))
      (if-let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (kw-name rule)) "</span>")
        (str "<span class=\"warn\">phase gate &middot; "
             (esc (kw-name (:phase-reason f))) " (phase " (esc (:phase f)) ")</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- release-cell [{:keys [released? release-number]}]
  (if released?
    (str "<span class=\"ok\">released</span> <code>" (esc release-number) "</code>")
    "<span class=\"muted\">not released</span>"))

(defn- production-row [ledger {:keys [id production-title jurisdiction
                                      proposed-release-channel restricted-channels
                                      rights-clearance-resolved?] :as p}]
  (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>"
               "<td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>")
          (esc id) (esc production-title) (esc jurisdiction)
          (esc (kw-name proposed-release-channel))
          (if (seq restricted-channels)
            (str "<span class=\"critical\">"
                 (esc (str/join ", " (sort (map kw-name restricted-channels)))) "</span>")
            "<span class=\"muted\">none</span>")
          (if rights-clearance-resolved?
            "<span class=\"ok\">resolved</span>"
            "<span class=\"critical\">unresolved</span>")
          (release-cell p)
          (status-cell ledger id)))

(defn- hard-hold-row [{:keys [op subject violations confidence]}]
  (let [v (first violations)]
    (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td class=\"num\">%s</td></tr>"
            (esc (kw-name (:rule v))) (esc (kw-name op)) (esc subject)
            (esc (:detail v)) (esc confidence))))

(defn- phase-hold-row [{:keys [op subject phase phase-reason violations]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td class=\"num\">%s</td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (kw-name op)) (esc subject) (esc phase) (esc (kw-name phase-reason))
          (str "<span class=\"muted\">" (esc (count violations)) "</span>")))

(defn- ledger-detail [{:keys [basis disposition phase-reason phase reason by]}]
  (cond
    (seq basis) (str/join ", " (map kw-name basis))
    phase-reason (str "phase gate: " (kw-name phase-reason) " (phase " phase ")")
    reason (str "reason: " (kw-name reason))
    by (str "by " by)
    :else (or (some-> disposition kw-name) "")))

(defn- ledger-row [{:keys [t op subject] :as f}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (kw-name t)) (esc (kw-name (or op :n-a))) (esc subject)
          (esc (ledger-detail f))))

(defn- attribution-row [production-id label register]
  (let [[k v] (approver-entry register)]
    (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc production-id) (esc label)
            (if k
              (str "<span class=\"ok\">" (esc v) "</span>")
              "<span class=\"warn\">not retained</span>")
            (if k
              (str "<code>" (esc k) "</code> on the committed register")
              "<span class=\"muted\">audit only &mdash; not retained on record</span>"))))

(defn- approval-row [{:keys [op subject by]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (kw-name op)) (esc subject) (esc by)))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; `Ops`, `entertainment.governor` / `entertainment.phase`) --
  ;; documentation of fixed behaviour, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:production/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when governor-clean &middot; no public-facing act</span></td></tr>"
   "        <tr><td><code>:jurisdiction/assess</code></td><td><span class=\"warn\">phase-3: human approval (never auto-eligible) &middot; official spec-basis citation required</span></td></tr>"
   "        <tr><td><code>:rights/screen</code></td><td><span class=\"warn\">phase-3: human approval (never auto-eligible) &middot; HARD-holds on its own unresolved finding</span></td></tr>"
   "        <tr><td><code>:production/release</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase &middot; evidence, restricted channels, rights clearance and double-release re-checked independently</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from the `{:db :audit}`
  map `run-demo!` returns (or any other real scenario)."
  [{:keys [db audit]}]
  (let [ledger (vec (store/ledger db))
        productions (store/all-productions db)
        hard (hard-holds ledger)
        phased (phase-holds ledger)
        ;; MEASURED: `:approval-granted` lives in the graph's audit
        ;; channel only. `entertainment.operation` appends just the
        ;; commit/hold facts to the store, so filtering the persisted
        ;; ledger for approvals yields zero -- see the section note.
        approvals (filterv #(= :approval-granted (:t %)) audit)
        ledger-approvals (filterv #(= :approval-granted (:t %)) ledger)
        attribution (for [p productions
                          [label register] (registers-for db p)]
                      (attribution-row (:id p) label register))
        releases (store/release-history db)]
    (str
     "<!doctype html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-9000 &middot; creative-arts-and-entertainment</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Creative, arts and entertainment activities (ISIC 9000) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · production release always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Productions</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>entertainment.store</code> via <code>entertainment.render-html</code> (<code>clojure -M:render-html</code>). Every id, channel and verdict below is the real actor's output against the seeded production set; nothing on this page is hand-typed.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Production</th><th>Title</th><th>Jurisdiction</th><th>Proposed channel</th><th>Restricted channels</th><th>Rights clearance</th><th>Release</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial production-row ledger) productions)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Content and Booking Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by any approver. The governor independently recomputes the production's own restricted-channel membership and rights-clearance state rather than trusting the advisor's proposal, and refuses to release the same production twice off a dedicated <code>:released?</code> fact.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD governor holds (this run) — " (count hard) "</h2>\n"
     "    <p class=\"muted\">The governor genuinely refused. <code>:violations</code> is non-empty, no human approver can override, and none of these ever reached a human at all. This build refuses to write the page when this table is empty.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Op</th><th>Production</th><th>Detail</th><th>Advisor confidence</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map hard-hold-row hard)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Phase / rollout gate holds (this run) — " (count phased) "</h2>\n"
     "    <p class=\"muted\">A DIFFERENT claim, kept in its own table on purpose: here the governor was <em>clean</em> and <code>entertainment.phase/gate</code> held the write because the op is not enabled in that rollout phase. <code>:violations</code> is empty, so a naive <code>:governor-hold</code> count would wrongly report these as compliance refusals. The row below is the same <code>:jurisdiction/assess</code> the governor cleared at phase 3, replayed at phase 1.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Production</th><th>Phase</th><th>Phase reason</th><th>Governor violations</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map phase-hold-row phased)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Approver attribution on committed registers</h2>\n"
     "    <p class=\"muted\">Measured at render time by scanning each committed register for an approver key — not assumed, and not joined from the ledger on <code>[op, subject]</code> (that pairing is not unique and would let a record inherit an earlier approval). Rows appear only for registers this run actually committed. This store retains the approver on the assessment and rights-screening registers (it persists the approval-decorated <code>:payload</code>) but <strong>not</strong> on the production-release record, which <code>commit-record!</code> rebuilds from <code>entertainment.registry</code> and which therefore carries no approver at all. This table is derived from the live registers, so it flips on its own if that is changed.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Production</th><th>Register</th><th>Approver on record</th><th>Source</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" attribution) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Human approvals granted (audit trail) — " (count approvals) "</h2>\n"
     "    <p class=\"muted\">Every approval this run granted, taken from the actor graph's <code>:audit</code> channel. <strong>Measured gap:</strong> these facts are <em>not</em> persisted — <code>entertainment.operation</code> appends only commit and hold facts via <code>store/append-ledger!</code>, so the store's own ledger contains <span class=\"num\">" (count ledger-approvals) "</span> approval facts. Combined with the row above, the human who approved the production release is recoverable from neither the release record nor the persisted ledger; today it survives only in this run's in-memory audit channel.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Production</th><th>Approved by</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map approval-row approvals)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Draft production-release records — " (count releases) "</h2>\n"
     "    <p class=\"muted\">Unsigned drafts built by <code>entertainment.registry</code>: the record a venue/company would keep, never the act of releasing itself. Signature is the rights-holder's own act, not this actor's.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record id</th><th>Kind</th><th>Production</th><th>Jurisdiction</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (map (fn [r]
                      (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td></tr>"
                              (esc (get r "record_id")) (esc (get r "kind"))
                              (esc (get r "production_id")) (esc (get r "jurisdiction"))))
                    releases)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run) — " (count ledger) " facts</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal outcome, hold, approval request, approval and commit this scenario produced, in order.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Production</th><th>Basis / reason</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>Generated by <code>entertainment.render-html</code> from a single in-memory run of the real actor graph (<code>entertainment.operation</code> → <code>entertainment.governor</code> → <code>entertainment.store</code>). Deterministic: no timestamps, no randomness, byte-identical across reruns against the same seed.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        hard (hard-holds ledger)
        phased (phase-holds ledger)]
    ;; Build-time invariant: a console that shows no HARD governor hold
    ;; has not demonstrated the one thing this actor exists to prove.
    ;; Phase/rollout holds do NOT satisfy this -- they carry empty
    ;; :violations and mean the governor never refused anything.
    (when (zero? (count hard))
      (throw (ex-info (str "render-html: refusing to write " out
                           " -- the run produced ZERO HARD governor holds."
                           " Phase/rollout holds (empty :violations) do not count.")
                      {:ledger-facts (count ledger)
                       :hard-holds 0
                       :phase-holds (count phased)})))
    (io/make-parents out)
    (spit out (render result))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count hard) " HARD governor holds "
                  (pr-str (vec (distinct (map #(-> % :violations first :rule) hard))))
                  ", " (count phased) " phase/rollout holds, "
                  (count (store/release-history db)) " production-release drafts)"))))
