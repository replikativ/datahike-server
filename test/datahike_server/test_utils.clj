(ns datahike-server.test-utils
  (:require [clojure.edn :as edn]
            [datahike-server.database :refer [cleanup-databases]]
            [datahike-server.core :refer [start-all stop-all]]
            [clj-http.client :as client]))

(defn parse-body [{:keys [body]}]
  (if-not (empty? body)
    (edn/read-string body)
    ""))

(defn api-request
  ([method url]
   (api-request method url nil nil))
  ([method url data]
   (api-request method url data nil))
  ([method url data opts]
   (-> (client/request (merge {:url (str "http://localhost:3333" url)
                               :method method
                               :throw-exceptions? false
                               :content-type "application/edn"
                               :accept "application/edn"}
                              (when (or (= method :post) data)
                                {:body (str data)})
                              opts))
       parse-body)))

(defn setup-db [f]
  (start-all)
  (cleanup-databases)
  (f)
  (stop-all))
