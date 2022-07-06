(ns datahike-server.test-utils
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [datahike-server.config] ; needed just for mount/start and mount/start-with-states
            [datahike-server.json-utils :as ju]
            [datahike-server.database :as db]
            [datahike-server.server] ; needed just for mount/start and mount/start-with-states
            [clj-http.client :as client]
            [mount.core :as mount]
            [muuntaja.core :as m]))

(defn setup-db
  ([f] (setup-db f nil))
  ([f alt-config]
   (if (some? alt-config)
     (mount/start-with-states {#'datahike-server.config/config alt-config})
     (mount/start))
   (db/cleanup-databases)
   (f)
   (mount/stop)))

(defn parse-body
  ([response] (parse-body response false))
  ([{:keys [body]} json?]
   (if-not (empty? body)
     (let [json-parse (fn [body]
                        (walk/postwalk #(if (and (vector? %) (= "!set" (first %)))
                                          (set (rest %))
                                          %)
                                       (m/decode ju/json-fmt body)))
           parse (if json? json-parse edn/read-string)]
       (parse body))
     "")))

(defn to-json-recursive [c]
  (walk/postwalk #(if (list? %)
                    (conj % "!list")
                    (if (or (coll? %) (number? %) (boolean? %) (nil? %))
                      %
                      (str %)))
                 c))

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
   (let [encode (if json-req?
                  (if keep-clj-syntax?
                    (fn [data]
                      (->> (to-json-recursive data)
                           (m/encode ju/json-fmt)))
                    (partial m/encode ju/json-fmt))
                  str)]
     (-> (client/request (merge {:url (str "http://localhost:3333" url)
                                 :method method
                                 :throw-exceptions? false
                                 :content-type (if json-req? ju/json-fmt ju/edn-fmt)
                                 :accept (if json-ret? ju/json-fmt ju/edn-fmt)}
                                (when (or (= method :post) data)
                                  {:body (encode data)})
                                opts))
         (parse-body json-ret?)))))
