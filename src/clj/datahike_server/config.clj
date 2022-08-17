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
  :args (s/cat :config-file #(or (string? %) (map? %)))
  :ret map?)
(s/def ::port int?)
(s/def ::join? boolean?)
(s/def ::loglevel #{:trace :debug :info :warn :error :fatal :report})
(s/def ::token keyword?)
(s/def ::dev-mode boolean?)
(s/def ::server-config (s/keys :req-un [::port ::join? ::loglevel]
                               :opt-un [::dev-mode ::token]))

(def config-file-path "resources/config.edn")

(defn load-config-file [config-file]
  (try
    (-> config-file slurp read-string)
    (catch java.io.FileNotFoundException e (log/info "No config file found at " config-file))
    (catch RuntimeException e (log/info "Could not validate edn in config file " config-file))))

(defn load-config
  "Loads and validates config for Datahike server. Accepts a map as config, or relative path of a config file as string."
  [config]
  (log/debug "Loading config")
  (let [arg-config (cond-> config
                     (string? config) load-config-file)
        server-config (merge
                       {:port (int-from-env :datahike-server-port 3000)
                        :join? (bool-from-env :datahike-server-join? false)
                        :loglevel (keyword (:datahike-server-loglevel env :info))
                        :dev-mode (bool-from-env :datahike-server-dev-mode false)}
                       (:server arg-config))
        token-config (if-let [token (keyword (:datahike-server-token env))]
                       (merge
                        {:token (keyword (:datahike-server-token env))}
                        server-config)
                       server-config)
        validated-server-config (if (s/valid? ::server-config token-config)
                                  token-config
                                  (throw (ex-info "Server configuration error:" (s/explain-data ::server-config token-config))))
        datahike-configs (:databases arg-config)]
    {:server validated-server-config
     :databases datahike-configs}))

(defstate config
  :start (load-config config-file-path)
  :stop {})
