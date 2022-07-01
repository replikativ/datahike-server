(ns datahike-server.json-utils
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [datahike.schema :as s]))

(def edn-fmt "application/edn")
(def json-fmt "application/json")
(def number-re #"\d+(\.\d+)?")
(def number-format-instance (java.text.NumberFormat/getInstance))

(defn- filter-value-type-attrs [valtypes schema]
  (into #{} (filter #(-> % schema :db/valueType valtypes) (keys schema))))

(def ^:private filter-kw-attrs
  (partial filter-value-type-attrs #{:db.type/keyword :db.type/value :db.type/cardinality :db.type/unique}))

(def keyword-valued-schema-attrs (filter-kw-attrs s/implicit-schema-spec))

(defn- int-obj-to-long [i]
  (if (some? i)
    (.longValue (java.lang.Integer. i))
    (throw (ex-info "Cannot store nil as a value"))))

(defn- xf-val [f v]
  (if (vector? v) (map f v) (f v)))

(declare handle-id-or-av-pair)

(defn- xf-ref-val [v valtype-attrs-map db]
  (if (vector? v)
    (walk/prewalk #(handle-id-or-av-pair % valtype-attrs-map db) v)
    v))

(defn keywordize-string [s]
  (if (string? s) (keyword s) s))

(defn ident-for [db a]
  (if (and (number? a) (some? db)) (.-ident-for db a) a))

(defn cond-xf-val
  [a-ident v {:keys [ref-attrs long-attrs keyword-attrs symbol-attrs] :as valtype-attrs-map} db]
  (cond
    (contains? ref-attrs a-ident) (xf-ref-val v valtype-attrs-map db)
    (contains? long-attrs a-ident) (xf-val int-obj-to-long v)
    (contains? keyword-attrs a-ident) (xf-val keyword v)
    (contains? symbol-attrs a-ident) (xf-val symbol v)
    :else v))

(defn handle-id-or-av-pair
  ([v valtype-attrs-map]
   (handle-id-or-av-pair v valtype-attrs-map nil))
  ([v valtype-attrs-map db]
   (if (and (vector? v) (= (count v) 2))
     (let [a (keywordize-string (first v))]
       [a (cond-xf-val (ident-for db a) (nth v 1) valtype-attrs-map db)])
     v)))

(defn- xf-tx-data-map [m valtype-attrs-map db]
  (into {}
        (map (fn [[a v]]
               [a (if (= :db/id a)
                    (handle-id-or-av-pair [a v] valtype-attrs-map db)
                    (cond-xf-val a v valtype-attrs-map db))])
             m)))

(defn- xf-tx-data-vec [tx-vec valtype-attrs-map db]
  (let [op (first tx-vec)
        [e a v] (rest tx-vec)
        a (keywordize-string a)]
    (vec (filter some? (list (keyword op)
                             (handle-id-or-av-pair e valtype-attrs-map db)
                             a
                             (cond-xf-val (ident-for db a) v valtype-attrs-map db))))))

(defn get-valtype-attrs-map [schema]
  (let [ref-valued-attrs (filter-value-type-attrs #{:db.type/ref} schema)
        long-valued-attrs (filter-value-type-attrs #{:db.type/long} schema)
        kw-valued-attrs (clojure.set/union keyword-valued-schema-attrs (filter-kw-attrs schema))
        sym-valued-attrs (filter-value-type-attrs #{:db.type/symbol} schema)]
    {:ref-attrs ref-valued-attrs
     :long-attrs long-valued-attrs
     :keyword-attrs kw-valued-attrs
     :symbol-attrs sym-valued-attrs}))

(defn xf-data-for-tx [tx-data db]
  (let [valtype-attrs-map (get-valtype-attrs-map (:schema db))]
    (map #(let [xf-fn (cond (map? %) xf-tx-data-map
                            (vector? %) xf-tx-data-vec
                            ; Q: Is this error appropriate?
                            :else (throw (ex-info "Only maps and vectors allowed in :tx-data and :tx-meta"
                                                  {:event :handlers/transact :data tx-data})))]
            (xf-fn % valtype-attrs-map db))
         tx-data)))

(defn- component-index [c index components]
  (let [c-index (string/index-of index c)]
    (if (> (count components) c-index) c-index nil)))

(defn xf-datoms-components [index components db]
  (let [e-index (component-index \e index components)
        a-index (component-index \a index components)
        valtype-attrs-map (when (or (> (count components) 2) (some? e-index))
                            (get-valtype-attrs-map (:schema db)))
        a (when (some? a-index) (keywordize-string (nth components a-index)))]
    (cond-> components
      (some? e-index) (update e-index #(handle-id-or-av-pair % valtype-attrs-map db))
      (some? a-index) (assoc a-index a)
      (> (count components) 2) (update 2 #(cond-xf-val (ident-for db a) % valtype-attrs-map db)))))

(defn- strip [s]
  (subs s 1 (- (count s) 1)))

(defn first-char-str [s] (subs s 0 1))

(defn- first-char-is? [s cstr] (= cstr (first-char-str s)))

(defn- last-char-str [s] (subs s (- (count s) 1) (count s)))

(defn- last-char-is? [s cstr] (= cstr (last-char-str s)))

(defn- symbol-or-string [c1 s]
  (if (and (> (count s) 1) (= c1 (subs s 1 2)))
    (subs s 1)
    (symbol s)))

(defn- handle-non-singleton-char [s char-str f]
  (if (> (count s) 1)
    (cond-> (subs s 1)
      (not= char-str (subs s 1 2)) f)
    (throw (ex-info (str "Illegal use of \"" char-str "\" as " s)))))

(defn- clojurize-string [s]
  (let [first-char (subs s 0 1)]
    (case first-char
      ":" (handle-non-singleton-char s ":" keyword)
      "?" (symbol-or-string "?" s)
      "$" (symbol-or-string "$" s)
      "." (if (#{"." "..."} s) (symbol s) s)
      ("+" "-" "*" "/" "_" "%") (if (= (count s) 1)
                                  (symbol s)
                                  s) ; Q: Or should this be escaped by doubling, for consistency with other special chars?
      "&" (handle-non-singleton-char s "&" symbol)
      "n" (if (= s "nil") nil s)
      s)))

(defn clojurize [c]
  (walk/prewalk #(cond
                   (string? %) (clojurize-string %)
                   (sequential? %) (if (= "!list" (first %)) (apply list (rest %)) %)
                   :else %)
                c))

(defn clojurize-coll-str [s]
  (into []
        (comp (map #(if (and (first-char-is? % "\"") (last-char-is? % "\""))
                      (strip %)
                      %))
              (map #(if (re-matches number-re %)
                      (.parse number-format-instance %)
                      %)))
        (string/split (strip s) #" ")))
