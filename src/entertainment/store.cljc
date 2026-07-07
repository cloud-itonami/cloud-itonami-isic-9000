(ns entertainment.store
  "SSoT for the creative/arts/entertainment actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/entertainment/store_contract_test.clj), which is the whole
  point: the actor, the Content and Booking Governor and the audit
  ledger never know which SSoT they run on.

  Like `credit.store`'s/`accounting.store`'s/`marketadmin.store`'s/
  `testlab.store`'s/`clinic.store`'s/`salon.store`'s simpler entities,
  a PRODUCTION is acted on directly by the ONE actuation op -- no
  dynamically-filed sub-record, and the double-release guard checks a
  dedicated `:released?` boolean rather than a `:status` value, the
  same discipline `accounting.governor`'s/`marketadmin.governor`'s/
  `testlab.governor`'s/`clinic.governor`'s/`salon.governor`'s guards
  establish.

  The ledger stays append-only on every backend: 'which production was
  screened for an unresolved rights-clearance flag, which production
  was released, on what jurisdictional basis, approved by whom' is
  always a query over an immutable log -- the audit trail a rights-
  holder/licensor trusting a venue/company needs, and the evidence an
  operator needs if a release is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [entertainment.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (production [s id])
  (all-productions [s])
  (rights-screening-of [s production-id] "committed rights-clearance screening verdict for a production, or nil")
  (assessment-of [s production-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (release-history [s] "the append-only production-release history (entertainment.registry drafts)")
  (next-sequence [s jurisdiction] "next production-release-number sequence for a jurisdiction")
  (production-already-released? [s production-id] "has this production already been released?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-productions [s productions] "replace/seed the production directory (map id->production)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained production set so the actor + tests run
  offline."
  []
  {:productions
   {"production-1" {:id "production-1" :production-title "Cherry Blossom Requiem"
                    :proposed-release-channel :streaming :restricted-channels #{}
                    :rights-clearance-resolved? true :released? false
                    :jurisdiction "JPN" :status :intake}
    "production-2" {:id "production-2" :production-title "Unregistered Work"
                    :proposed-release-channel :streaming :restricted-channels #{}
                    :rights-clearance-resolved? true :released? false
                    :jurisdiction "ATL" :status :intake}
    "production-3" {:id "production-3" :production-title "Exclusivity-Windowed Show"
                    :proposed-release-channel :streaming :restricted-channels #{:streaming}
                    :rights-clearance-resolved? true :released? false
                    :jurisdiction "JPN" :status :intake}
    "production-4" {:id "production-4" :production-title "Unlicensed-Score Production"
                    :proposed-release-channel :streaming :restricted-channels #{}
                    :rights-clearance-resolved? false :released? false
                    :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-release!
  "Backend-agnostic `:production/mark-released` -- looks up the
  production via the protocol and drafts the production-release
  record, and returns {:result .. :production-patch ..} for the
  caller to persist."
  [s production-id]
  (let [p (production s production-id)
        seq-n (next-sequence s (:jurisdiction p))
        result (registry/register-production-release production-id (:jurisdiction p) seq-n)]
    {:result result
     :production-patch {:released? true
                        :release-number (get result "release_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (production [_ id] (get-in @a [:productions id]))
  (all-productions [_] (sort-by :id (vals (:productions @a))))
  (rights-screening-of [_ id] (get-in @a [:rights-screenings id]))
  (assessment-of [_ production-id] (get-in @a [:assessments production-id]))
  (ledger [_] (:ledger @a))
  (release-history [_] (:releases @a))
  (next-sequence [_ jurisdiction] (get-in @a [:sequences jurisdiction] 0))
  (production-already-released? [_ production-id] (boolean (get-in @a [:productions production-id :released?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :production/upsert
      (swap! a update-in [:productions (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :rights-screening/set
      (swap! a assoc-in [:rights-screenings (first path)] payload)

      :production/mark-released
      (let [production-id (first path)
            {:keys [result production-patch]} (finalize-release! s production-id)
            jurisdiction (:jurisdiction (production s production-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences jurisdiction] (fnil inc 0))
                       (update-in [:productions production-id] merge production-patch)
                       (update :releases registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-productions [s productions] (when (seq productions) (swap! a assoc :productions productions)) s))

(defn seed-db
  "A MemStore seeded with the demo production set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :rights-screenings {} :ledger [] :sequences {}
                           :releases []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/rights-screening payloads, ledger
  facts, release records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:production/id             {:db/unique :db.unique/identity}
   :assessment/production-id  {:db/unique :db.unique/identity}
   :rights-screening/production-id {:db/unique :db.unique/identity}
   :ledger/seq                {:db/unique :db.unique/identity}
   :release/seq                {:db/unique :db.unique/identity}
   :sequence/jurisdiction      {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- production->tx [{:keys [id production-title proposed-release-channel restricted-channels
                               rights-clearance-resolved? released?
                               jurisdiction status release-number]}]
  (cond-> {:production/id id}
    production-title                   (assoc :production/production-title production-title)
    proposed-release-channel           (assoc :production/proposed-release-channel proposed-release-channel)
    restricted-channels                (assoc :production/restricted-channels (enc restricted-channels))
    (some? rights-clearance-resolved?) (assoc :production/rights-clearance-resolved? rights-clearance-resolved?)
    (some? released?)                  (assoc :production/released? released?)
    jurisdiction                       (assoc :production/jurisdiction jurisdiction)
    status                             (assoc :production/status status)
    release-number                     (assoc :production/release-number release-number)))

(def ^:private production-pull
  [:production/id :production/production-title :production/proposed-release-channel
   :production/restricted-channels :production/rights-clearance-resolved? :production/released?
   :production/jurisdiction :production/status :production/release-number])

(defn- pull->production [m]
  (when (:production/id m)
    {:id (:production/id m) :production-title (:production/production-title m)
     :proposed-release-channel (:production/proposed-release-channel m)
     :restricted-channels (or (dec* (:production/restricted-channels m)) #{})
     :rights-clearance-resolved? (boolean (:production/rights-clearance-resolved? m))
     :released? (boolean (:production/released? m))
     :jurisdiction (:production/jurisdiction m) :status (:production/status m)
     :release-number (:production/release-number m)}))

(defrecord DatomicStore [conn]
  Store
  (production [_ id]
    (pull->production (d/pull (d/db conn) production-pull [:production/id id])))
  (all-productions [_]
    (->> (d/q '[:find [?id ...] :where [?e :production/id ?id]] (d/db conn))
         (map #(pull->production (d/pull (d/db conn) production-pull [:production/id %])))
         (sort-by :id)))
  (rights-screening-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?pid
                :where [?k :rights-screening/production-id ?pid] [?k :rights-screening/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ production-id]
    (dec* (d/q '[:find ?p . :in $ ?pid
                :where [?a :assessment/production-id ?pid] [?a :assessment/payload ?p]]
              (d/db conn) production-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (release-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :release/seq ?s] [?e :release/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sequence/jurisdiction ?j] [?e :sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (production-already-released? [s production-id]
    (boolean (:released? (production s production-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :production/upsert
      (d/transact! conn [(production->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/production-id (first path) :assessment/payload (enc payload)}])

      :rights-screening/set
      (d/transact! conn [{:rights-screening/production-id (first path) :rights-screening/payload (enc payload)}])

      :production/mark-released
      (let [production-id (first path)
            {:keys [result production-patch]} (finalize-release! s production-id)
            jurisdiction (:jurisdiction (production s production-id))
            next-n (inc (next-sequence s jurisdiction))]
        (d/transact! conn
                     [(production->tx (assoc production-patch :id production-id))
                      {:sequence/jurisdiction jurisdiction :sequence/next next-n}
                      {:release/seq (count (release-history s)) :release/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-productions [s productions]
    (when (seq productions) (d/transact! conn (mapv production->tx (vals productions)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:productions ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [productions]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-productions s productions))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo production set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
