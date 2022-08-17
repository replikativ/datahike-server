(ns datahike-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [taoensso.timbre :as log]
            [datahike-server.config :as config]
            [datahike-server.database]
            [datahike-server.server]))

(defn -main [& args]
  (mount/start)
  (log/info "Successfully loaded configuration: " (str config/config))
  (log/set-level! (get-in config/config [:server :loglevel]))
  (log/debugf "Datahike Server Running!"))
