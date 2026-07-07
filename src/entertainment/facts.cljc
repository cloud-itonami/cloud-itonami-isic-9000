(ns entertainment.facts
  "Per-jurisdiction creative/rights-clearance regulatory catalog -- the
  G2-style spec-basis table the Content and Booking Governor checks
  every jurisdiction/assess proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's copyright/rights-
  clearance requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official copyright/
  rights-administration body (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a real
  source, done -- never invent a jurisdiction's requirements to make
  coverage look bigger.

  The DEU entry cites GEMA (the German music-rights collecting
  society) rather than a general copyright office, since music-
  licensing clearance -- the concern this actor's `rights-clearance-
  unresolved-violations` check models -- runs through GEMA in
  practice, not through the German Patent and Trademark Office
  (DPMA, which handles patents/trademarks, not copyright registration
  -- German copyright itself is unregistered under UrhG). An honest,
  domain-accurate citation rather than a generic 'the IP office'
  placeholder.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  rights-clearance/licensing-agreement/content-rating/distribution-
  agreement evidence set submitted in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "文化庁 (Agency for Cultural Affairs)"
          :legal-basis "著作権法 (Copyright Act)"
          :national-spec "著作物の公表・利用許諾に関する規定"
          :provenance "https://www.bunka.go.jp/seisaku/chosakuken/"
          :required-evidence ["権利処理記録 (rights-clearance documentation)"
                              "利用許諾契約書 (licensing agreement)"
                              "レイティング/分類記録 (content-rating/classification record)"
                              "配給契約記録 (distribution-agreement record)"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Copyright Office"
          :legal-basis "Copyright Act (Title 17, U.S. Code)"
          :national-spec "Copyright registration and public-performance/distribution rights"
          :provenance "https://www.copyright.gov/"
          :required-evidence ["Rights-clearance documentation"
                              "Licensing agreement"
                              "Content-rating/classification record"
                              "Distribution-agreement record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "UK Intellectual Property Office (IPO)"
          :legal-basis "Copyright, Designs and Patents Act 1988"
          :national-spec "Copyright/performing-rights clearance for public release"
          :provenance "https://www.gov.uk/government/organisations/intellectual-property-office"
          :required-evidence ["Rights-clearance documentation"
                              "Licensing agreement"
                              "Content-rating/classification record"
                              "Distribution-agreement record"]}
   "DEU" {:name "Germany"
          :owner-authority "GEMA (Gesellschaft für musikalische Aufführungs- und mechanische Vervielfältigungsrechte)"
          :legal-basis "Urheberrechtsgesetz (UrhG) + GEMA-Berechtigungsvertrag"
          :national-spec "Musiknutzungs- und Aufführungsrechte-Klärung"
          :provenance "https://www.gema.de/"
          :required-evidence ["Rechteklärungsnachweis (rights-clearance documentation)"
                              "Lizenzvereinbarung (licensing agreement)"
                              "Alterskennzeichnung/Klassifizierungsnachweis (content-rating/classification record)"
                              "Vertriebsvereinbarung (distribution-agreement record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to release a
  production on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9000 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `entertainment.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
