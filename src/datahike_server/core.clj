(ns datahike-server.core
  (:require [clojure.java.io :as io]
            [datahike.api :as d]
            [ring.util.response :refer [response content-type]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.util UUID)))

(def config {:backend :mem
             :path "/tmp/dh-2"
             :temporal-index true
             :schema-on-read false})




(comment

  (let [{:keys [temporal-index schema-on-read]} config]
    (d/create-database config
                       :temporal-index temporal-index
                       :schema-on-read schema-on-read))

  (init config)

  (def server (start-server))

  (datahike.db/-config @(:conn @state))

  (.stop server)

  (d/datoms (-> @state :conn d/db) :eavt nil)

  (def report (d/transact (:conn @state) [{:foo "BAR"}]))

  (-> report
      (dissoc :db-after :db-before)
      (update :tx-data #(mapv seq %))) 

  (d/q {:query (-> @state :query)
        :args (concat [(-> @state :conn d/db)] )
        :limit -1
        :offset 0})

  )
