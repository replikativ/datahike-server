(ns user
  (:require
   [datahike-server.core]
   [mount.core :as mount]
   [datahike-server.config :as dc]
   [taoensso.timbre :as log]
   [clojure.tools.namespace.repl :refer [refresh]]))

(defn start []
  (mount/start-with {#'datahike-server.config/config (dc/load-config "env/dev/resources/config.edn")})
  (log/set-level! :debug))

(defn stop []
  (mount/stop))

(defn restart []
  (stop)
  (refresh)
  (start))

(comment

  (restart)

  )
