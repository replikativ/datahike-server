(ns datahike-server.handlers
  (:require [datahike-server.database :refer [database]]
            [konserve.core :as k]
            [konserve.filestore :refer [list-keys]]
            [clojure.core.async :refer [<!!]]
            [datahike.api :as d]
            [datahike.db :as dd]
            [datahike.core :as c])
  (:import [java.util UUID]))

(defn success
  ([data] {:status 200 :body data})
  ([] {:status 200}))

(defn connect [{{:keys [id]} :path-params}]
  (let [config (<!! (k/get-in (:store @database) [:configurations id]))]
    (swap! database assoc-in [:connections id] (d/connect config))
    (success)))

(defn create-database [{{config :body} :parameters}]
  (let [id (str (UUID/randomUUID))
        initial-tx [{:db/ident :user/query
                     :db/valueType :db.type/string
                     :db/cardinality :db.cardinality/one}]]
    (<!! (k/assoc-in (:store @database) [:configurations id] config))
    (d/create-database (update config :initial-tx #(if (nil? %)
                                                     initial-tx
                                                     (concat % initial-tx))))
    (success {:id id})))

(defn delete-database [{{{:keys [id]} :path} :parameters}]
  (let [config (<!! (k/get-in (:store @database) [:configurations id]))]
    (d/delete-database config)
    (<!! (k/update-in (:store @database) [:configurations] #(dissoc % id)))
    (success {:id id})))

(defn list-databases [_]
  (let [databases (for [[id config] (<!! (k/get-in (:store @database) [:configurations]))]
                    (assoc config :id id))]
    (success {:databases (vec databases)})))

(defn list-connections [_]
  (let [conns (for [[id conn] (:connections @database [])]
                [id (:config @conn)])]
    (success {:connections conns})))

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

(defn entity [{{{:keys [eid]} :body} :parameters conn :conn}]
  (success (->> (d/entity @conn eid)
                c/touch
                (into {}))))

(defn schema [{:keys [conn]}]
  (success (dd/-schema @conn)))

(defn save-query [{{{:keys [query]} :body} :parameters conn :conn}]
  (-> (d/transact conn [{:user/query (str query)}])
      cleanup-result
      success))

(defn load-queries [{:keys [conn]}]
  (success {:queries (d/q '[:find (pull ?e [:user/query :db/id]) :where [?e :user/query _]] @conn)}))

(defn delete-query [{{{:keys [id]} :path} :parameters conn :conn}]
  (-> (d/transact conn [[:db/retractEntity id]])
      cleanup-result
      success))
