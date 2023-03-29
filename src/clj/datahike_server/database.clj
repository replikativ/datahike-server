(ns datahike-server.database
  (:require [mount.core :as mount :refer [defstate]]
            [taoensso.timbre :as log]
            [datahike-server.config :as config]
            [datahike.api :as d]
            [datahike.store :as ds])
  (:import [java.util UUID]))

(defn init-connections [{:keys [databases] :as config}]
  (log/debug "Connecting to databases with config: " (str config))
  (if (nil? databases)
    (let [_ (when-not (d/database-exists?)
              (log/infof "Creating database...")
              (d/create-database)
              (log/infof "Done"))
          conn (d/connect)]
      {(pr (ds/store-identity (-> @conn :config :store))) conn})
    (reduce
     (fn [acc cfg]
       (let [store-identity (pr (ds/store-identity (:store cfg)))]
         (when (contains? acc store-identity)
           (throw (ex-info
                   (str "A database with store-identity '" store-identity "' already exists. Store identities on the server must be unique.")
                   {:event :connection/initialization
                    :error :database.store-identity/duplicate
                    :config cfg})))
         (when-not (d/database-exists? cfg)
           (log/infof "Creating database...")
           (d/create-database cfg)
           (log/infof "Done"))
         (let [conn (d/connect cfg)]
           (assoc acc store-identity conn))))
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

(defn get-db [store-identity]
  (if-let [conn (get conns store-identity)]
    conn
    (throw (ex-info (format "Database %s does not exist." store-identity)
                    {:cause :db-does-not-exist
                     :conn conn
                     :store-identity store-identity}))))
