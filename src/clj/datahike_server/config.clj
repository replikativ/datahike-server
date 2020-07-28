(ns datahike-server.config
  (:require [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [mount.core :refer [defstate]]
            [environ.core :refer [env]]
            [datahike.config :refer [int-from-env bool-from-env]]))

(s/fdef load-config-file
  :args (s/cat :config-file string?)
  :ret map?)
(s/fdef load-config
  :args (s/cat :config-file string?)
  :ret map?)
(s/def ::port int?)
(s/def ::join? boolean?)
(s/def ::loglevel #{:trace :debug :info :warn :error :fatal :report})
(s/def ::server-config (s/keys :req-un [::port ::join? ::loglevel]))

(defn load-config-file [config-file]
  (try
    (-> config-file slurp read-string)
    (catch java.io.FileNotFoundException e (log/info "No config file found at " config-file))
    (catch RuntimeException e (log/info "Could not validate edn in config file " config-file))))

(defn load-config
  "Loads config for Datahike server

   Argument: relative path of config file as string"
  [config-file]
  (let [config-from-file (load-config-file config-file)
        server-config (merge
                       {:port (int-from-env :datahike-server-port 3000)
                        :join? (bool-from-env :datahike-server-join? false)
                        :loglevel (keyword (:datahike-server-loglevel env :info))}
                       (:server config-from-file))
        validated-server-config (if (s/valid? ::server-config server-config)
                                  server-config
                                  (do
                                    (log/error "Server configuration error:" (s/explain-data ::server-config server-config))
                                    (log/error "Loading default configuration for server.")
                                    {:port 3000
                                     :join? false
                                     :loglevel :info}))
        datahike-config (:datahike config-from-file)]
    {:server validated-server-config
     :datahike datahike-config}))

(defstate config
  :start (do
           (log/debug "Loading config")
           (load-config "resources/config.edn")))
