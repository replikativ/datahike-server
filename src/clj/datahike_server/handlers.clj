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
  (success {:hash (hash @conn)}))

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

(defn q [{{:keys [body]} :parameters conn :conn db :db}]
  (success (into []
                 (d/q {:query (:query body [])
                       :args (concat [(or db @conn)] (:args body []))
                       :limit (:limit body -1)
                       :offset (:offset body 0)}))))

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

(comment

  (def schema [{:db/ident       :name
                :db/cardinality :db.cardinality/one
                :db/index       true
                :db/unique      :db.unique/identity
                :db/valueType   :db.type/string}
               {:db/ident       :parents
                :db/cardinality :db.cardinality/many
                :db/valueType   :db.type/ref}
               {:db/ident       :age
                :db/cardinality :db.cardinality/one
                :db/valueType   :db.type/long}])

  (def cfg {:store              {:backend :mem
                                 :id      "dev-2"}
            :keep-history?      true
            :schema-flexibility :write
            :keep-log? true
            :index :datahike.index/persistent-set
            :attribute-refs?    true})

  (do
    (d/delete-database cfg)
    (d/create-database cfg))

  (def conn (d/connect cfg))

  (load-entities {:conn conn
                  :parameters {:body {:entities [[200 :db/ident :age 1000 true]
                                                 [200 :db/valueType :db.type/long 1000 true]
                                                 [200 :db/cardinality :db.cardinality/one 1000 true]
                                                 [300 :age 20 1000 true]
                                                 [400 :age 30 1001 true]]}}})

  (d/datoms @conn :eavt)

  (d/transact conn [{:age  25}
                    {:age  35}])

  (d/transact conn schema)

  (d/transact conn [{:name "Alice"
                     :age  25}
                    {:name "Bob"
                     :age  35}])

  (d/transact conn [{:name    "Charlie"
                     :age     5
                     :parents [[:name "Alice"] [:name "Bob"]]}])

  (d/transact conn [{:name "Daisy" :age 20}])
  (d/transact conn [{:name "Erhard" :age 20}])
  (d/transact conn [[:db/retractEntity [:name "Erhard"]]])

  (d/datoms (d/history @conn) :eavt [:name "Erhard"])

  (d/q '[:find ?e ?a ?v ?t
         :in $ ?a
         :where
         [?e :name ?v ?t]
         [?e :age ?a]]
       @conn
       25)

  (d/q '[:find ?e ?at ?v
         :where
         [?e ?a ?v]
         [?a :db/ident ?at]]
       @conn)

  (d/q '[:find ?e :where [?e :name "Alice"]] @conn)

  (index-range {:parameters {:body {:attrid (dd/-ref-for @conn :name)
                                    :start "A"
                                    :end "z"}}
                :conn conn})

  (reverse-schema {:conn conn}))
