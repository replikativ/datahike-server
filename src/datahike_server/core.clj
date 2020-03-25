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

(s/def ::backend #{"mem" "file"})
(s/def ::path string?)
(s/def ::database-name string?)
(s/def ::temporal-index boolean?)
(s/def ::schema-flexibility #{"on-read" "on-write"})
(s/def ::new-database (s/keys :opt-un [::backend ::temporal-index ::schema-flexibility ::path ::database-name]))
(s/def ::database-config (s/keys :req-un [::backend ::path]))

(def state (atom {}))

(def routes
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title       "my-api"
                            :description "with reitit-ring"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/database"
    {:swagger {:tags ["database"]}
     :post    {:summary    "Create a new database."
               :parameters {:body ::new-database}
               :handler    (fn [{{:keys [body]} :parameters}]
                             (do
                               (d/create-database
                                 {:backend (-> body (:backend "mem") keyword)
                                  :path    (:path body (str "/tmp/dh-" (UUID/randomUUID)))}
                                 :temporal-index (:temporal-index body true)
                                 :schema-on-read (-> body
                                                     (:schema-flexibility "on-read")
                                                     keyword
                                                     (= :on-read)))
                               {:status 200}))}
     :get     {:summary    "Get current database info"
               :parameters {:params ::database-config}
               :responses  {200 {:body {:path ::path :backend ::backend :count number?}}}
               :handler    (fn [{{:keys [params]} :parameters}]
                             (let [db-config {:backend (-> params :backend keyword)
                                              :path    (:path params)}]
                               (if (-> state db-config)
                                 (if (d/database-exists? db-config)
                                   
                                   {:status 404
                                    :body "Database not found."})
                                 {:status 404
                                  :body "Database not found."})))}
     :delete  {:summary    "Delete an existing database."
               :parameters {:body ::database-config}
               :handler    (fn [{{:keys [body]} :parameters}]
                             (d/delete-database
                               {:backend (-> body (:backend "mem") keyword)
                                :path    (:path body (str "/tmp/dh-" (UUID/randomUUID)))})
                             {:status 200})}}]

   ["/math"
    {:swagger {:tags ["math"]}}

    ["/plus"
     {:get  {:summary    "plus with spec query parameters"
             :parameters {:query {:x int?, :y int?}}
             :responses  {200 {:body {:total int?}}}
             :handler    (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body   {:total (+ x y)}})}
      :post {:summary    "plus with spec body parameters"
             :parameters {:body {:x int?, :y int?}}
             :responses  {200 {:body {:total int?}}}
             :handler    (fn [{{{:keys [x y]} :body} :parameters}]
                           {:status 200
                            :body   {:total (+ x y)}})}}]]])

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
  (run-jetty app {:port  3000
                  :join? false}))

(comment

  (def server (start-server))

  (.stop server)

  )