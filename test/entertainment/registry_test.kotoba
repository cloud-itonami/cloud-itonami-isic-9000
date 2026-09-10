(ns entertainment.registry-test
  (:require [clojure.test :refer [deftest is]]
            [entertainment.registry :as r]))

;; ----------------------------- release-channel-restricted? -----------------------------

(deftest not-restricted-when-channel-not-in-set
  (is (not (r/release-channel-restricted? {:proposed-release-channel :streaming :restricted-channels #{}})))
  (is (not (r/release-channel-restricted? {:proposed-release-channel :streaming :restricted-channels #{:broadcast}}))))

(deftest restricted-when-channel-in-set
  (is (r/release-channel-restricted? {:proposed-release-channel :streaming :restricted-channels #{:streaming}}))
  (is (r/release-channel-restricted? {:proposed-release-channel :broadcast :restricted-channels #{:streaming :broadcast}})))

(deftest restricted-is-false-on-missing-fields
  (is (not (r/release-channel-restricted? {})))
  (is (not (r/release-channel-restricted? {:proposed-release-channel :streaming}))))

;; ----------------------------- register-production-release -----------------------------

(deftest production-release-is-a-draft-not-a-real-release
  (let [result (r/register-production-release "production-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest production-release-assigns-release-number
  (let [result (r/register-production-release "production-1" "JPN" 7)]
    (is (= (get result "release_number") "JPN-REL-000007"))
    (is (= (get-in result ["record" "production_id"]) "production-1"))
    (is (= (get-in result ["record" "kind"]) "production-release-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest production-release-validation-rules
  (is (thrown? Exception (r/register-production-release "" "JPN" 0)))
  (is (thrown? Exception (r/register-production-release "production-1" "" 0)))
  (is (thrown? Exception (r/register-production-release "production-1" "JPN" -1))))

(deftest release-history-is-append-only
  (let [c1 (r/register-production-release "production-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-production-release "production-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-REL-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-REL-000001" (get-in hist2 [1 "record_id"])))))
