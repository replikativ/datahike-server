(ns datahike-server.database
  (:require [mount.core :as mount :refer [defstate]]
            [taoensso.timbre :as log]
            [datahike-server.config :as config]
            [datahike.api :as d])
  (:import [java.util UUID]))

(defn init-connections [{:keys [databases] :as config}]
  (log/debug "Connecting to databases with config: " (str config))
  (if (nil? databases)
    (let [_ (when-not (d/database-exists?)
              (log/infof "Creating database...")
              (d/create-database)
              (log/infof "Done"))
          conn (d/connect)]
      {(-> @conn :config :name) conn})
    (reduce
     (fn [acc {:keys [name] :as cfg}]
       (when (contains? acc name)
         (throw (ex-info
                 (str "A database with name '" name "' already exists. Database names on the transactor should be unique.")
                 {:event :connection/initialization
                  :error :database.name/duplicate})))
       (when-not (d/database-exists? cfg)
         (log/infof "Creating database...")
         (d/create-database cfg)
         (log/infof "Done"))
       (let [conn (d/connect cfg)]
         (assoc acc (-> @conn :config :name) conn)))
     {}
     databases)))

(defn release-conns [conns]
  (for [conn (vals conns)]
    (d/release conn)))

(defstate conns
  :start (init-connections config/config)
  :stop (release-conns conns))

(defn cleanup-databases []
  ; (vals conns) triggers an exception after conns is unmounted
  (let [cfgs (map (fn [conn] (.-config @conn)) (vals conns))]
    (mount/stop #'datahike-server.database/conns)
    (doseq [cfg cfgs]
      (println "Purging " cfg " ...")
      (d/delete-database cfg)
      (println "Done")))
  (mount/start #'datahike-server.database/conns))

(defn get-db [db-name]
  (if-let [conn (get conns db-name)]
    conn
    (throw (ex-info (format "Database %s does not exist." db-name)
                    {:cause :db-does-not-exist}))))
