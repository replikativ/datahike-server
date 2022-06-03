(ns ^:integration datahike-server.integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [datahike-server.json-utils :as ju]
            [datahike-server.test-utils :refer [api-request setup-db] :as utils]))

(def ^:private basic-header
  {:headers {:authorization "token neverusethisaspassword"}})

(def ^:private sessions-db-header
  (assoc-in basic-header [:headers :db-name] "sessions"))

(def ^:private users-db-header
  (assoc-in basic-header [:headers :db-name] "users"))

(defn add-test-data []
  (let [test-data [{:name "Alice" :age 20} {:name "Bob" :age 21}]]
    (api-request :post "/transact" {:tx-data test-data} sessions-db-header)))

(defn add-test-schema []
  (let [test-schema [{:db/ident :name
                      :db/valueType :db.type/string
                      :db/unique :db.unique/identity
                      :db/cardinality :db.cardinality/one}]]
    (api-request :post "/transact" {:tx-data test-schema} users-db-header)))

(use-fixtures :each setup-db)

(defn swagger-test
  ([]
   (swagger-test false))
  ([json?]
   (testing "Swagger Json"
     (is (= {:title "Datahike API"
             :description "Transaction and search functions"}
            (:info (api-request :get "/swagger.json" nil basic-header false json?)))))))

(deftest swagger-test-edn
  (swagger-test))

(deftest swagger-test-json
  (swagger-test true))

; TODO replace hard-coded expected value
(defn databases-test
  ([] (databases-test false))
  ([json?]
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
            (let [ret (api-request :get "/databases" nil basic-header false json?)]
              (if json?
                (update ret :databases (fn [db] (mapv #(-> (update-in % [:store :backend] keyword)
                                                           (update :schema-flexibility keyword)
                                                           (update :index keyword))
                                                      db)))
                ret)))))))

(deftest databases-test-edn
  (databases-test))

(deftest databases-test-json
  (databases-test true))

(defn- transact-request [body params json-req? json-ret?]
  (api-request :post "/transact" body params json-req? json-ret?))

(defn transact-test-without-schema
  ([] (transact-test-without-schema false false))
  ([json-req? json-ret?]
   (let [body {:tx-data [{:foo 1}]}
         result (transact-request body sessions-db-header json-req? json-ret?)]
     (testing "Transact values on database without schema"
       (is (= {:tx-data [[1 :foo 1 536870913 true]], :tempids #:db{:current-tx 536870913}, :tx-meta []}
              (if json-ret?
                ; TODO use json-utils instead?
                (update result :tx-data utils/keywordise-strs)
                result)))))))

(deftest transact-test-without-schema-edn
  (transact-test-without-schema))

(deftest transact-test-without-schema-json
  (transact-test-without-schema true true))

(deftest transact-test-without-schema-edn-json
  (transact-test-without-schema false true))

(deftest transact-test-without-schema-json-edn
  (transact-test-without-schema true false))

(defn transact-test-not-in-schema
  ([] (transact-test-not-in-schema false false))
  ([json-req? json-ret?]
   (testing "Transact values on database with schema"
     (is (= {:message "Bad entity attribute :foo at {:db/id 1, :foo 1}, not defined in current schema"}
            (transact-request {:tx-data [{:foo 1}]} users-db-header json-req? json-ret?))))))

(deftest transact-test-not-in-schema-edn
  (transact-test-not-in-schema))

(deftest transact-test-not-in-schema-json
  (transact-test-not-in-schema true true))

(deftest transact-test-not-in-schema-edn-json
  (transact-test-not-in-schema false true))

(deftest transact-test-not-in-schema-json-edn
  (transact-test-not-in-schema true false))

(defn compare-retract-attr [result expected]
  (let [retracted (-> result :tx-data rest)]
    (is (every? #(not %) (map last retracted)))
    (is (= expected (into #{} (map #(-> (subvec % 0 3)
                                        (update 1 keyword))
                                   retracted))))))

(defn transact-test
  ([] (transact-test false false))
  ([json-req? json-ret?]
   (let [schema [{:db/ident       :name
                  :db/cardinality :db.cardinality/one
                  :db/index       true
                  :db/unique      :db.unique/identity
                  :db/valueType   :db.type/string}
                 {:db/ident       :parents
                  :db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}
                 {:db/ident       :age
                  :db/cardinality :db.cardinality/one
                  :db/valueType   :db.type/long}]
         xf-datom-vec (partial ju/xf-tx-data-vec [:parents] [:age] ju/keyword-valued-schema-attrs #{})
         transact-request-wrapper (fn [body] (transact-request body users-db-header json-req? json-ret?))
         trim-result (fn [result] (->> result
                                       :tx-data
                                       (remove #(= (nth % 0) (nth % 3)))
                                       (map #(subvec % 0 3))))]
     (testing "Schema transaction"
       (is (= #{[1 :db/index true]
                [1 :db/unique :db.unique/identity]
                [1 :db/valueType :db.type/string]
                [1 :db/cardinality :db.cardinality/one]
                [1 :db/ident :name]
                [2 :db/valueType :db.type/ref]
                [2 :db/cardinality :db.cardinality/many]
                [2 :db/ident :parents]
                [3 :db/valueType :db.type/long]
                [3 :db/cardinality :db.cardinality/one]
                [3 :db/ident :age]}
              (into #{} (cond->> (trim-result (transact-request-wrapper {:tx-data schema}))
                          json-ret? (map #(xf-datom-vec % false)))))))
     (testing "Transact new entity as map"
       (is (= #{[4 :age 20]
                [4 :name "Alice"]}
              (into #{} (cond->> (trim-result (transact-request-wrapper {:tx-data [{:name "Alice" :age  20}]}))
                          json-ret? (map #(xf-datom-vec % false)))))))
     (testing "Transact new entity as vector"
       (is (= #{[5 :age 21]
                [5 :name "Bob"]}
              (into #{} (cond->> (trim-result (transact-request-wrapper {:tx-data [[:db/add -1 :name "Bob"]
                                                                                   [:db/add -1 :age 21]]}))
                          json-ret? (map #(xf-datom-vec % false)))))))
     (testing "Transact new entity as map with lookup ref for reference attribute"
       (is (= #{[6 :age 5]
                [6 :name "Chris"]
                [6 :parents 4]}
              (into #{} (cond->> (trim-result (transact-request-wrapper
                                               {:tx-data [{:name "Chris" :age 5 :parents [:name "Alice"]}]}))
                          json-ret? (map #(xf-datom-vec % false)))))))
     (testing "Add reference attribute to existing entity with lookup refs"
       (is (= #{[6 :parents 5]}
              (into #{} (cond->> (trim-result (transact-request-wrapper
                                               {:tx-data [[:db/add [:name "Chris"] :parents [:name "Bob"]]]}))
                          json-ret? (map #(xf-datom-vec % false)))))))
     (testing "Retract one value of a cardinality-many attribute"
       (compare-retract-attr (transact-request-wrapper {:tx-data [[:db/retract 6 :parents 5]]})
                             #{[6 :parents 5]}))
     (testing "Retract a cardinality-many attribute"
       (compare-retract-attr (transact-request-wrapper {:tx-data [[:db.fn/retractAttribute 6 :parents]]})
                             #{[6 :parents 4]}))
     (testing "Retract an entity"
       (compare-retract-attr (transact-request-wrapper {:tx-data [[:db/retractEntity 6]]})
                             #{[6 :name "Chris"]
                               [6 :age 5]}))
     (testing "Transact new entity as vector with reference attribute values"
       (is (= #{[7 :age 5]
                [7 :name "Chris"]
                [7 :parents 4]
                [7 :parents 5]}
              (into #{} (cond->> (trim-result
                                  (transact-request-wrapper
                                   {:tx-data [[:db/add -1 :name "Chris"]
                                              [:db/add -1 :age 5]
                                              [:db/add -1 :parents 4]
                                              [:db/add -1 :parents 5]]}))
                          json-ret? (map #(xf-datom-vec % false))))))))))

(deftest transact-test-edn
  (transact-test))

(deftest transact-test-json
  (transact-test true true))

(defn GET-db-should-return-database-data
  ([] (GET-db-should-return-database-data false))
  ([json?]
   (testing "schemaless database should return meta, config, hash, max-tx, and max-eid"
     (let [{:keys [meta] :as db-data} (api-request :get "/db" nil sessions-db-header false json?)]
       (is (= #{:datahike/version
                :konserve/version
                :hitchhiker.tree/version
                :datahike/id
                :datahike/created-at}
              (set (keys meta))))
       (is (= {:config  {:store              {:backend :mem
                                              :id      "sessions"}
                         :schema-flexibility :read
                         :attribute-refs?    false
                         :keep-history?      false
                         :name               "sessions"
                         :cache-size         100000,
                         :index              :datahike.index/hitchhiker-tree
                         :index-config       {:index-b-factor       17
                                              :index-log-size       283
                                              :index-data-node-size 300}}
               :hash    0
               :max-tx  536870912
               :max-eid 0}
              (dissoc db-data :meta)))))
   (testing "schemaful database should return meta, config, hash, max-tx, and max-eid"
     (let [{:keys [meta] :as db-data} (api-request :get "/db" nil users-db-header false json?)]
       (is (= #{:datahike/version
                :konserve/version
                :hitchhiker.tree/version
                :datahike/id
                :datahike/created-at}
              (set (keys meta))))
       (is (= {:config  {:store              {:backend :file
                                              :path    "/tmp/dh-file"}
                         :name               "users"
                         :keep-history?      true
                         :attribute-refs?    false
                         :schema-flexibility :write
                         :cache-size         100000
                         :index :datahike.index/hitchhiker-tree
                         :index-config       {:index-b-factor       17
                                              :index-log-size       283
                                              :index-data-node-size 300}}
               :hash    0
               :max-tx 536870912
               :max-eid 0}
              (dissoc db-data :meta)))))))

(deftest GET-db-should-return-database-data-edn
  (GET-db-should-return-database-data))

(deftest GET-db-should-return-database-data-json
  (GET-db-should-return-database-data true))

(deftest q-test
  (let [query {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
               :args ["Alice"]}]
    (testing "Executes a datalog query"
      (add-test-data)
      (is (= "Alice"
             (second (first (api-request :post "/q" query sessions-db-header))))))))

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
