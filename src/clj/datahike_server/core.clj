(ns datahike-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [datahike-server.config]
            [datahike-server.database]
            [datahike-server.server]))

(defn start-all []
  (mount/start))

(defn stop-all []
  (mount/stop))

(defn -main [& args]
  (mount/start))

(comment

  (mount/start)

  (mount/stop)
)



