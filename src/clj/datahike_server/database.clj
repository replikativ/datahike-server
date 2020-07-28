(ns datahike-server.database
  (:require [mount.core :refer [defstate]]
            [taoensso.timbre :as log]
            [datahike-server.config :refer [config]]
            [datahike.api :as d])
  (:import [java.util UUID]))

(defn connect [config]
 (if-let [datahike-config (:datahike config)]
    (when-not (d/database-exists? datahike-config)
      (log/infof "Creating database..." datahike-config)
      (d/create-database datahike-config)
      (log/infof "Database created.")
      (d/connect datahike-config))
    (when-not (d/database-exists?)
      (log/infof "Creating database...")
      (d/create-database)
      (log/infof "Database created.")
      (d/connect))))

(defstate conn
  :start (do
           (log/debug "Connecting database with config: " (str config))
           (connect config))
  :stop (d/release conn))

(comment
  (connect {:datahike {:store {:backend :foo
                               :path    :baz}}})
  (d/database-exists?)
  (d/create-database)
  (connect nil))
