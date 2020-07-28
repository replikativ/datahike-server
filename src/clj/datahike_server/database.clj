(ns datahike-server.database
  (:require
   [datahike.api :as d]
   [datahike-server.config :refer [config]]
   [hitchhiker.tree.bootstrap.konserve :as htbk]
   [mount.core :refer [defstate start stop]]))


(defn init-connections [{:keys [databases]}]
  (reduce
   (fn [acc {:keys [name] :as cfg}]
     (when (contains? acc name)
       (throw (ex-info
               (str "A database with name '" name "' already exists. Database names on the transactor should be unique.")
               {:event :connection/initialization
                :error :database.name/duplicate})))
     (when-not (d/database-exists? cfg)
       (println "-> Creating database...")
       (d/create-database cfg)
       (println "Done"))
     (let [conn (d/connect cfg)]
       (assoc acc name conn)))
   {}
   databases))

(defstate database
  :start (atom {:connections (init-connections config)})
  :stop (do
          (println "Cleaning up connections...")
          (swap! database update :connections (fn [old]
                                                (for [[_ conn] old]
                                                  (d/release conn))))
          (swap! database dissoc :connections)
          (println "Done")))

(defn cleanup-databases []
  (stop #'datahike-server.database/database)
  (doall
   (for [cfg (:databases config)]
     (do
       (println "Purging " cfg " ...")
       (d/delete-database cfg)
       (println "Done"))))
  (start #'datahike-server.database/database))


(comment

  (cleanup-databases)

  (def conn (-> @database :connections (get "users")))

  (d/transact conn [{:db/ident :email
                     :db/valueType :db.type/string
                     :db/unique :db.unique/value
                     :db/cardinality :db.cardinality/one}
                    {:db/ident :password
                     :db/valueType :db.type/string
                     :db/cardinality :db.cardinality/one}])

  (d/transact conn [{:email "coyote@acme.corp"
                     :password "kaboom"}
                    {:email "roadrunner@freedom.org"
                     :password "wooosh"}])

  (def ao (d/as-of @conn 536870916))


  (d/q '[:find ?e :where [?e :email _]] ao)

  (d/datoms ao :eavt)

  )
