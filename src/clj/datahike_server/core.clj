(ns datahike-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [taoensso.timbre :as log]
            [datahike-server.config :refer [config]]
            [datahike-server.database]
            [datahike-server.server]))

(defn -main [& args]
  (mount/start)
  (log/info "Successfully loaded configuration: " (str config))
  (log/set-level! (get-in config [:server :loglevel]))
  (log/debugf "Datahike Running!"))

(comment

  (mount/start)

  (mount/stop))
