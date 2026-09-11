(ns saltops.store-contract-test
  "The Store contract as executable tests."
  (:require [clojure.test :refer [deftest is testing]]
            [saltops.store :as store]))

(deftest seed-db-read-parity
  (let [s (store/seed-db)]
    (is (= "Northern Rock-Salt Mine" (:name (store/site s "salt-site-1"))))
    (is (true? (:registered? (store/site s "salt-site-1"))))
    (is (true? (:verified? (store/site s "salt-site-1"))))
    (is (true? (:registered? (store/site s "salt-site-3"))))
    (is (false? (:verified? (store/site s "salt-site-3"))) "seeded as registered but not yet verified")
    (is (nil? (store/site s "no-such-site")))
    (is (= ["salt-site-1" "salt-site-2" "salt-site-3"] (mapv :site-id (store/all-sites s))))
    (is (= [] (store/ledger s)))
    (is (= [] (store/coordination-log s)))))

(deftest mem-store-honors-explicit-sites-map
  (let [s (store/mem-store {"a" {:site-id "a" :registered? true :verified? true}})]
    (is (some? (store/site s "a")))
    (is (nil? (store/site s "b"))))
  (testing "an empty sites map means unregistered everywhere"
    (let [s (store/mem-store {})]
      (is (nil? (store/site s "salt-site-1"))))))

(deftest commit-record-appends-to-coordination-log
  (let [s (store/seed-db)]
    (store/commit-record! s {:op :log-production-record :site-id "salt-site-1" :value {:tonnage 100}})
    (store/commit-record! s {:op :schedule-maintenance :site-id "salt-site-1" :value {:equipment "belt"}})
    (is (= 2 (count (store/coordination-log s))))
    (is (= [:log-production-record :schedule-maintenance] (mapv :op (store/coordination-log s))))))

(deftest ledger-is-append-only-and-order-preserving
  (let [s (store/seed-db)]
    (store/append-ledger! s {:op :a :disposition :commit})
    (store/append-ledger! s {:op :b :disposition :hold})
    (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))

(deftest with-sites-replaces-directory-when-non-empty
  (let [s (store/mem-store {"x" {:site-id "x" :registered? true :verified? true}})]
    (store/with-sites s {"y" {:site-id "y" :registered? true :verified? true}})
    (is (nil? (store/site s "x")))
    (is (some? (store/site s "y"))))
  (testing "an empty replacement is a no-op (never silently wipes the directory)"
    (let [s (store/mem-store {"x" {:site-id "x" :registered? true :verified? true}})]
      (store/with-sites s {})
      (is (some? (store/site s "x"))))))
