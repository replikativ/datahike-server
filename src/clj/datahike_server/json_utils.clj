(ns datahike-server.json-utils
  (:require [clojure.walk :refer [prewalk]]
            [datahike.datom]  ;; TODO temporary workaround for requiring schema: remove
            [datahike.schema :as s]))

(def edn-fmt "application/edn")
(def json-fmt "application/json")

(defn- filter-value-type-attrs [valtypes schema]
  (into #{} (filter #(-> % schema :db/valueType valtypes) (keys schema))))

(def ^:private filter-kw-attrs
  (partial filter-value-type-attrs #{:db.type/keyword :db.type/value :db.type/cardinality :db.type/unique}))

(def keyword-valued-schema-attrs (filter-kw-attrs s/implicit-schema-spec))

(defn- kw-first-if-lookup-ref [v]
  (if (and (vector? v) (string? (first v)))
    (update v 0 keyword)
    v))

; Does Datahike allow null values???
(defn- int-obj-to-long [i]
    (if (some? i) (.longValue (java.lang.Integer. i)) i))

(defn- xf-val [f v]
  (if (vector? v) (map f v) (f v)))

(defn- xf-ref-val [v]
  (if (vector? v) (prewalk kw-first-if-lookup-ref v) v))

(defn- cond-xf-val [ref-attrs long-attrs kw-attrs sym-attrs a v]
  (cond
    (contains? ref-attrs a) (xf-ref-val v)
    (contains? long-attrs a) (xf-val int-obj-to-long v)
    (contains? kw-attrs a) (xf-val keyword v)
    (contains? sym-attrs a) (xf-val symbol v)
    :else v))

(defn xf-tx-data-map [ref-attrs long-attrs kw-attrs sym-attrs m]
  (into {}
        (map (fn [[a v]] [a (if (= :db/id a)
                              (kw-first-if-lookup-ref v)
                              (cond-xf-val ref-attrs long-attrs kw-attrs sym-attrs a v))])
             m)))

(defn xf-tx-data-vec
  ([ref-attrs long-attrs kw-attrs sym-attrs tx-vec]
   (xf-tx-data-vec ref-attrs long-attrs kw-attrs sym-attrs tx-vec true))
  ([ref-attrs long-attrs kw-attrs sym-attrs tx-vec op?]
   (let [op (when op? (first tx-vec))
         [e a v] (if op? (rest tx-vec) tx-vec)
         a (if (string? a) (keyword a) a)]
     (vec (filter some? (list (keyword op)
                              (kw-first-if-lookup-ref e)
                              a
                              (cond-xf-val ref-attrs long-attrs kw-attrs sym-attrs a v)))))))

(defn xf-data-for-tx [tx-data conn]
  (let [conn-schema (:schema @conn)
        ref-valued-attrs (into #{} (filter-value-type-attrs #{:db.type/ref} conn-schema))
        long-valued-attrs (into #{} (filter-value-type-attrs #{:db.type/long} conn-schema))
        kw-valued-attrs (clojure.set/union keyword-valued-schema-attrs (filter-kw-attrs conn-schema))
        sym-valued-attrs (into #{} (filter-value-type-attrs #{:db.type/symbol} conn-schema))]
    (map #(cond
            (map? %) (xf-tx-data-map ref-valued-attrs long-valued-attrs kw-valued-attrs sym-valued-attrs %)
            (vector? %) (xf-tx-data-vec ref-valued-attrs long-valued-attrs kw-valued-attrs sym-valued-attrs %)
            :else (throw (ex-info "Only maps and vectors allowed in :tx-data and :tx-meta"
                                  {:event :handlers/transact :data tx-data})))
         tx-data)))
