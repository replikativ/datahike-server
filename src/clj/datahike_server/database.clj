(ns datahike-server.database
  (:require [konserve.filestore :refer [new-fs-store]]
            [konserve.core :as k]
            [clojure.core.async :as async :refer [<!!]]
            [mount.core :refer [defstate]]))

(defstate database
  :start (atom {:connections {}
                :store (<!! (new-fs-store "./.store"))}))

