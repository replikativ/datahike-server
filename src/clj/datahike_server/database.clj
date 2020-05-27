(ns datahike-server.database
  (:require [konserve.filestore :refer [new-fs-store]]
            [konserve.core :as k]
            [clojure.core.async :as async :refer [<!!]]
            [datahike.api :as d]
            [mount.core :refer [defstate]]))

(defstate database
  :start (atom {:connections {}
                :store (<!! (new-fs-store "./.store"))})
  :stop (do
          (println "Cleaning up connections...")
          (swap! database update :connections (fn [old]
                                                (for [[id conn] old]
                                                  (do
                                                    (<!! (k/update-in (:store @database) [:configurations] dissoc id))
                                                    (d/release conn)))))
          (swap! database dissoc :connections)
          (println "Done")
          (println "Disconnecting from store...")
          (swap! database dissoc :store)
          (println "Done")))

