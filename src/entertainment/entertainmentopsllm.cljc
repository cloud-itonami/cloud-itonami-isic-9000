(ns entertainment.entertainmentopsllm
  "EntertainmentOps-LLM client -- the *contained intelligence node* for
  the creative/arts/entertainment actor.

  It normalizes production intake, drafts a per-jurisdiction
  copyright/rights-clearance evidence checklist, screens productions
  for an unresolved rights-clearance flag, and drafts the production-
  release action. CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited), never
  a committed record or a real public release. Every output is
  censored downstream by `entertainment.governor` before anything
  touches the SSoT, and `:production/release` proposals NEVER auto-
  commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/release-production | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [entertainment.facts :as facts]
            [entertainment.registry :as registry]
            [entertainment.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the production, release channel or jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "作品記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :production/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction copyright/rights-clearance evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `entertainment.facts` -- the Content and Booking Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [p (store/production db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction p))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "entertainment.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-rights
  "Rights-clearance screening draft. `:rights-clearance-resolved?` on
  the production record injects the failure mode: the Content and
  Booking Governor must HOLD, un-overridably, on any unresolved
  rights-clearance flag."
  [db {:keys [subject]}]
  (let [p (store/production db subject)]
    (cond
      (nil? p)
      {:summary "対象作品が見つかりません" :rationale "no production record"
       :cites [] :effect :rights-screening/set :value {:production-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (false? (:rights-clearance-resolved? p))
      {:summary    (str (:production-title p) ": 未解決の権利処理フラグを検出")
       :rationale  "スクリーニングが未解決の権利処理フラグを検出。人手確認とホールドが必須。"
       :cites      [:rights-check]
       :effect     :rights-screening/set
       :value      {:production-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:production-title p) ": 権利処理フラグ解決済み")
       :rationale  "権利処理スクリーニング完了。"
       :cites      [:rights-check]
       :effect     :rights-screening/set
       :value      {:production-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-production-release
  "Draft the actual PRODUCTION-RELEASE action -- releasing or
  publishing a real production to the public. ALWAYS `:stake
  :actuation/release-production` -- this is a REAL-WORLD, public-
  facing act, never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`entertainment.phase`); the governor also always escalates on
  `:actuation/release-production`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [p (store/production db subject)
        restricted? (and p (registry/release-channel-restricted? p))]
    {:summary    (str subject " 向け公開提案"
                      (when p (str " (production=" (:production-title p) ")")))
     :rationale  (if p
                   (str "proposed-release-channel=" (:proposed-release-channel p)
                        " restricted-channels=" (:restricted-channels p))
                   "作品が見つかりません")
     :cites      (if p [subject] [])
     :effect     :production/mark-released
     :value      {:production-id subject}
     :stake      :actuation/release-production
     :confidence (if restricted? 0.3 0.9)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :production/intake      (normalize-intake db request)
    :jurisdiction/assess         (assess-jurisdiction db request)
    :rights/screen                   (screen-rights db request)
    :production/release                  (propose-production-release db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは劇場/制作会社の作品公開エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:production/upsert|:assessment/set|:rights-screening/set|"
       ":production/mark-released) "
       ":stake(:actuation/release-production か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess  {:production (store/production st subject)}
    :rights/screen        {:production (store/production st subject)}
    :production/release   {:production (store/production st subject)}
    {:production (store/production st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Content and Booking
  Governor escalates/holds -- an LLM hiccup can never auto-release a
  production."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :entertainmentopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
