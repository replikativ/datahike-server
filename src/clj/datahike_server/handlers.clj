(ns datahike-server.handlers
  (:require [datahike-server.database :refer [conns]]
            [datahike.api :as d]
            [datahike.core :as c]
            [datahike-server.json-utils :as ju]
            [taoensso.timbre :as log]
            [clojure.walk :as walk]))

(defn success
  ([] {:status 200})
  ([data] {:status 200 :body data}))

(defn list-databases [_]
  (let [xf (comp
            (map deref)
            (map :config))
        databases (into [] xf (-> conns vals))]
    (success {:databases databases})))

(defn get-db [{:keys [conn]}]
  (success (select-keys @conn [:meta :config :max-eid :max-tx :hash])))

(defn- cleanup-result [result]
  (-> result
      (dissoc :db-after :db-before)
      (update :tx-data #(mapv (comp vec seq) %))
      (update :tx-meta #(mapv (comp vec seq) %))))

(defn transact [{:keys [conn content-type] {{:keys [tx-data tx-meta]} :body} :parameters}]
  (let [args {:tx-data (if (= content-type ju/json-fmt) (ju/xf-data-for-tx tx-data @conn) tx-data)
              :tx-meta (if (= content-type ju/json-fmt) (ju/xf-data-for-tx tx-meta @conn) tx-meta)}
        start (System/currentTimeMillis)
        _ (log/info "Transacting with arguments: " args)
        result (d/transact conn args)]
    (-> result
        cleanup-result
        success)))

(defn q [{{:keys [body]} :parameters :keys [conn db content-type] :as req-arg}]
  (let [args {:query (cond-> (:query body [])
                       (= content-type ju/json-fmt) ju/clojurize)
              :args (concat [(or db @conn)] (cond-> (:args body [])
                                              (= content-type ju/json-fmt) ju/clojurize))
              :limit  (:limit body -1)
              :offset (:offset body 0)}]
    (log/info "Querying with arguments: " (str args))
    (success (d/q args))))

(defn pull [{:keys [conn db content-type] {{:keys [selector eid]} :body} :parameters}]
  (let [result (if (= content-type ju/json-fmt)
                 (d/pull (or db @conn) (ju/clojurize selector) (ju/clojurize eid))
                 (d/pull (or db @conn) selector eid))]
    (success result)))

(defn pull-many [{:keys [conn db content-type] {{:keys [selector eids]} :body} :parameters}]
  (let [result (if (= content-type ju/json-fmt)
                 (d/pull-many (or db @conn) (ju/clojurize selector) (ju/clojurize eids))
                 (d/pull-many (or db @conn) selector eids))]
    (success result)))

(defn datoms [{:keys [conn db content-type] {{:keys [index] :as body} :body} :parameters}]
  (let [db (or db @conn)
        arg-map (if (= content-type ju/json-fmt)
                  (-> (update body :index keyword)
                      (update :components #(ju/xf-datoms-components (name index) % db)))
                  body)]
    (success (mapv (comp vec seq) (d/datoms db arg-map)))))

(defn seek-datoms [{:keys [conn db content-type] {{:keys [index] :as body} :body} :parameters}]
  (let [db (or db @conn)
        arg-map (if (= content-type ju/json-fmt)
                  (-> (update body :index keyword)
                      (update :components #(ju/xf-datoms-components (name index) % db)))
                  body)]
    (success (mapv (comp vec seq) (d/seek-datoms db arg-map)))))

(defn tempid [_]
  (success {:tempid (d/tempid :db.part/db)}))

(defn entity [{:keys [conn db content-type] {{:keys [eid attr]} :body} :parameters}]
  (let [db (or db @conn)
        e (if (= content-type ju/json-fmt)
            (ju/handle-id-or-av-pair eid (ju/get-valtype-attrs-map (.-schema db)) db)
            eid)
        entity (d/entity db e)]
    (and entity (if attr
                  (success (get entity (keyword attr)))
                  (success (into {} (c/touch entity)))))))

(defn schema [{:keys [conn]}]
  (success (d/schema @conn)))

(defn reverse-schema [{:keys [conn]}]
  (success (d/reverse-schema @conn)))

(defn index-range [{:keys [conn db content-type] {{:keys [attrid start end] :as body} :body} :parameters}]
  (let [db (or db @conn)
        a (ju/keywordize-string attrid)
        a-ident (ju/ident-for db a)
        valtype-attrs-map (ju/get-valtype-attrs-map (.-schema db))
        arg-map (if (= content-type ju/json-fmt)
                  (-> (assoc body :attrid a)
                      (update :start #(ju/cond-xf-val a-ident % valtype-attrs-map db))
                      (update :end #(ju/cond-xf-val a-ident % valtype-attrs-map db)))
                  body)]
    (success (mapv (comp vec seq) (d/index-range db arg-map)))))

(defn load-entities [{:keys [conn content-type] {{:keys [entities]} :body} :parameters}]
  (let [valtype-attrs-map (ju/get-valtype-attrs-map (.-schema @conn))
        entities (if (= content-type ju/json-fmt)
                   (map (fn [d]
                          (if (= (:schema-flexibility (:config @conn)) :write)
                            (let [a (ju/keywordize-string (nth d 1))]
                              (-> (assoc d 1 a)
                                  (update 2 #(ju/cond-xf-val (ju/ident-for @conn a) % valtype-attrs-map @conn))))
                            (-> (update d 1 ju/clojurize)
                                (update 2 ju/clojurize))))
                        entities)
                   entities)]
    (-> @(d/load-entities conn entities)
        cleanup-result
        success)))
