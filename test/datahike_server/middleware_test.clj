(ns ^:integration datahike-server.middleware-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [datahike.core :as dc]
            [datahike.constants :as dconst]
            [datahike.db :as dd]
            [datahike-server.middleware :as sut]
            [datahike-server.test-utils :as utils]))

(use-fixtures :once utils/setup-db)

(deftest token-auth-test
  (testing "Authenticating as user"
    (is (= "authenticated-user"
           (sut/token-authfn nil "correct-token" {:server {:token :correct-token}})))
    (is (= "authenticated-user"
           (sut/token-authfn nil "correct-token" {:server {:token :correct-token}
                                                  :dev-mode true}))))
  (testing "Not authenticated"
    (is (= nil
           (sut/token-authfn nil nil {:server {:token :foobar}}))))
  (testing "Using dev-mode"
    (is (= nil
           (sut/token-authfn nil nil {:server {:dev-mode true}}))))
  (testing "Using dev-mode and providing token"
    (is (= "authenticated-user"
           (sut/token-authfn nil "correct-token" {:server {:dev-mode true
                                                           :token :correct-token}})))))

(deftest wrap-server-exception-test
  (testing "default exception behavior"
    (let [error-text "server error"]
      (is (= {:status 400, :body {:message error-text}}
             ((sut/wrap-server-exception (fn [_] (throw (ex-info error-text {})))) {}))))))

(deftest wrap-db-connection-test
  (let [app (-> (sut/wrap-db-connection identity)
                sut/wrap-server-exception
                sut/wrap-fallback-exception)
        request {:headers {"db-name" "sessions"}}]
    (testing "existing database"
      (is (-> (app request)
              :conn
              dc/conn?))
      (is (= "sessions" (-> (app request)
                            :conn
                            deref
                            :config
                            :name))))
    (testing "non-existing database"
      (let [request (assoc-in request [:headers "db-name"] "bakeries")]
        (is (= {:status 404
                :body {:message "Database bakeries does not exist."}}
               (app request)))))))

(deftest wrap-db-history-test
  (let [app (-> (sut/wrap-db-history identity)
                sut/wrap-db-connection
                sut/wrap-server-exception
                sut/wrap-fallback-exception)
        request {:headers {"db-name" "users"}}]
    (testing "history exists for the database"
      (let [request (assoc-in request [:headers "db-history-type"] "history")]
        (is (-> (app request)
                :db
                dd/-temporal-index?))))
    (testing "as-of exists for the database"
      (let [request (-> request
                        (assoc-in [:headers "db-history-type"] "as-of")
                        (assoc-in [:headers "db-timepoint"] (str dconst/tx0)))]
        (is (-> (app request)
                :db
                dd/-temporal-index?))))
    (testing "since exists for the database"
      (let [request (-> request
                        (assoc-in [:headers "db-history-type"] "since")
                        (assoc-in [:headers "db-timepoint"] (str dconst/tx0)))]
        (is (-> (app request)
                :db
                dd/-temporal-index?))))
    (testing "history on database with no history enabled"
      (let [request (-> request
                        (assoc-in [:headers "db-history-type"] "history")
                        (assoc-in [:headers "db-name"] "sessions"))]
        (is (= {:status 400
                :body {:message "history is only allowed on temporal indexed databases."}}
               (app request)))))
    (testing "as-of on database with no history enabled"
      (let [request (-> request
                        (assoc-in [:headers "db-history-type"] "as-of")
                        (assoc-in [:headers "db-timepoint"] (str dconst/tx0))
                        (assoc-in [:headers "db-name"] "sessions"))]
        (is (= {:status 400
                :body {:message "as-of is only allowed on temporal indexed databases."}}
               (app request)))))
    (testing "since on database with no history enabled"
      (let [request (-> request
                        (assoc-in [:headers "db-timepoint"] (str dconst/tx0))
                        (assoc-in [:headers "db-history-type"] "since")
                        (assoc-in [:headers "db-name"] "sessions"))]
        (is (= {:status 400
                :body {:message "since is only allowed on temporal indexed databases."}}
               (app request)))))))
