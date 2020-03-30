(ns datahike-server.server
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
            [datahike-server.handlers :as h]
            [datahike-server.config :refer [config]]
            [mount.core :refer [defstate]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.util UUID)))

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

(s/def ::selector (s/coll-of any?))
(s/def ::eid any?)
(s/def ::pull-request (s/keys :req-un [::selector ::eid]))

(s/def ::eids (s/coll-of ::eid))
(s/def ::pull-many-request (s/keys :req-un [::selector ::eids]))

(def routes
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title       "my-api"
                            :description "with reitit-ring"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/transact"
    {:swagger {:tags ["transact"]}
     :post {:summary "Applies transaction to the underlying database value."
            :parameters {:body ::transactions}
            :handler h/transact}}]

   ["/q"
    {:swagger {:tags ["query"]}
     :post {:summary "Executes a datalog query."
            :parameters {:body ::query-request}
            :handler h/q}}]

   ["/pull"
    {:swagger {:tags ["query"]}
     :post {:summary "Fetches data from database using recursive declarative description."
            :parameters {:body ::pull-request}
            :handler h/pull}}]

   ["/pull-many"
    {:swagger {:tags ["query"]}
     :post {:summary "Same as [[pull]], but accepts sequence of ids and returns sequence of maps."
            :parameters {:body ::pull-many-request}
            :handler h/pull-many}}]])

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

(defn start-server []
  (run-jetty app (:server config)))

(defstate server
  :start (start-server)
  :stop (.stop server))
