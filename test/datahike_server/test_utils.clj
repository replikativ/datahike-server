(ns datahike-server.test-utils
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [datahike-server.database :refer [cleanup-databases]]
            [datahike-server.core :refer [start-all stop-all]]
            [datahike-server.json-utils :refer [json-fmt edn-fmt]]
            [clj-http.client :as client]
            [muuntaja.core :as m]))

; TODO check against json-utils and consolidate or remove if similar/same
(defn keywordize-if-str [v]
  (if (string? v) (keyword v) v))

(defn keywordize-strs [c]
  (mapv #(if (map? %)
           (into {} (map (fn [[k v]] [k (walk/prewalk keywordize-if-str v)])) %)
           (walk/prewalk keywordize-if-str %))
        c))

(defn parse-body
  ([response] (parse-body response false))
  ([{:keys [body]} json?]
   (if-not (empty? body)
     (let [json-parse (partial m/decode json-fmt)
           parse (if json? json-parse edn/read-string)]
       (parse body))
     "")))

(defn api-request
  ([method url]
   (api-request method url nil nil false false))
  ([method url data]
   (api-request method url data nil false false))
  ([method url data opts]
   (api-request method url data opts false false))
  ([method url data opts json-req? json-ret?]
   (let [encode (if json-req? (partial m/encode json-fmt) str)]
     (-> (client/request (merge {:url (str "http://localhost:3333" url)
                                 :method method
                                 :throw-exceptions? false
                                 :content-type (if json-req? json-fmt edn-fmt)
                                 :accept (if json-ret? json-fmt edn-fmt)}
                                (when (or (= method :post) data)
                                  {:body (encode data)})
                                opts))
         (parse-body json-ret?)))))

(defn setup-db [f]
  (start-all)
  (cleanup-databases)
  (f)
  (stop-all))
