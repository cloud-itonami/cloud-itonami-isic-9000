(ns entertainment.governor-contract-test
  "The governor contract as executable tests -- the creative/arts/
  entertainment analog of `cloud-itonami-isic-6512`'s `casualty.
  governor-contract-test`. The single invariant under test:

    EntertainmentOps-LLM never releases a production the Content and
    Booking Governor would reject, `:production/release` NEVER auto-
    commits at any phase, `:production/intake` (no direct capital
    risk) MAY auto-commit when clean, and every decision (commit OR
    hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [entertainment.store :as store]
            [entertainment.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :producer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- screen!
  "Walks `subject` through rights screening -> approve, leaving a
  screening on file. Only safe to call for a production whose rights-
  clearance flag is already resolved -- an unresolved flag HARD-holds
  the screen itself (see `rights-clearance-unresolved-is-held-and-
  unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :rights/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :production/intake :subject "production-1"
                   :patch {:id "production-1" :production-title "Cherry Blossom Requiem"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Cherry Blossom Requiem" (:production-title (store/production db "production-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "production-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "production-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "production-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "production-1")) "no assessment written"))))

(deftest production-release-without-assessment-is-held
  (testing "production/release before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :production/release :subject "production-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest release-channel-restricted-is-held
  (testing "a production whose proposed release channel appears on its own restricted-channels list -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "production-3")
          res (exec-op actor "t5" {:op :production/release :subject "production-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:release-channel-restricted} (-> (store/ledger db) last :basis)))
      (is (empty? (store/release-history db))))))

(deftest rights-clearance-unresolved-is-held-and-unoverridable
  (testing "an unresolved rights-clearance flag on a production -> HOLD, and never reaches request-approval -- exercised via :rights/screen DIRECTLY, not via the actuation op against an unscreened production (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's and salon's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :rights/screen :subject "production-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:rights-clearance-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/rights-screening-of db "production-4")) "no clearance written"))))

(deftest production-release-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, unrestricted-channel, rights-clear production still ALWAYS interrupts for human approval -- actuation/release-production is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "production-1")
          _ (screen! actor "t7pre2" "production-1")
          r1 (exec-op actor "t7" {:op :production/release :subject "production-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, release record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:released? (store/production db "production-1"))))
          (is (= 1 (count (store/release-history db))) "one draft release record"))))))

(deftest production-release-double-release-is-held
  (testing "releasing the same production twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "production-1")
          _ (screen! actor "t8pre2" "production-1")
          _ (exec-op actor "t8a" {:op :production/release :subject "production-1"} operator)
          _ (approve! actor "t8a")
          res (exec-op actor "t8" {:op :production/release :subject "production-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-released} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/release-history db))) "still only the one earlier release"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :production/intake :subject "production-1"
                          :patch {:id "production-1" :production-title "Cherry Blossom Requiem"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "production-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
