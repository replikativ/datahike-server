(ns ^:integration datahike-server.setup-test
  (:require [clojure.test :refer [deftest testing is]]
            [datahike-server.database :as db]
            [datahike-server.config :as config]
            [datahike-server.test-utils :refer [api-request]]
            [mount.core :as mount]))

(deftest dev-mode-test
  (mount/start-with-states {#'datahike-server.config/config
                            {:start #(assoc-in (config/load-config config/config-file-path) [:server :dev-mode] true)
                             :stop (fn [] {})}})
  (db/cleanup-databases)
  (is (= {:databases
          [{:store {:id "sessions", :backend :mem},
            :keep-history? false,
            :schema-flexibility :read,
            :index :datahike.index/hitchhiker-tree
            :attribute-refs? false,
            :cache-size 100000,
            :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}
           {:store {:path "/tmp/dh-file", :backend :file},
            :keep-history? true,
            :schema-flexibility :write,
            :index :datahike.index/hitchhiker-tree
            :attribute-refs? false,
            :cache-size 100000,
            :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}]}
         (api-request :get "/databases"
                      nil
                      {})))
  (mount/stop))
