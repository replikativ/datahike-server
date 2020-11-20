(ns datahike-server.server
  (:require [datahike-server.handlers :as h]
            [datahike-server.config :refer [config]]
            [datahike-server.database :refer [conns]]
            [datahike-server.middleware :as middleware]
            [datahike.api :as d]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.middleware.dev :as dev]
            [ring.middleware.cors :refer [wrap-cors]]
            [muuntaja.core :as m]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [mount.core :refer [defstate]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.pprint :refer [pprint]]))

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

(s/def ::index #{:eavt :aevt :avet})
(s/def ::components (s/coll-of any?))
(s/def ::datoms-request (s/keys :req-un [::index] :opt-un [::components]))

(s/def ::attr keyword?)
(s/def ::entity-request (s/keys :req-un [::eid] :opt-un [::attr]))

(s/def ::db-name string?)
(s/def ::query-id number?)

(s/def ::conn-header (s/keys :req-un [::db-name]))

(s/def ::db-hash number?)

(s/def ::db-tx int?)
(s/def ::db-header (s/keys :req-un [::db-name]
                           :opt-un [::db-tx]))

(def routes
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title       "Datahike API"
                            :description "Transaction and search functions"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/databases"
    {:swagger {:tags ["API"]}
     :get     {:operationId "ListDatabases"
               :summary "List available databases."
               :middleware [middleware/token-auth middleware/auth]
               :handler h/list-databases}}]

   ["/echo"
    {:get  {:operationId "EchoGET"
            :summary "For testing purposes only."
            :handler (fn [request]
                       (pprint request)
                       {:status 200})}
     :post {:operationId "EchoPOST"
            :parameters {:body map?}
            :handler    (fn [request]
                          (pprint request)
                          {:status 200})}}]

   ["/echo/:id"
    {:get {:operationId "EchoId"
           :summary "For testing purposes only."
           :handler (fn [request]
                      (pprint request)
                      {:status 200})}}]

   ["/transact"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Transact"
               :summary "Applies transaction to the underlying database value."
               :parameters {:body   ::transactions
                            :header ::conn-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/transact}}]

   ["/db"
    {:swagger {:tags ["API"]}
     :get     {:operationId "DatabaseHash"
               :summary "Get current database as a hash."
               :parameters {:header ::conn-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/get-db}}]

   ["/q"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Query"
               :summary    "Executes a datalog query."
               :parameters {:body   ::query-request
                            :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/q}}]

   ["/pull"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Pull"
               :summary    "Fetches data from database using recursive declarative description."
               :parameters {:body ::pull-request :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/pull}}]

   ["/pull-many"
    {:swagger {:tags ["API"]}
     :post    {:operationId "PullMany"
               :summary    "Same as [[pull]], but accepts sequence of ids and returns sequence of maps."
               :parameters {:body ::pull-many-request :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/pull-many}}]

   ["/datoms"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Datoms"
               :summary    "Index lookup. Returns a sequence of datoms (lazy iterator over actual DB index) which components (e, a, v) match passed arguments."
               :parameters {:body ::datoms-request :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/datoms}}]

   ["/seek-datoms"
    {:swagger {:tags ["API"]}
     :post    {:operationId "SeekDatoms"
               :summary    "Similar to [[datoms]], but will return datoms starting from specified components and including rest of the database until the end of the index."
               :parameters {:body ::datoms-request :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/seek-datoms}}]

   ["/tempid"
    {:swagger {:tags ["API"]}
     :get     {:operationId "TempID"
               :summary    "Allocates and returns an unique temporary id."
               :parameters {:header ::conn-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/tempid}}]

   ["/entity"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Entity"
               :summary    "Retrieves an entity by its id from database. Realizes full entity in contrast to entity in local environments."
               :parameters {:body ::entity-request :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/entity}}]

   ["/schema"
    {:swagger {:tags ["API"]}
     :get     {:operationId "Schema"
               :summary    "Fetches current schema"
               :parameters {:header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/schema}}]])

(defn wrap-db-connection [handler]
  (fn [request]
    (if-let [db-name (get-in request [:headers "db-name"])]
      (if-let [conn (conns db-name)]
        (if-let [tx (get-in request [:headers "db-tx"])]
          (if-let [db (d/as-of @conn (Integer/parseInt tx))]
            (handler (assoc request :db db :conn conn))
            (handler request))
          (handler (assoc request :conn conn)))
        (handler request))
      (handler request))))

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
  (-> (ring/ring-handler
       (ring/router routes route-opts)
       (ring/routes
        (swagger-ui/create-swagger-ui-handler
         {:path   "/"
          :config {:validatorUrl     nil
                   :operationsSorter "alpha"}})
        (ring/create-default-handler)))
      wrap-db-connection
      (wrap-cors :access-control-allow-origin [#"http://localhost" #"http://localhost:8080" #"http://localhost:4000"]
                 :access-control-allow-methods [:get :put :post :delete])))

(defn start-server [config]
  (run-jetty app (:server config)))

(defstate server
  :start (do
           (log/debug "Starting server")
           (start-server config))
  :stop (.stop server))

