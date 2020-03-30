(ns datahike-server.core
  (:require [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [datahike.api :as d]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.util UUID)))

(def config {:backend :mem
             :path "/tmp/dh-2"
             :temporal-index true
             :schema-on-read false})

(s/def ::entity any?)
(s/def ::tx-data (s/coll-of ::entity))
(s/def ::tx-meta (s/coll-of ::entity))
(s/def ::transactions (s/keys :req-un [::tx-data] :opt-un [::tx-meta]))

(s/def ::query (s/coll-of any?))
(s/def ::args (s/coll-of any?))
(s/def ::limit number?)
(s/def ::offset number?)
(s/def ::query-request (s/keys :req-un [::query]
                               :opt-un [::args ::limit ::offset]))


(def state (atom {}))

(defn transact-handler [{{:keys [body]} :parameters}]
  (let [result (d/transact (:conn @state) {:tx-data (:tx-data body [])
                                                   :tx-meta (:tx-meta body [])})]
    {:status 200
     :body (-> result
               (dissoc :db-after :db-before)
               (update :tx-data #(map seq %))
               (update :tx-meta #(map seq %)))}))

(defn query-handler [{{:keys [body]} :parameters}]
  {:status 200
   :body (d/q {:query (:query body [])
               :args (concat [(-> @state :conn d/db)] (:args body []))
               :limit (:limit body -1)
               :offset (:offset body 0)})})

(def routes
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title       "my-api"
                            :description "with reitit-ring"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/transact"
    {:swagger {:tags ["transact"]}
     :post {:summary "Transact new data."
            :parameters {:body ::transactions}
            :handler transact-handler}}]

   ["/q"
    {:swagger {:tags ["query"]}
     :post {:summary "Query database"
            :parameters {:body ::query-request}
            :handler query-handler}}]])

(def route-opts
  {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
   ;; :validate spec/validate ;; enable spec validation for route data
   ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
   :exception pretty/exception
   :data      {:coercion   reitit.coercion.spec/coercion
               :muuntaja   m/instance
               :middleware [swagger/swagger-feature
                            parameters/parameters-middleware
                            muuntaja/format-negotiate-middleware
                            muuntaja/format-response-middleware
                            exception/exception-middleware
                            muuntaja/format-request-middleware
                            coercion/coerce-response-middleware
                            coercion/coerce-request-middleware
                            multipart/multipart-middleware]}})

(def app
  (ring/ring-handler
    (ring/router routes route-opts)
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path   "/"
         :config {:validatorUrl     nil
                  :operationsSorter "alpha"}})
      (ring/create-default-handler))))

(defn init [{:keys [temporal-index schema-on-read] :as config}]
  (when-not (d/database-exists? config)
    (d/create-database config
                       :temporal-index temporal-index
                       :schema-on-read schema-on-read))
  (swap! state assoc :conn (d/connect config)))

(defn start-server []
  (run-jetty app {:port  3000
                  :join? false}))
(comment

  (let [{:keys [temporal-index schema-on-read]} config]
    (d/create-database config
                       :temporal-index temporal-index
                       :schema-on-read schema-on-read))

  (transact-handler {:parameters {:body {:tx-data [{:db/ident :boo
                                                    :db/valueType :db.type/string
                                                    :db/cardinality :db.cardinality/one}]}}})

  (init config)

  (def server (start-server))

  (datahike.db/-config @(:conn @state))

  (.stop server)

  (d/datoms (-> @state :conn d/db) :eavt nil)


  )
