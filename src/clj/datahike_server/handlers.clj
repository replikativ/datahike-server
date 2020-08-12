(ns datahike-server.handlers
  (:require [datahike-server.database :refer [conns]]
            [datahike.api :as d]
            [datahike.db :as dd]
            [datahike.core :as c]))

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
  (success {:tx (dd/-max-tx @conn)}))

(defn cleanup-result [result]
  (-> result
      (dissoc :db-after :db-before)
      (update :tx-data #(mapv (comp vec seq) %))
      (update :tx-meta #(mapv (comp vec seq) %))))

(defn transact [{{{:keys [tx-data tx-meta]} :body} :parameters conn :conn}]
  (let [result (d/transact conn {:tx-data tx-data
                                 :tx-meta tx-meta})]

    (-> result
        cleanup-result
        success)))

(defn q [{{:keys [body]} :parameters conn :conn}]
  (success (into []
                 (d/q {:query (:query body [])
                       :args (concat [@conn] (:args body []))
                       :limit (:limit body -1)
                       :offset (:offset body 0)}))))

(defn pull [{{{:keys [selector eid]} :body} :parameters conn :conn}]
  (success (d/pull @conn selector eid)))

(defn pull-many [{{{:keys [selector eids]} :body} :parameters conn :conn}]
  (success (vec (d/pull-many @conn selector eids))))

(defn datoms [{{{:keys [index components]} :body} :parameters conn :conn}]
  (success (mapv (comp vec seq) (apply d/datoms (into [@conn index] components)))))

(defn seek-datoms [{{{:keys [index components]} :body} :parameters conn :conn}]
  (success (mapv (comp vec seq) (apply d/seek-datoms (into [@conn index] components)))))

(defn tempid [_]
  (success {:tempid (d/tempid :db.part/db)}))

(defn entity [{{{:keys [eid attr]} :body} :parameters conn :conn}]
  (if attr
    (success (get (d/entity @conn eid) attr))
    (success (->> (d/entity @conn eid)
                  c/touch
                  (into {})))))

(defn schema [{:keys [conn]}]
  (success (dd/-schema @conn)))
