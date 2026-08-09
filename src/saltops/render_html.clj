(ns saltops.render-html
  "Build-time HTML renderer. Drives the REAL actor stack deterministically.
   Usage: clojure -M:dev:render-html [out-file]."
  (:require [clojure.string :as str]
            [saltops.store :as store]
            [saltops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private op-p1 {:actor-id "op-1" :actor-role :shift-supervisor :phase 1})
(def ^:private op-p3 {:actor-id "op-1" :actor-role :shift-supervisor :phase 3})
(defn- exec! [actor tid request ctx] (g/run* actor {:request request :context ctx} {:thread-id tid}))
(defn- approve! [actor tid] (g/run* actor {:approval {:status :approved :by "shift-supervisor-1"}} {:thread-id tid :resume? true}))

(defn run-demo! []
  (let [db (store/seed-db) actor (op/build db)]
    ;; phase-1 production log always needs approval
    (exec! actor "t1" {:op :log-production-record :site-id "salt-site-1"
                       :patch {:tonnage 3200 :purity 0.985 :shift "day"}} op-p1)
    (approve! actor "t1")
    ;; phase-3 clean auto-commit
    (exec! actor "t2" {:op :log-production-record :site-id "salt-site-1"
                       :patch {:tonnage 3350 :purity 0.982 :shift "night"}} op-p3)
    (exec! actor "t3" {:op :schedule-maintenance :site-id "salt-site-2"
                       :patch {:equipment "brine-pump-3" :window "2026-07-20"}} op-p3)
    (exec! actor "t4" {:op :coordinate-shipment :site-id "salt-site-1"
                       :patch {:carrier "rail-co-1" :tonnage 3200}} op-p3)
    ;; always-escalate safety concern, then human approve
    (exec! actor "t5" {:op :flag-safety-concern :site-id "salt-site-2"
                       :patch {:concern "elevated brine seepage near containment berm" :confidence 0.95}} op-p3)
    (approve! actor "t5")
    ;; HARD holds: unregistered + registered-but-unverified
    (exec! actor "t6" {:op :log-production-record :site-id "salt-site-9"
                       :patch {:tonnage 100}} op-p3)
    (exec! actor "t7" {:op :log-production-record :site-id "salt-site-3"
                       :patch {:tonnage 100}} op-p3)
    ;; scope exclusion via :out-of-scope? (real advisor failure-mode hook)
    (exec! actor "t8" {:op :schedule-maintenance :site-id "salt-site-1"
                       :out-of-scope? true
                       :patch {}} op-p3)
    db))

(defn- esc [v] (-> (str v) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- last-fact-for [ledger sid] (last (filter #(= (:site-id %) sid) ledger)))
(defn- status-cell [ledger sid]
  (let [f (last-fact-for ledger sid)]
    (cond (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved</span>"
      (= :governor-hold (:t f)) (let [rule (-> f :basis first)] (str "<span class=\"critical\">HARD hold: " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))
(defn- ledger-row [{:keys [t op site-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc site-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))
(def ^:private gate-rows
  ["        <tr><td><code>:log-production-record</code></td><td><span class=\"warn\">phase-1 always approval; phase-3 auto-commit when clean</span></td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"warn\">registered + verified site required</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval (site safety)</span></td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"warn\">phase-3 auto-commit when clean; coordination only</span></td></tr>"])
(defn render [db]
  (let [ledger (vec (store/ledger db))
        sites (->> (store/all-sites db) (sort-by :site-id))
        srow (fn [s] (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
                             (esc (:site-id s)) (esc (or (:name s) "-"))
                             (esc (name (or (:method s) :-)))
                             (status-cell ledger (:site-id s))))
        srows (str/join "\n" (map srow sites))
        lrows (str/join "\n" (map ledger-row ledger))]
    (str "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0893</title>"
     "<style>body{font:14px/1.5 sans-serif;margin:0;color:#1a1a1a;background:#f5f5f5}"
     ".bar{background:#1a2a3a;color:#fff;padding:1.2rem 2rem}.bar h1{margin:0;font-size:1.15rem}"
     "main{max-width:980px;margin:1.5rem auto;padding:0 1rem}"
     ".card{background:#fff;border-radius:8px;padding:1.2rem 1.4rem;margin-bottom:1.2rem;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
     ".muted{color:#777;font-size:.82rem}table{border-collapse:collapse;width:100%;font-size:.85rem}"
     "th,td{text-align:left;padding:.42rem .5rem;border-bottom:1px solid #eee}th{font-weight:600;color:#555}"
     ".ok{color:#0a7d33}.warn{color:#9a6700}.critical{color:#b41010;font-weight:600}"
     "code{background:#f0f0f0;padding:.1rem .3rem;border-radius:3px;font-size:.8rem}</style></head><body>"
     "<header class=\"bar\"><h1>Salt extraction ops (ISIC 0893) — <code>saltops</code></h1></header><main>"
     "<section class=\"card\"><h2>Salt sites</h2>"
     "<p class=\"muted\">Demo from <code>saltops.store</code> via <code>saltops.render-html</code>. No invented data.</p>"
     "<table><thead><tr><th>Site</th><th>Name</th><th>Method</th><th>Last op</th></tr></thead><tbody>" srows "</tbody></table></section>"
     "<section class=\"card\"><h2>Action gate</h2>"
     "<table><thead><tr><th>Op</th><th>Gate</th></tr></thead><tbody>" (str/join "\n" gate-rows) "</tbody></table></section>"
     "<section class=\"card\"><h2>Audit ledger</h2>"
     "<table><thead><tr><th>Fact</th><th>Op</th><th>Site</th><th>Basis</th></tr></thead><tbody>" lrows "</tbody></table></section>"
     "</main></body></html>")))
(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!) f (java.io.File. out)]
    (.. f getParentFile mkdirs) (spit f (render db))
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
