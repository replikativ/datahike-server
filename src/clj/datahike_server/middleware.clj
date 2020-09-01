(ns datahike-server.middleware
  (:require
   [datahike-server.config :as dc]
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.backends :as buddy-auth-backends]
   [buddy.auth.middleware :as buddy-auth-middleware]))

(defn auth
  "Middleware used in routes that require authentication. If request is not
   authenticated a 401 not authorized response will be returned.
   Dev mode always authenticates."
  [handler]
  (fn [request]
    (let [dev-mode? (clojure.core/get-in dc/config [:server :dev-mode])]
      (if (or (authenticated? request) dev-mode?)
        (handler request)
        {:status 401 :error "Not authorized"}))))

(defn token-authfn
  ([_ token]
   (token-authfn _ token dc/config))
  ([_ token config]
   (let [valid-token? (not (nil? token))
         correct-auth? (= (clojure.core/get-in config [:server :token])
                          (keyword token))]
     (when (and correct-auth? valid-token?)
       "authenticated-user"))))

(def token-backend (buddy-auth-backends/token {:authfn token-authfn :token-name "token"}))

(defn token-auth
  "Middleware used on routes requiring token authentication"
  [handler]
  (buddy-auth-middleware/wrap-authentication handler token-backend))
