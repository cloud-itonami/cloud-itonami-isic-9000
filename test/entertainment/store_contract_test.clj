(ns entertainment.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [entertainment.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Cherry Blossom Requiem" (:production-title (store/production s "production-1"))))
      (is (= "JPN" (:jurisdiction (store/production s "production-1"))))
      (is (= :streaming (:proposed-release-channel (store/production s "production-1"))))
      (is (= #{} (:restricted-channels (store/production s "production-1"))))
      (is (true? (:rights-clearance-resolved? (store/production s "production-1"))))
      (is (= #{:streaming} (:restricted-channels (store/production s "production-3"))))
      (is (false? (:rights-clearance-resolved? (store/production s "production-4"))))
      (is (false? (:released? (store/production s "production-1"))))
      (is (= ["production-1" "production-2" "production-3" "production-4"]
             (mapv :id (store/all-productions s))))
      (is (nil? (store/rights-screening-of s "production-1")))
      (is (nil? (store/assessment-of s "production-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/release-history s)))
      (is (zero? (store/next-sequence s "JPN")))
      (is (false? (store/production-already-released? s "production-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :production/upsert
                                 :value {:id "production-1" :production-title "Cherry Blossom Requiem"}})
        (is (= "Cherry Blossom Requiem" (:production-title (store/production s "production-1"))))
        (is (= :streaming (:proposed-release-channel (store/production s "production-1"))) "unrelated field preserved"))
      (testing "assessment / rights-screening payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["production-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "production-1")))
        (store/commit-record! s {:effect :rights-screening/set :path ["production-1"]
                                 :payload {:production-id "production-1" :verdict :resolved}})
        (is (= {:production-id "production-1" :verdict :resolved} (store/rights-screening-of s "production-1"))))
      (testing "production release drafts a release record and advances the sequence"
        (store/commit-record! s {:effect :production/mark-released :path ["production-1"]})
        (is (= "JPN-REL-000000" (get (first (store/release-history s)) "record_id")))
        (is (= "production-release-draft" (get (first (store/release-history s)) "kind")))
        (is (true? (:released? (store/production s "production-1"))))
        (is (= 1 (count (store/release-history s))))
        (is (= 1 (store/next-sequence s "JPN")))
        (is (true? (store/production-already-released? s "production-1")))
        (is (false? (store/production-already-released? s "production-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/production s "nope")))
    (is (= [] (store/all-productions s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/release-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (store/with-productions s {"x" {:id "x" :production-title "n" :proposed-release-channel :streaming
                                    :restricted-channels #{} :rights-clearance-resolved? true
                                    :released? false :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:production-title (store/production s "x"))))))
