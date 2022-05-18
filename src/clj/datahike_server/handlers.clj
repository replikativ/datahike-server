(ns datahike-server.handlers
  (:require [datahike-server.database :refer [conns]]
            [datahike.api :as d]
            [datahike.core :as c]
            [taoensso.timbre :as log]))

(defn success
  ([data] {:status 200 :body data})
  ([] {:status 200}))

(defn list-databases [_]
  (let [xf (comp
            (map deref)
            (map :config))
        databases (into [] xf (-> conns vals))]
    (success {:databases databases})))

(defn get-db [{:keys [conn]}]
  (success (select-keys @conn [:meta :config :max-eid :max-tx :hash])))

(defn cleanup-result [result]
  (-> result
      (dissoc :db-after :db-before)
      (update :tx-data #(mapv (comp vec seq) %))
      (update :tx-meta #(mapv (comp vec seq) %))))

(defn transact [{{{:keys [tx-data tx-meta]} :body} :parameters conn :conn}]
  (let [start (System/currentTimeMillis)
        args {:tx-data tx-data
              :tx-meta tx-meta}
        result (d/transact conn args)]
    (log/info "Transacting with arguments: " args)
    (-> result
        cleanup-result
        success)))

(defn q [{{:keys [body]} :parameters conn :conn db :db}]
  (let [args {:query (:query body [])
              :args (concat [(or db @conn)] (:args body []))
              :limit (:limit body -1)
              :offset (:offset body 0)}]
    (log/info "Querying with arguments: " (str args))
    (success (into [] (d/q args)))))

(defn pull [{{{:keys [selector eid]} :body} :parameters conn :conn db :db}]
  (success (d/pull (or db @conn) selector eid)))

(defn pull-many [{{{:keys [selector eids]} :body} :parameters conn :conn db :db}]
  (success (vec (d/pull-many (or db @conn) selector eids))))

(defn datoms [{{{:keys [index components]} :body} :parameters conn :conn db :db}]
  (success (mapv (comp vec seq) (apply d/datoms (into [(or db @conn) index] components)))))

(defn seek-datoms [{{{:keys [index components]} :body} :parameters conn :conn db :db}]
  (success (mapv (comp vec seq) (apply d/seek-datoms (into [(or db @conn) index] components)))))

(defn tempid [_]
  (success {:tempid (d/tempid :db.part/db)}))

(defn entity [{{{:keys [eid attr]} :body} :parameters conn :conn db :db}]
  (let [db (or db @conn)]
    (if attr
      (success (get (d/entity db eid) attr))
      (success (->> (d/entity db eid)
                    c/touch
                    (into {}))))))

(defn schema [{:keys [conn]}]
  (success (d/schema @conn)))

(defn reverse-schema [{:keys [conn]}]
  (success (d/reverse-schema @conn)))

(defn index-range [{{{:keys [attrid start end]} :body} :parameters conn :conn db :db}]
  (let [db (or db @conn)]
    (success (d/index-range db {:attrid attrid
                                :start  start
                                :end    end}))))

(defn load-entities [{{{:keys [entities]} :body} :parameters conn :conn}]
  (-> @(d/load-entities conn entities)
      cleanup-result
      success))

