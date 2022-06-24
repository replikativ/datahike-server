(ns datahike-server.test-utils
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [datahike-server.config] ; needed just for mount/start and mount/start-with-states
            [datahike-server.json-utils :refer [json-fmt edn-fmt]]
            [datahike-server.database :as db]
            [datahike-server.server] ; needed just for mount/start and mount/start-with-states
            [clj-http.client :as client]
            [mount.core :as mount]
            [muuntaja.core :as m]
            [jsonista.core :as json]))

(defn setup-db
  ([f] (setup-db f nil))
  ([f alt-config]
   (if (some? alt-config)
     (mount/start-with-states {#'datahike-server.config/config alt-config})
     (mount/start))
   (db/cleanup-databases)
   (f)
   (mount/stop)))

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
   (api-request method url nil nil false false false))
  ([method url data]
   (api-request method url data nil false false false))
  ([method url data opts]
   (api-request method url data opts false false false))
  ([method url data opts json-req? json-ret?]
   (api-request method url data opts json-req? json-ret? false))
  ([method url data opts json-req? json-ret? keep-clj-syntax?]
   (let [encode (if json-req?   ; TODO: move to json-utils?
                  (if keep-clj-syntax?
                    (fn [data] (-> (walk/postwalk #(if (coll? %) % (str %)) data)
                                   (#(if (map? %) (reduce-kv (fn [m k v] (assoc m (subs k 1) v)) {} %) %))
                                   (json/write-value-as-string)))
                    (partial m/encode json-fmt))
                  str)]
     (-> (client/request (merge {:url (str "http://localhost:3333" url)
                                 :method method
                                 :throw-exceptions? false
                                 :content-type (if json-req? json-fmt edn-fmt)
                                 :accept (if json-ret? json-fmt edn-fmt)}
                                (when (or (= method :post) data)
                                  {:body (encode data)})
                                opts))
         (parse-body json-ret?)))))
