(ns datahike-server.database
  (:require [mount.core :refer [defstate]]
            [datahike-server.config :refer [config]]
            [datahike.api :as d])
  (:import [java.util UUID]))

(defstate conn
  :start
  (let [store-config (:store config {:backend :mem :path (str"/dh/" (UUID/randomUUID))})]
    (when-not (d/database-exists? store-config)
      (println "Creating database..." store-config)
      (d/create-database store-config
                         :temporal-index (:temporal-index config false)
                         :schema-on-read (:schema-on-read config true))
      (println "Database created."))
    (println "Connecting to database...")
    (d/connect store-config))
  :stop (d/release conn))


