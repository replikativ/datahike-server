(ns datahike-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [datahike-server.config :refer [config]]
            [datahike-server.database]
            [datahike-server.server]))

(log/merge-config! {:level :debug
                    :appenders {:rotating (rotor/rotor-appender
                                           {:path "/var/log/datahike-server.log"
                                            :max-size (* 512 1024)
                                            :backlog 10})}})

(defn -main [& args]
  (mount/start)
  (log/merge-config! (:logging config))
  (log/debugf "Datahike Running!"))

(comment

  (mount/start)

  (mount/stop))
