(ns datahike-server.config-test
  (:require [clojure.test :refer :all]
            [datahike-server.config :refer :all]))

(deftest bad-config-test
  (testing "Loading bad config file"
    (with-redefs [load-config-file (constantly {:server {:join? :baz}})]
      (is (= {:server {:port 3000 :join? false :loglevel :info}
              :datahike nil}
             (load-config "foobar")))
      (is (= {:server {:port 3000 :join? false :loglevel :info}
              :datahike nil}
             (load-config :foobar))))))

(deftest config-test
  (testing "Loading good config file"
      (with-redefs [load-config-file (constantly {:datahike {:backend :file
                                                             :path "/foo/bar"}
                                                  :server {:port 5678
                                                           :join? true
                                                           :loglevel :fatal}})]
        (is (= {:server {:port 5678 :join? true :loglevel :fatal}
                :datahike {:backend :file :path "/foo/bar"}}
               (load-config "foobar"))))
      (with-redefs [load-config-file (constantly {:datahike {:backend :pg
                                                             :username "tester"
                                                             :password "test"
                                                             :host "testhost"
                                                             :port 12345
                                                             :path "/testdb"}
                                                  :server {:port 5678
                                                           :join? true
                                                           :loglevel :fatal}})]
        (is (= {:server {:port 5678, :join? true, :loglevel :fatal},
                :datahike
                {:backend :pg,
                 :username "tester",
                 :password "test",
                 :host "testhost",
                 :port 12345,
                 :path "/testdb"}}
               (load-config "foobar"))))
      (with-redefs [load-config-file (constantly {:foo {:bar :baz}})]
        (is (= {:server {:port 3000 :join? false :loglevel :info}
                :datahike nil}
               (load-config "foobar"))))
      (with-redefs [load-config-file (constantly nil)]
        (is (= {:server {:port 3000 :join? false :loglevel :info}
                :datahike nil}
               (load-config "foobar"))))
      (with-redefs [load-config-file (constantly "foobar")]
        (is (= {:server {:port 3000 :join? false :loglevel :info}
                :datahike nil}
               (load-config "foobar"))))))

(deftest load-config-file-test
  (testing "Loading a file from disk"
    (is (= nil (load-config-file "foo")))
    (is (= nil (load-config-file "test/datahike_server/resources/config.edn.broken")))
    (is (= {:datahike
            {:store {:backend :file, :path "/tmp/dh-2"},
             :schema-on-read true,
             :temporal-index false},
            :server {:port 3333, :join? false, :loglevel :debug}}
           (load-config-file "test/datahike_server/resources/config.edn")))))
