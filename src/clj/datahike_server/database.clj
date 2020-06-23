(ns datahike-server.database
  (:require
   [datahike.api :as d]
   [datahike-server.config :refer [config]]
   [mount.core :refer [defstate]]))


(defn init-connections [{:keys [databases]}]
  (reduce
   (fn [acc {:keys [name] :as cfg}]
     (when (contains? acc name)
       (throw (ex-info
               (str "A database with name '" name "' already exists. Database names on the transactor should be unique.")
               {:event :connection/initialization
                :error :database.name/duplicate})))
     (when-not (d/database-exists? cfg)
       (d/create-database cfg))
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

