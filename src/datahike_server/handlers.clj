(ns datahike-server.handlers
  (:require [datahike-server.database :refer [conn]]
            [datahike.api :as d]))

(defn transact [{{:keys [body]} :parameters}]
  (let [result (d/transact conn {:tx-data (:tx-data body [])
                                           :tx-meta (:tx-meta body [])})]
    {:status 200
     :body (-> result
               (dissoc :db-after :db-before)
               (update :tx-data #(map seq %))
               (update :tx-meta #(map seq %)))}))

(defn q [{{:keys [body]} :parameters}]
  {:status 200
   :body (d/q {:query (:query body [])
               :args (concat [@conn] (:args body []))
               :limit (:limit body -1)
               :offset (:offset body 0)})})


