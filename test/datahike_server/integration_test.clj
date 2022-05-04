(ns ^:integration datahike-server.integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [datahike-server.test-utils :refer [api-request setup-db] :as utils]))

(defn add-test-data []
  (api-request :post "/transact"
               {:tx-data [{:name "Alice" :age 20} {:name "Bob" :age 21}]}
               {:headers {:authorization "token neverusethisaspassword"
                          :db-name "sessions"}}))

(defn add-test-schema []
  (api-request :post "/transact"
               {:tx-data [{:db/ident :name
                           :db/valueType :db.type/string
                           :db/unique :db.unique/identity
                           :db/cardinality :db.cardinality/one}]}
               {:headers {:authorization "token neverusethisaspassword"
                          :db-name "users"}}))

(use-fixtures :each setup-db)

(deftest swagger-test
  (testing "Swagger Json"
    (is (= {:title "Datahike API"
            :description "Transaction and search functions"}
           (:info (api-request :get
                               "/swagger.json"
                               nil
                               {:headers {:authorization "token neverusethisaspassword"}}))))))

(deftest databases-test
  (testing "Get Databases"
    (is (= {:databases
            [{:store {:id "sessions", :backend :mem},
              :keep-history? false,
              :schema-flexibility :read,
              :name "sessions",
              :index :datahike.index/hitchhiker-tree
              :attribute-refs? false,
              :cache-size 100000,
              :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}
             {:store {:path "/tmp/dh-file", :backend :file},
              :keep-history? true,
              :schema-flexibility :write,
              :name "users",
              :index :datahike.index/hitchhiker-tree
              :attribute-refs? false,
              :cache-size 100000,
              :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}]}
           (api-request :get "/databases"
                        nil
                        {:headers {:authorization "token neverusethisaspassword"}})))))

(deftest transact-test
  (let [transact-request (partial api-request :post "/transact")
        body {:tx-data [{:foo 1}]}
        params {:headers {:authorization "token neverusethisaspassword"
                          :db-name "sessions"}}]
    (testing "Transact values on database without schema"
      (is (= {:tx-data [[1 :foo 1 536870913 true]], :tempids #:db{:current-tx 536870913}, :tx-meta []}
             (transact-request body params))))
    (testing "Transact values on database with schema"
      (is (= {:message "Bad entity attribute :foo at {:db/id 1, :foo 1}, not defined in current schema"}
             (transact-request body (assoc-in params [:headers :db-name] "users")))))))

(deftest db-test
  (testing "Get current database as a hash"
    (is (= 0 (:hash (api-request :get "/db"
                                 nil
                                 {:headers {:authorization "token neverusethisaspassword"
                                            :db-name "sessions"}}))))
    (is (= 0 (:hash (api-request :get "/db"
                                 nil
                                 {:headers {:authorization "token neverusethisaspassword"
                                            :db-name "users"}}))))))

(deftest q-test
  (testing "Executes a datalog query"
    (add-test-data)
    (is (= "Alice"
           (second (first (api-request :post "/q"
                                       {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                                        :args ["Alice"]}
                                       {:headers {:authorization "token neverusethisaspassword"
                                                  :db-name "sessions"}})))))))

(deftest pull-test
  (testing "Fetches data from database using recursive declarative description."
    (add-test-data)
    (is (= {:name "Alice"}
           (api-request :post "/pull"
                        {:selector '[:name]
                         :eid 1}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))
(deftest pull-many-test
  (testing "Same as pull, but accepts sequence of ids and returns sequence of maps."
    (add-test-data)
    (is (= [{:name "Alice"} {:name "Bob"}]
           (api-request :post "/pull-many"
                        {:selector '[:name]
                         :eids '(1 2 3 4)}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))

(deftest datoms-test
  (testing "Index lookup. Returns a sequence of datoms (lazy iterator over actual DB index) which components (e, a, v) match passed arguments."
    (add-test-data)
    (is (= 20
           (-> (api-request :post "/datoms"
                            {:index :aevt
                             :components [:age]}
                            {:headers {:authorization "token neverusethisaspassword"
                                       :db-name "sessions"}})
               first
               (get 2))))))

(deftest seek-datoms-test
  (testing "Similar to datoms, but will return datoms starting from specified components and including rest of the database until the end of the index."
    (add-test-data)
    (is (= 20
           (nth (first (api-request :post "/seek-datoms"
                                    {:index :aevt
                                     :components [:age]}
                                    {:headers {:authorization "token neverusethisaspassword"
                                               :db-name "sessions"}}))
                2)))))

(deftest tempid-test
  (testing "Allocates and returns an unique temporary id."
    (is (number? (:tempid (api-request :get "/tempid"
                                       {}
                                       {:headers {:authorization "token neverusethisaspassword"
                                                  :db-name "sessions"}}))))))

(deftest entity-test
  (testing "Retrieves an entity by its id from database. Realizes full entity in contrast to entity in local environments."
    (add-test-data)
    (is (= {:age 21 :name "Bob"}
           (api-request :post "/entity"
                        {:eid 2}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))

(deftest schema-test
  (testing "Fetches current schema"
    (add-test-schema)
    (is (= {:name #:db{:ident       :name,
                       :valueType   :db.type/string,
                       :unique  :db.unique/identity
                       :cardinality :db.cardinality/one,
                       :id          1}}
           (api-request :get "/schema"
                        {}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "users"}})))))

(deftest reverse-schema-test
  (testing "Fetches current reverse schema"
    (add-test-schema)
    (is (= {:db/ident #{:name}
            :db/unique #{:name}
            :db.unique/identity #{:name}
            :db/index #{:name}}
           (api-request :get "/reverse-schema"
                        {}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "users"}})))))

(deftest load-entities-test
  (testing "Loading entities into a new database"
    (api-request :post "/load-entities"
                 {:entities [[100 :foo 200 1000 true]
                             [101 :foo  300 1000 true]
                             [100 :foo 200 1001 false]
                             [100 :foo 201 1001 true]]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "sessions"}})
    (is (= [[1 :foo 201 536870914 true]
            [2 :foo 300 536870913 true]]
           (api-request :post "/datoms"
                        {:index :eavt
                         :components []}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))

(deftest history-test
  (testing "History with removed entries"
    (add-test-schema)
    (api-request :post "/transact"
                 {:tx-data [{:name "Alice"} {:name "Bob"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "users"}})
    (api-request :post "/transact"
                 {:tx-data [[:db/retractEntity [:name "Alice"]]]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name       "users"}})
    (is (= #{[2 "Alice" false] [2 "Alice" true]}
           (set (api-request :post "/q"
                             {:query '[:find ?e ?n ?s :in $ ?n :where [?e :name ?n _ ?s]]
                              :args ["Alice"]}
                             {:headers {:authorization "token neverusethisaspassword"
                                        :db-name       "users"
                                        :db-history-type "history"}})))))
  (testing "History on non-temporal database"
    (is (= {:message "history is only allowed on temporal indexed databases."}
           (api-request :post "/q"
                        {:query '[:find ?e ?x ?s
                                  :where [?e :foo ?x _ ?s]]}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "sessions"
                                   :db-history-type "history"}})))))


(deftest as-of-test
  (testing "As-of with removed entries"
    (add-test-schema)
    (api-request :post "/transact"
                 {:tx-data [{:name "Alice"} {:name "Bob"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "users"}})
    (api-request :post "/transact"
                 {:tx-data [[:db/retractEntity [:name "Alice"]]]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name       "users"}})
    (let [tx-id (->> (api-request :post "/q"
                                  {:query '[:find ?t :where [?t :db/txInstant _ ?t]]}
                                  {:headers {:authorization "token neverusethisaspassword"
                                             :db-name       "users"}})
                     second
                     first)]
      (is (= #{[2 "Alice"]}
             (set (api-request :post "/q"
                               {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                                :args ["Alice"]}
                               {:headers {:authorization "token neverusethisaspassword"
                                          :db-name       "users"
                                          :db-timepoint tx-id
                                          :db-history-type "as-of"}}))))
      (is (= []
             (api-request :post "/q"
                          {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                           :args ["Alice"]}
                          {:headers {:authorization "token neverusethisaspassword"
                                     :db-name       "users"}})))))
  (testing "As-of on non-temporal database"
    (is (= {:message "as-of is only allowed on temporal indexed databases."}
           (api-request :post "/q"
                        {:query '[:find ?e ?x ?s
                                  :where [?e :foo ?x _ ?s]]}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "sessions"
                                   :db-history-type "as-of"
                                   :db-timepoint 1}})))))
(deftest since-test
  (testing "Since with removed and new entries"
    (add-test-schema)
    (api-request :post "/transact"
                 {:tx-data [{:name "Alice"} {:name "Bob"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "users"}})
    (api-request :post "/transact"
                 {:tx-data [{:name "Charlie"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name       "users"}})
    (let [tx-id (->> (api-request :post "/q"
                                  {:query '[:find ?t :where [?t :db/txInstant _ ?t]]}
                                  {:headers {:authorization "token neverusethisaspassword"
                                             :db-name       "users"}})
                     last
                     first)]
      (is (= #{["Charlie"]}
             (set (api-request :post "/q"
                               {:query '[:find ?n :in $ ?n :where [?e :name ?n]]
                                :args []}
                               {:headers {:authorization "token neverusethisaspassword"
                                          :db-name       "users"
                                          :db-timepoint tx-id
                                          :db-history-type "since"}}))))
      (is (= #{["Alice"] ["Bob"] ["Charlie"]}
             (set (api-request :post "/q"
                               {:query '[:find ?n :in $ ?n :where [?e :name ?n]]
                                :args []}
                               {:headers {:authorization "token neverusethisaspassword"
                                          :db-name       "users"}}))))))
  (testing "Since on non-temporal database"
    (is (= {:message "since is only allowed on temporal indexed databases."}
           (api-request :post "/q"
                        {:query '[:find ?e ?x ?s
                                  :where [?e :foo ?x _ ?s]]}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "sessions"
                                   :db-history-type "since"
                                   :db-timepoint 1}})))))
