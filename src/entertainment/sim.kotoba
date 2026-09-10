(ns entertainment.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean production
  through intake -> jurisdiction assessment -> rights screening ->
  production-release proposal (always escalates) -> human approval ->
  commit, then shows four HARD holds (a jurisdiction with no spec-
  basis, a proposed release channel that appears on the production's
  own restricted-channel list, an unresolved rights-clearance flag
  screened directly via `:rights/screen` [never via the actuation op
  against an unscreened production -- see this actor's own governor ns
  docstring / the lesson `parksafety`'s ADR-2607071922 Decision 5,
  `eldercare`'s, `museum`'s, `conservation`'s and `salon`'s ADR-0001s
  already recorded], and a double release of an already-processed
  production) that never reach a human at all, and prints the audit
  ledger + the draft production-release records."
  (:require [langgraph.graph :as g]
            [entertainment.store :as store]
            [entertainment.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :producer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== production/intake production-1 (JPN, clean; no restricted channels, rights-clearance resolved) ==")
    (println (exec! actor "t1" {:op :production/intake :subject "production-1"
                                :patch {:id "production-1" :production-title "Cherry Blossom Requiem"}} operator))

    (println "== jurisdiction/assess production-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "production-1"} operator))
    (println (approve! actor "t2"))

    (println "== rights/screen production-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :rights/screen :subject "production-1"} operator))
    (println (approve! actor "t3"))

    (println "== production/release production-1 (always escalates -- actuation/release-production) ==")
    (let [r (exec! actor "t4" {:op :production/release :subject "production-1"} operator)]
      (println r)
      (println "-- human producer approves --")
      (println (approve! actor "t4")))

    (println "== jurisdiction/assess production-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t5" {:op :jurisdiction/assess :subject "production-2" :no-spec? true} operator))

    (println "== jurisdiction/assess production-3 (escalates -- human approves; sets up the restricted-channel test) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "production-3"} operator))
    (println (approve! actor "t6"))

    (println "== production/release production-3 (streaming is in restricted-channels -> HARD hold) ==")
    (println (exec! actor "t7" {:op :production/release :subject "production-3"} operator))

    (println "== rights/screen production-4 (unresolved rights-clearance flag -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :rights/screen :subject "production-4"} operator))

    (println "== production/release production-1 AGAIN (double-release -> HARD hold) ==")
    (println (exec! actor "t9" {:op :production/release :subject "production-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft production-release records ==")
    (doseq [r (store/release-history db)] (println r))))
