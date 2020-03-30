(ns datahike-server.core
  (:require [mount.core :as mount]
            [datahike-server.config]
            [datahike-server.database]
            [datahike-server.server]))

(defn -main [& args]
  (mount/start))

(comment

  (mount/start)

  (mount/stop)

  )

