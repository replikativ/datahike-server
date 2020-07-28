(ns datahike-server.database
  (:require [mount.core :refer [defstate]]
            [taoensso.timbre :as log]
            [datahike-server.config :refer [config]]
            [datahike.api :as d])
  (:import [java.util UUID]))

(defn connect [config]
 (let [datahike-config (:datahike config)]
    (when-not (d/database-exists? datahike-config)
      (log/infof "Creating database..." datahike-config)
      (d/create-database datahike-config)
      (log/infof "Database created."))
    (d/connect datahike-config)))

(defstate conn
  :start (do
           (log/debug "Connecting database with config: " (str config))
           (connect config))
  :stop (d/release conn))
