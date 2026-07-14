(ns saltops.governor
  "SaltExtractionGovernor -- the independent compliance layer that earns
  the SaltOpsAdvisor the right to commit. The advisor has no notion of
  whether a site is actually registered and verified, whether its own
  proposed `:effect` secretly claims a direct actuation instead of a
  mere proposal, or whether it has silently drifted into a permanently
  out-of-scope decision area, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- OPERATIONS COORDINATION
  only (production-record logging, maintenance scheduling, safety-
  concern flagging, outbound-shipment coordination) -- across BOTH
  ISIC-0893 extraction-method families (rock-salt dry/underground
  mining and solution mining/evaporation). It NEVER performs or
  authorizes:
    - direct extraction-equipment control (continuous-miner operation,
      drill-and-blast sequencing, room-and-pillar excavation
      sequencing -- rock-salt mining -- or brine injection/extraction
      well-pump control, cavern-pressure control, evaporation-pond
      gate/valve control, harvester operation -- solution mining /
      evaporation. Both method families' equipment-control territory is
      the SAME excluded decision territory, not a carve-out for either
      method)
    - site-safety decisions (subsidence-control determinations, brine-
      containment-system overrides, cavern-integrity control decisions)
    - site-safety-authority decisions (permit issuance, license
      suspension, compliance enforcement)

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Site unverified            -- the target site record must exist
                                      AND be independently confirmed
                                      `:registered?`/`:verified?` in the
                                      store before ANY proposal for it
                                      may commit or even escalate. Never
                                      trusts a proposal's own claim
                                      about the site -- re-derived from
                                      the site's own store record, the
                                      same 'ground truth, not
                                      self-report' discipline every
                                      sibling actor's governor uses.
    2. Effect not :propose        -- every proposal's `:effect` MUST
                                      be `:propose`. Any other effect
                                      value is, by construction, a
                                      claim to directly actuate/commit
                                      outside governance -- HARD block,
                                      not merely low-confidence.
    3. Scope exclusion            -- ANY proposal (regardless of op)
                                      whose op, rationale, summary,
                                      citations or draft value touches
                                      extraction-equipment-control/
                                      site-safety(-authority) decision
                                      territory is a HARD, PERMANENT
                                      block -- this actor's charter
                                      excludes that territory
                                      structurally, not as a rollout
                                      milestone. Evaluated
                                      UNCONDITIONALLY on every
                                      proposal, the same 'exercise the
                                      failure mode directly' discipline
                                      every sibling actor's own
                                      unconditional-evaluation checks
                                      establish. An op outside the
                                      closed four-op allowlist is the
                                      SAME failure mode (an advisor
                                      proposing something it was never
                                      authorized to propose) and is
                                      folded into this same check.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is `:flag-safety-concern` -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `saltops.phase` independently agrees: `:flag-safety-concern` is never
  a member of any phase's `:auto` set either -- two layers, not one."
  (:require [clojure.string :as str]
            [saltops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-production-record :schedule-maintenance
    :flag-safety-concern :coordinate-shipment})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- direct extraction-equipment
  control (either method family) or site-safety(-authority) decisions.
  Scanned across the proposal's op/summary/rationale/cites/value, never
  trusting the advisor's own framing of its intent."
  ["blast" "発破"
   "drill-and-blast" "ドリルアンドブラスト"
   "drilling pattern" "drilling-pattern" "ドリリングパターン"
   "room-and-pillar" "ルームアンドピラー"
   "continuous miner" "continuous-miner" "連続採掘機"
   "excavation sequenc" "excavation-sequenc" "掘進順序" "採掘順序"
   "brine pump control" "brine-pump control" "ブラインポンプ制御"
   "injection well control" "injection-well control" "圧入井制御"
   "injection well pump control" "圧入井ポンプ制御"
   "extraction well control" "extraction-well control" "採取井制御"
   "cavern pressure control" "cavern-pressure control" "空洞圧力制御"
   "cavern integrity control" "cavern-integrity control" "空洞健全性制御"
   "pond gate control" "pond-gate control" "蒸発池ゲート制御"
   "pond valve control" "pond-valve control" "蒸発池バルブ制御"
   "harvester operation" "harvester-operation" "収穫機操作"
   "subsidence control decision" "subsidence-control decision" "地盤沈下制御決定"
   "brine containment control" "brine-containment control" "ブライン封じ込め制御"
   "permit issuance" "permit-issuance" "許可発行"
   "license suspension" "license-suspension" "免許停止"
   "compliance enforcement" "compliance-enforcement" "コンプライアンス執行"])

;; ----------------------------- checks -----------------------------

(defn- site-unverified-violations
  "The target site must exist AND be independently `:registered?`/
  `:verified?` in the store -- never trust the proposal's own
  `:site-id` claim without a store lookup."
  [{:keys [site-id]} st]
  (let [s (store/site st site-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :site-unverified
        :detail (str site-id " は未登録または未検証のsite -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches extraction-equipment-control/site-
  safety(-authority) territory, regardless of confidence or how clean
  every other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "抽出設備制御(発破/掘進順序/ブライン井戸・蒸発池制御等)またはサイト安全(機関)の判断領域に触れる提案は永久に禁止"}])))

(defn check
  "Censors a SaltOpsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [site-id (or (:site-id proposal) (:site-id request))
        hard (into []
                   (concat (site-unverified-violations {:site-id site-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
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
   :site-id    (:site-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
