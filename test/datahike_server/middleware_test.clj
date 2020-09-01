(ns datahike-server.middleware-test
  (:require [clojure.test :refer :all]
            [datahike-server.middleware :as sut]))

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
