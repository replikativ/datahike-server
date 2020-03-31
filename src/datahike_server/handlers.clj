(ns datahike-server.handlers
  (:require [datahike-server.database :refer [conn]]
            [datahike.api :as d]
            [datahike.core :as c]))

(defn success [data]
  {:status 200
   :body data})

(defn transact [{{{:keys [tx-data tx-meta]} :body} :parameters}]
  (let [result (d/transact conn {:tx-data tx-data
                                 :tx-meta tx-meta})]
    (-> result
        (dissoc :db-after :db-before)
        (update :tx-data #(mapv (comp vec seq) %))
        (update :tx-meta #(mapv (comp vec seq) %))
        success)))

(defn q [{{:keys [body]} :parameters}]
  (success (into []
                 (d/q {:query (:query body [])
                      :args (concat [@conn] (:args body []))
                      :limit (:limit body -1)
                      :offset (:offset body 0)}))))

(defn pull [{{{:keys [selector eid]} :body} :parameters}]
  (success (d/pull @conn selector eid)))

(defn pull-many [{{{:keys [selector eids]} :body} :parameters}]
  (success (vec (d/pull-many @conn selector eids))))

(defn datoms [{{{:keys [index components]} :body} :parameters}]
  (success (mapv (comp vec seq) (apply d/datoms (into [@conn index] components)))))

(defn seek-datoms [{{{:keys [index components]} :body} :parameters}]
  (success (mapv (comp vec seq) (apply d/seek-datoms (into [@conn index] components)))))

(defn tempid []
  (success (d/tempid :db.part/db)))

(defn entity [{{{:keys [eid]} :body} :parameters}]
  (success (->> (d/entity @conn eid)
                c/touch
                (into {}))))


