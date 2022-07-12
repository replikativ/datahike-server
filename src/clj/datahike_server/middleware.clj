(ns datahike-server.middleware
  (:require
   [datahike.api :as d]
   [datahike-server.config :as dc]
   [datahike-server.database :as dd]
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.backends :as buddy-auth-backends]
   [buddy.auth.middleware :as buddy-auth-middleware]
   [taoensso.timbre :as log])
  (:import
   [clojure.lang ExceptionInfo]))

(defn auth
  "Middleware used in routes that require authentication. If request is not
   authenticated a 401 not authorized response will be returned.
   Dev mode always authenticates."
  [handler]
  (fn [request]
    (let [dev-mode? (get-in dc/config [:server :dev-mode])]
      (if (or (authenticated? request) dev-mode?)
        (handler request)
        {:status 401 :error "Not authorized"}))))

(defn token-authfn
  ([_ token]
   (token-authfn _ token dc/config))
  ([_ token config]
   (let [valid-token? (not (nil? token))
         correct-auth? (= (get-in config [:server :token])
                          (keyword token))]
     (when (and correct-auth? valid-token?)
       "authenticated-user"))))

(def token-backend (buddy-auth-backends/token {:authfn token-authfn :token-name "token"}))

(defn token-auth
  "Middleware used on routes requiring token authentication"
  [handler]
  (buddy-auth-middleware/wrap-authentication handler token-backend))

(defn wrap-db-connection
  "Middleware that adds a database connection based on db-name in the header of a request."
  [handler]
  (fn [{:keys [headers] :as request}]
    (if-let [db-name (get headers "db-name")]
      (let [conn (dd/get-db db-name)]
        (handler (assoc request :conn conn)))
      (handler request))))

(defn wrap-db-history
  "Middleware that adds a historical database based on db-history-type and db-timepoint in the header of a request"
  [handler]
  (fn [{:keys [headers conn] :as request}]
    (if (some? conn)
      (if-let [history-type (get headers "db-history-type")]
        (if (= "history" history-type)
          (handler (assoc request :db (d/history @conn)))
          (if-let [timepoint (get headers "db-timepoint")]
            (case history-type
              "as-of" (handler (assoc request :db (d/as-of @conn (Long/parseLong timepoint))))
              "since" (handler (assoc request :db (d/since @conn (Long/parseLong timepoint))))
              (handler request))
            (handler request)))
        (handler request))
      (handler request))))

(defn cause->status-code [cause]
  (case cause
    :db-does-not-exist 404
    400))

(defn wrap-server-exception [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [cause (:cause (.getData e))]
          {:status (cause->status-code cause)
           :body {:message (.getMessage e)}})))))

(defn wrap-fallback-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception _
        {:status 500
         :body {:message "Unexpected internal server error."}}))))

(defn time-api-call
  [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)]
      (log/info "Time elapsed: " (- (System/currentTimeMillis) start) " ms")
      response)))
