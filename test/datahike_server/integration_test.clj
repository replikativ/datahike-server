(ns ^:integration datahike-server.integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [datahike.constants :as dc]
            [datahike.store :as ds]
            [datahike.db :as db]
            [datahike-server.config :as config]
            [datahike-server.database :refer [cfg->store-identity]]
            [datahike-server.json-utils :as ju]
            [datahike-server.test-utils :refer [api-request] :as utils]))

(def ^:private default-cfg {:store {:backend :mem
                                    :id "default"}
                            :schema-flexibility :write
                            :keep-history? true})

(defn- rename-cfg [cfg new-name]
  (assoc-in cfg [:store :id] new-name))

(def ^:private test-cfg
  {:databases [default-cfg
               (-> (assoc default-cfg :schema-flexibility :read)
                   (rename-cfg "schema-on-read"))
               (-> (assoc default-cfg :keep-history? false)
                   (rename-cfg "no-history"))
               (-> (assoc default-cfg :attribute-refs? true)
                   (rename-cfg "attr-refs"))]
   :server {:port  3333
            :join? false
            :loglevel :info
            :dev-mode false
            :token :neverusethisaspassword}})

(def ^:private test-cfg-headers
  (into {}
        (map (fn [cfg] [(or (:id (:store cfg)) ;; mem
                            (:path (:store cfg))) ;; file
                        {:headers {:authorization  "token neverusethisaspassword"
                                   :store-identity (cfg->store-identity cfg)}}])
             (:databases test-cfg))))

(def ^:private basic-header {:headers {:authorization "token neverusethisaspassword"}})

(defn- get-test-cfg [store-identity]
  (reduce (fn [s cfg] (if (= s (or (:id (:store cfg)) ;; mem
                                   (:path (:store cfg)))) ;; file
                        (reduced cfg)
                        s))
          store-identity
          (:databases test-cfg)))

(defn- get-test-header [store-identity]
  (test-cfg-headers store-identity))

(defn- update-header [h cfg]
  (update h :headers #(merge % cfg)))

(def ^:private name-schema {:db/index true
                            :db/ident :name
                            :db/valueType :db.type/string
                            :db/unique :db.unique/identity
                            :db/cardinality :db.cardinality/one})

(def ^:private age-schema {:db/valueType :db.type/long
                           :db/cardinality :db.cardinality/one
                           :db/ident :age})

(def ^:private parents-schema {:db/valueType :db.type/ref
                               :db/cardinality :db.cardinality/many
                               :db/ident :parents})

(def ^:private alias-symbol-schema {:db/unique :db.unique/identity
                                    :db/valueType :db.type/keyword
                                    :db/cardinality :db.cardinality/one
                                    :db/ident :alias})

(def ^:private name-age-data [{:name "Alice", :age 20} {:name "Bob", :age 21}])

(def ^:private schema-data-map
  [{:schema [name-schema]
    :data [{:name "Alice"} {:name "Bob"}]}
   {:schema [name-schema age-schema]
    :data name-age-data}
   {:schema [name-schema age-schema parents-schema]
    :data (conj name-age-data {:name "Chris", :age 6, :parents [[:name "Alice"] [:name "Bob"]]})}])

(defn- transact-request
  ([data params] (transact-request data params false false))
  ([data params json-req? json-ret?] (api-request :post "/transact" {:tx-data data} params json-req? json-ret?)))

(defn- add-test-schema-and-data
  ([schema-data header]
   (add-test-schema-and-data schema-data header true))
  ([{:keys [schema data]} header schema?]
   (when schema? (transact-request schema header))
   (transact-request data header)))

(def ^:private max-system-schema-eid (.-e (last db/ref-datoms)))

(defn- get-db-schema-ident-datoms [db-schema-datoms]
  (let [ident-eid (:db/id (:db/ident dc/ref-implicit-schema))]
    (filter #(= (nth % 1) ident-eid) db-schema-datoms)))

(defn- schema-ident-datoms-to-refs [db-schema-ident-datoms]
  (reduce (fn [m d] (assoc m (nth d 2) (first d)))
          (db/get-ident-ref-map dc/ref-implicit-schema)
          db-schema-ident-datoms))

(defn get-schema-ident-refs [schema-tx-result json?]
  (let [schema-ident-datoms (get-db-schema-ident-datoms (rest (:tx-data schema-tx-result)))]
    (schema-ident-datoms-to-refs (if json?
                                   (map #(update % 2 keyword) schema-ident-datoms)
                                   schema-ident-datoms))))

(use-fixtures :each #(utils/setup-db % {:start (constantly (config/load-config test-cfg))
                                        :stop (constantly {})}))

(defn swagger-test
  ([]
   (swagger-test false))
  ([json?]
   (testing "Swagger Json"
     (is (= {:title "Datahike API"
             :description "Transaction and search functions"}
            (:info (api-request :get "/swagger.json" nil basic-header json? json?)))))))

(deftest swagger-test-edn
  (swagger-test))

(deftest swagger-test-json
  (swagger-test true))

(defn databases-test
  ([] (databases-test false))
  ([json?]
   (testing "Get databases"
     (is (= {:databases (map (fn [cfg] (update cfg :attribute-refs? #(or % false))) (:databases test-cfg))}
            (let [ret (api-request :get "/databases" nil basic-header json? json?)
                  ret (if json?
                        (update ret :databases (fn [dbs] (mapv #(-> (update-in % [:store :backend] keyword)
                                                                    (update :schema-flexibility keyword)
                                                                    (update :index keyword))
                                                               dbs)))
                        ret)
                  ret (update ret :databases (fn [dbs] (map #(select-keys % [:keep-history? :attribute-refs?
                                                                             :schema-flexibility :store])
                                                            dbs)))]
              ret))))))

(deftest databases-test-edn
  (databases-test))

(deftest databases-test-json
  (databases-test true))

(defn transact-test-without-schema
  ([] (transact-test-without-schema false false))
  ([json-req? json-ret?]
   (let [result (-> (transact-request [{:foo 1}] (get-test-header "schema-on-read") json-req? json-ret?)
                    (update :tx-data rest))]
     (testing "Transact values on database without schema"
       (is (= {:tx-data [[1 :foo 1 (+ dc/tx0 1) true]]
               :tempids #:db{:current-tx (+ dc/tx0 1)}
               :tx-meta []}
              (if json-ret?
                (update result :tx-data (fn [v] (mapv #(update % 1 keyword) v)))
                result)))))))

(deftest transact-test-without-schema-edn
  (transact-test-without-schema))

(deftest transact-test-without-schema-json
  (transact-test-without-schema true true))

(defn transact-test-not-in-schema
  ([] (transact-test-not-in-schema false false))
  ([json-req? json-ret?]
   (testing "Transact values on database with schema"
     (is (= {:message "Bad entity attribute :foo at {:db/id 1, :foo 1}, not defined in current schema"}
            (transact-request [{:foo 1}] (get-test-header "default") json-req? json-ret?))))))

(deftest transact-test-not-in-schema-edn
  (transact-test-not-in-schema))

(deftest transact-test-not-in-schema-json
  (transact-test-not-in-schema true true))

(defn- compare-retract-attr
  ([result expected]
   (compare-retract-attr result expected nil))
  ([result expected ref-idents]
   (let [retracted (rest (:tx-data result))]
     (is (every? #(not %) (map last retracted)))
     (is (= expected (into #{} (map (fn [d] (-> (subvec d 1 3)
                                                (update 0 #(cond
                                                             (string? %) (keyword %)
                                                             (number? %) (ref-idents %)
                                                             :else %))))
                                    retracted)))))))

(defn transact-test
  ([] (transact-test false false))
  ([json-req? json-ret?]
   (let [test-schema (conj (:schema (last schema-data-map)) alias-symbol-schema)
         vectorized-test-schema (reduce (fn [acc1 attr]
                                          (reduce (fn [acc2 [a v]] (conj acc2 [a v])) acc1 attr))
                                        #{}
                                        test-schema)
         test-schema-datom-count (reduce (fn [sum e] (+ sum (count e))) 0 test-schema)
         trim-result (fn [result] (->> (rest (:tx-data result))
                                       (map #(subvec % 1 3))))
         clojurize-av (fn [v] (ju/handle-id-or-av-pair
                               v {:ref-attrs #{:parents}
                                  :long-attrs #{:age}
                                  :keyword-attrs (conj ju/keyword-valued-schema-attrs :alias)
                                  :symbol-attrs #{}}))
         test-tx-bob (fn [name-a age-a tx-request-wrapper handle-result-fn]
                       (testing "Transact new entity as vector"
                         (is (= #{[:age 21] [:name "Bob"]}
                                (into #{}
                                      (cond->> (handle-result-fn (tx-request-wrapper [[:db/add -1 name-a "Bob"]
                                                                                      [:db/add -1 age-a 21]]))
                                        json-ret? (map clojurize-av)))))))
         test-tx-chris-1 (fn [tx-request-wrapper handle-result-fn alice-id]
                           (testing "Transact new entity as map with reference-valued attribute"
                             (let [tx-data [{:name "Chris" :age 5 :parents alice-id}]]
                               (is (= #{[:age 5]
                                        [:name "Chris"]
                                        [:parents alice-id]}
                                      (into #{} (cond->> (handle-result-fn (tx-request-wrapper tx-data))
                                                  json-ret? (map clojurize-av))))))))
         test-chris-bob (fn [tx-request-wrapper handle-result parents-a bob-id]
                          (testing "Add reference attribute to existing entity with keyword-valued lookup ref"
                            (let [tx-data [[:db/add [:name "Chris"] parents-a [:alias :bob]]]]
                              (is (= #{[:parents bob-id]}
                                     (into #{} (cond->> (handle-result (tx-request-wrapper tx-data))
                                                 json-ret? (map clojurize-av))))))))
         test-retract-cardinality-many (fn [tx-request-wrapper parents-a alice-id ref-idents]
                                         (let [tx-data [[:db.fn/retractAttribute [:name "Chris"] parents-a]]]
                                           (testing "Retract a cardinality-many attribute"
                                             (compare-retract-attr (tx-request-wrapper tx-data)
                                                                   #{[:parents alice-id]}
                                                                   ref-idents))))]
     (testing "Without attribute refs"
       (let [header (get-test-header "default")
             tx-request-wrapper (fn [data] (transact-request data header json-req? json-ret?))
             schema-tx-result (tx-request-wrapper test-schema)
             schema-tx-datoms (rest (:tx-data schema-tx-result))]
         (testing "Schema transaction"
           (testing "Number of transacted datoms is correct"
             (is (= (count schema-tx-datoms) test-schema-datom-count)))
           (testing "Set of transacted datoms is correct"
             (is (= vectorized-test-schema
                    (into #{} (cond->> (trim-result schema-tx-result)
                                json-ret? (map clojurize-av)))))))
         (testing "Transact new entity as map"
           (is (= #{[:name "Alice"] [:age 20]}
                  (into #{} (cond->> (trim-result (tx-request-wrapper [{:name "Alice" :age 20}]))
                              json-ret? (map clojurize-av))))))
         (test-tx-bob :name :age tx-request-wrapper trim-result)
         (let [test-schema-entity-count (count test-schema)
               alice-id (+ test-schema-entity-count 1)
               bob-id (+ test-schema-entity-count 2)]
           (tx-request-wrapper [[:db/add alice-id :alias :alice]])
           (tx-request-wrapper [[:db/add bob-id :alias :bob]])
           (test-tx-chris-1 tx-request-wrapper trim-result alice-id)
           (test-chris-bob tx-request-wrapper trim-result :parents bob-id)
           (testing "Retract one value of a cardinality-many attribute"
             (compare-retract-attr (tx-request-wrapper [[:db/retract [:name "Chris"] :parents bob-id]])
                                   #{[:parents bob-id]}))
           (test-retract-cardinality-many tx-request-wrapper :parents alice-id nil)
           (testing "Retract an entity"
             (compare-retract-attr (tx-request-wrapper [[:db/retractEntity [:name "Chris"]]])
                                   #{[:name "Chris"] [:age 5]})))))
     (testing "With attribute refs"
       (let [header (get-test-header "attr-refs")
             tx-request-wrapper (fn [data] (transact-request data header json-req? json-ret?))
             max-schema-eid (+ max-system-schema-eid (count test-schema))
             schema-tx-result (tx-request-wrapper test-schema)
             schema-tx-datoms (rest (:tx-data schema-tx-result))
             schema-ident-datoms (get-db-schema-ident-datoms schema-tx-datoms)
             schema-ident-datoms (if json-ret?
                                   (map #(update % 2 keyword) schema-ident-datoms)
                                   schema-ident-datoms)
             schema-ident-refs (schema-ident-datoms-to-refs schema-ident-datoms)
             schema-ref-idents (clojure.set/map-invert schema-ident-refs)
             xf-result (fn [result] (->> (trim-result result)
                                         (map #(update % 0 schema-ref-idents))))
             db-schema-eids (map first schema-ident-datoms)]
         (testing "Schema transaction"
           (testing "Minimum transacted schema entity ID is correct"
             (is (= (first db-schema-eids) (+ max-system-schema-eid 1))))
           (testing "Maximum transacted schema entity ID is correct"
             (is (= (last db-schema-eids) max-schema-eid)))
           (testing "Number of transacted datoms is correct"
             (is (= (count schema-tx-datoms)
                    (reduce (fn [sum e] (+ sum (count e))) 0 test-schema))))
           (testing "Set of transacted datoms is correct"
             (is (= vectorized-test-schema
                    (into #{} (cond->> (map (fn [d] (mapv #(if (contains? schema-ref-idents %)
                                                             (schema-ref-idents %)
                                                             %)
                                                          d))
                                            (trim-result schema-tx-result))
                                json-ret? (map clojurize-av)))))))
         (tx-request-wrapper [{:name "Alice" :age 20}])
         (test-tx-bob (:name schema-ident-refs) (:age schema-ident-refs) tx-request-wrapper xf-result)
         (let [alice-id (+ max-schema-eid 1)
               bob-id (+ max-schema-eid 2)
               parents-ref (:parents schema-ident-refs)]
           (tx-request-wrapper [[:db/add alice-id (:alias schema-ident-refs) :alice]])
           (tx-request-wrapper [[:db/add bob-id (:alias schema-ident-refs) :bob]])
           (test-tx-chris-1 tx-request-wrapper xf-result alice-id)
           (test-chris-bob tx-request-wrapper xf-result (:parents schema-ident-refs) bob-id)
           (tx-request-wrapper [[:db/retract [:name "Chris"] parents-ref bob-id]])
           (test-retract-cardinality-many tx-request-wrapper parents-ref alice-id schema-ref-idents)))))))

(deftest transact-test-edn
  (transact-test))

(deftest transact-test-json
  (transact-test true true))

(defn GET-db-should-return-database-data
  ([] (GET-db-should-return-database-data false))
  ([json?]
   (let [meta-keys #{:datahike/version
                     :konserve/version
                     :hitchhiker.tree/version
                     :datahike/id
                     :datahike/created-at}
         update-json-cfg (fn [c] (-> (update c :index keyword)
                                     (update :schema-flexibility keyword)
                                     (update-in [:store :backend] keyword)))
         cfg-others {:attribute-refs? false,
                     :cache-size 100000,
                     :index :datahike.index/hitchhiker-tree,
                     :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}
         db-data-others {:hash    0
                         :max-tx dc/tx0
                         :max-eid 0}]
     (testing "schemaless database should return meta, config, hash, max-tx, and max-eid"
       (let [{:keys [meta] :as db-data} (api-request :get "/db" nil (get-test-header "schema-on-read") false json?)]
         (is (= meta-keys (set (keys meta))))
         (is (= (merge {:config  (merge (get-test-cfg "schema-on-read") cfg-others)}
                       db-data-others)
                (cond-> (dissoc db-data :meta)
                  json? (update :config update-json-cfg))))))
     (testing "schemaful database should return meta, config, hash, max-tx, and max-eid"
       (let [{:keys [meta] :as db-data} (api-request :get "/db" nil (get-test-header "no-history") false json?)]
         (is (= meta-keys (set (keys meta))))
         (is (= (merge {:config  (merge (get-test-cfg "no-history") cfg-others)}
                       db-data-others)
                (cond-> (dissoc db-data :meta)
                  json? (update :config update-json-cfg)))))))))

(deftest GET-db-should-return-database-data-edn
  (GET-db-should-return-database-data))

(deftest GET-db-should-return-database-data-json
  (GET-db-should-return-database-data true))

(defn q-test
  ([] (q-test false false))
  ([json-req? json-ret?]
   (let [ev-q {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
               :args ["Alice"]}
         eav-q {:query '[:find ?e ?a ?v :in $ ?n :where [?e :name ?n] [?e ?a ?v]]
                :args ["Alice"]}
         q `[:find ~'?e :where ([~'?e :name ~(if json-req? ":::not-a-keyword" "::not-a-keyword")]
                                [~'?e :name ~(if json-req? "$$$not-a-symbol" "$$not-a-symbol")]
                                [~'?e :name ~(if json-req? "????" "???")]
                                [~'?e :name "/just-a-string"])]
         or-sym (if json-req? '&or 'or)
         special-q {:query (update q 3 #(apply list (conj % or-sym)))}
         collection-q {:query '[:find [?pn ...] :in $ :where
                                [?ce :name ?cn] [?ce :parents ?pe] [?pe :name ?pn]]}
         scalar-q {:query '[:find ?n . :in $ ?a :where [?e :name ?n] [?e :age ?a]]
                   :args [0]}
         predicate-q {:query (if json-req?
                               '[:find ?n ?a :in $ :where [?e :name ?n] [?e :age ?a] [(&> ?a 20)]]
                               '[:find ?n ?a :in $ :where [?e :name ?n] [?e :age ?a] [(> ?a 20)]])}
         aggregate-q {:query (if json-req?
                               '[:find [(&min ?a) (&max ?a)] :in $ :where [_ :age ?a]]
                               '[:find [(min ?a) (max ?a)] :in $ :where [_ :age ?a]])}
         test-header (get-test-header "default")
         special-chars-tx-data [{:name "::not-a-keyword" :age 1}
                                {:name "$$not-a-symbol" :age 0}
                                {:name "???" :age 2}
                                {:name "/just-a-string" :age 3}]
         q-req-wrapper (fn [query]
                         (api-request :post "/q" query test-header json-req? json-ret? true))]
     (add-test-schema-and-data (last schema-data-map) test-header)
     (transact-request special-chars-tx-data test-header json-req? json-ret?)
     (testing "Simple datalog query for entity ID and person name"
       (is (= #{[4 "Alice"]} (q-req-wrapper ev-q))))
     (testing "Datalog query for entity ID, attribute, and person name"
       (is (contains? (q-req-wrapper eav-q)
                      [4 (if json-ret? "age" :age) 20])))
     (testing "Query containing symbol, for strings containing special characters"
       (is (= #{[7] [8] [9] [10]} (q-req-wrapper special-q))))
     (testing "Collection find spec: fetch parents' names for specified entity"
       (is (= ["Alice" "Bob"] (q-req-wrapper collection-q))))
     (testing "Scalar find spec: fetch name of entity with specified age"
       (is (= "$$not-a-symbol" (q-req-wrapper scalar-q))))
     (testing "Predicate transaction: find entities with age > 20"
       (is (= #{["Bob" 21]} (q-req-wrapper predicate-q))))
     (testing "Aggregate transaction: find minimum and maximum age"
       (is (= [0 21] (q-req-wrapper aggregate-q)))))))

(deftest q-test-edn
  (q-test))

(deftest q-test-json
  (q-test true true))

; [:aka :limit 500] within a map isn't supported in Datahike; use (limit :aka 500) instead
(defn pull-test
  ([] (pull-test false false))
  ([json-req? json-ret?]
   (let [p1 {:selector [:name], :eid 4}
         p2 {:selector '[* {:parents [:name :age]}], :eid [:name "Chris"]}
         limit-list-1 (conj '(:parents 1) (if json-req? '&limit 'limit))
         limit-list-nil (conj '(:parents nil) (if json-req? '&limit 'limit))
         p3 {:selector `[:name :age {~limit-list-1 [:name :age]}]
             :eid [:name "Chris"]}
         p4 {:selector `[:name :age {~limit-list-nil [:name :age]}]
             :eid [:name "Chris"]}
         default-list (conj '(:foo "bar") (if json-req? '&default 'default))
         p5 {:selector `[~default-list]
             :eid 5}
         p6 {:selector [[:foo :default "bar"]]
             :eid 5}
         test-header (get-test-header "default")
         schema-on-read-header (get-test-header "schema-on-read")
         chris-avs {:name "Chris" :age 6}
         parents-avs {:parents [{:age 20, :name "Alice"}
                                {:age 21, :name "Bob"}]}
         pull-req-wrapper (fn [p] (api-request :post "/pull" p test-header json-req? json-ret? true))]
     (add-test-schema-and-data (last schema-data-map) test-header)
     (testing "/pull: Fetches data from database using recursive declarative description."
       (testing "Fetch attribute for specified entity"
         (is (= {:name "Alice"} (pull-req-wrapper p1))))
       (testing "Fetch attributes using wildcard description"
         (is (= (merge {:db/id 6} chris-avs parents-avs)
                (pull-req-wrapper p2))))
       (testing "Fetch cardinality-many attribute with limit 1"
         (is (= (merge chris-avs {:parents [{:age 20, :name "Alice"}]})
                (pull-req-wrapper p3))))
       (testing "Fetch cardinality-many attribute with limit nil"
         (is (= (merge chris-avs parents-avs)
                (pull-req-wrapper p4))))
       (add-test-schema-and-data (last schema-data-map) schema-on-read-header)
       (testing "Fetch non-existent attribute with default value specified in list"
         (is (= {:foo "bar"}
                (api-request :post "/pull" p5 schema-on-read-header json-req? json-ret? true))))
       (testing "Fetch non-existent attribute with default value specified in vector"
         (is (= {:foo "bar"}
                (api-request :post "/pull" p6 schema-on-read-header json-req? json-ret? true))))))))

(deftest pull-test-edn
  (pull-test))

(deftest pull-test-json
  (pull-test true true))

(defn pull-many-test
  ([] (pull-many-test false false))
  ([json-req? json-ret?]
   (let [test-header (get-test-header "schema-on-read")
         data {:selector [:name], :eids '(1 2 3 4)}]
     (testing "/pull-many: Same as pull, but accepts sequence of ids and returns sequence of maps."
       (add-test-schema-and-data (first schema-data-map) test-header)
       (is (= [{:name "Alice"} {:name "Bob"}]
              (api-request :post "/pull-many" data test-header json-req? json-ret? true)))))))

(deftest pull-many-test-edn
  (pull-many-test))

(deftest pull-many-test-json
  (pull-many-test true true))

(defn datoms-test
  ([] (datoms-test false false))
  ([json-req? json-ret?]
   (let [header (get-test-header "attr-refs")
         schema-and-data (last schema-data-map)
         schema-tx-result (transact-request (:schema schema-and-data) header)
         schema-ident-refs (get-schema-ident-refs schema-tx-result false)
         schema-ref-idents (clojure.set/map-invert schema-ident-refs)
         max-schema-eid (+ max-system-schema-eid (count (:schema schema-and-data)))
         alice-eid (+ max-schema-eid 1)
         bob-eid (+ max-schema-eid 2)
         chris-eid (+ max-schema-eid 3)
         request-wrapper (fn [data] (api-request :post "/datoms" data header json-req? json-ret?))
         xf-datom-vec (fn [d]
                        (if json-ret?
                          (let [[a v] (ju/handle-id-or-av-pair (subvec d 1 3)
                                                               {:ref-attrs #{:parents}
                                                                :long-attrs #{:age}
                                                                :keyword-attrs ju/keyword-valued-schema-attrs
                                                                :symbol-attrs #{}})]
                            [(first d) (schema-ref-idents a) v])
                          [(first d) (schema-ref-idents (nth d 1)) (nth d 2)]))
         trim-result (fn [result] (map #(subvec % 0 3) result))]
     (transact-request (:data schema-and-data) header)
     (testing "/datoms with index :eavt, with components eid and attribute keyword"
       (is (= [[alice-eid :name "Alice"]]
              (map xf-datom-vec (request-wrapper {:index :eavt
                                                  :components [alice-eid :name]})))))
     (testing "/datoms with index :eavt, with components entity lookup ref and attribute ref"
       (let [data {:index :eavt
                   :components [[:name "Alice"] (schema-ident-refs :age)]}]
         (is (= [[alice-eid :age 20]]
                (map xf-datom-vec (request-wrapper data))))))
     (testing "/datoms with index :aevt, with components attribute ref, eid and value"
       (let [data {:index :aevt
                   :components [(schema-ident-refs :parents) chris-eid bob-eid]}]
         (is (= [[chris-eid :parents bob-eid]]
                (map xf-datom-vec (request-wrapper data))))))
     (testing "/datoms with index :avet, with components attribute keyword and value"
       (is (= [[chris-eid :parents bob-eid]]
              (map xf-datom-vec
                   (request-wrapper {:index :avet, :components [:parents bob-eid]}))))))))

(deftest datoms-test-edn
  (datoms-test))

(deftest datoms-test-json
  (datoms-test true true))

(defn seek-datoms-test
  ([] (seek-datoms-test false false))
  ([json-req? json-ret?]
   (testing "/seek-datoms: returns datoms starting from specified components and including rest of the database until the end of the index."
     (let [header (get-test-header "schema-on-read")
           _ (add-test-schema-and-data (nth schema-data-map 1) header false)
           data {:index :aevt, :components [:name]}
           result (api-request :post "/seek-datoms" data header json-req? json-ret?)]
       (is (= [[1 :name "Alice"] [2 :name "Bob"]]
              (map #(-> (subvec % 0 3) (update 1 keyword)) (drop-last result))))
       (is (= (+ dc/tx0 1) (first (last result))))))))

(deftest seek-datoms-test-edn
  (seek-datoms-test))

(deftest seek-datoms-test-json
  (seek-datoms-test true true))

(defn tempid-test
  ([] (tempid-test false))
  ([json?]
   (testing "/tempid: allocates and returns an unique temporary id."
     (let [result (api-request :get "/tempid" nil (get-test-header "schema-on-read") json? json?)]
       (is (number? (:tempid result)))))))

(deftest tempid-test-edn
  (tempid-test))

(deftest tempid-test-json
  (tempid-test true))

(defn entity-test
  ([] (entity-test false false))
  ([json-req? json-ret?]
   (let [header (get-test-header "default")
         schema-data (nth schema-data-map 1)
         schema-entity-count (count (:schema schema-data))
         request-wrapper (fn [body] (api-request :post "/entity" body header json-req? json-ret?))]
     (add-test-schema-and-data schema-data header true)
     (testing "/entity: Retrieve an entity from database by id. Realizes full entity in contrast to entity in local environments."
       (is (= {:age 21 :name "Bob"} (request-wrapper {:eid (+ schema-entity-count 2)}))))
     (testing "Retrieve an entity attribute by entity id from database"
       (is (= "Alice" (request-wrapper {:eid (+ schema-entity-count 1) :attr :name}))))
     (testing "Retrieve an entity attribute from database by entity lookup ref"
       (is (= 20 (request-wrapper {:eid [:name "Alice"] :attr :age})))))))

(deftest entity-test-edn
  (entity-test))

(deftest entity-test-json
  (entity-test true true))

(defn- jsonize-schema [json? schema]
  (if json?
    (let [jsonize-val (fn [[k v]]
                        (let [v (if (contains? ju/keyword-valued-schema-attrs k) (keyword v) v)]
                          [k v]))]
      (reduce (fn [m [attr schema]]
                (assoc m attr (into {} (map jsonize-val schema))))
              {}
              schema))
    schema))

(defn schema-test
  ([] (schema-test false))
  ([json?]
   (testing "Fetches current schema"
     (let [header (get-test-header "default")
           schema-data (first schema-data-map)
           schema (:schema schema-data)]
       (add-test-schema-and-data schema-data header)
       (is (= (zipmap (map :db/ident schema)
                      (map #(assoc %1 :db/id %2)
                           schema
                           (map inc (range (count schema)))))
              (->> (api-request :get "/schema" nil header json? json?)
                   (jsonize-schema json?))))))))

(deftest schema-test-edn
  (schema-test))

(deftest schema-test-json
  (schema-test true))

(defn reverse-schema-test
  ([] (reverse-schema-test false))
  ([json?]
   (testing "Fetches current reverse schema"
     (let [header (get-test-header "default")]
       (add-test-schema-and-data (first schema-data-map) header)
       (is (= {:db/ident #{:name}
               :db/unique #{:name}
               :db.unique/identity #{:name}
               :db/index #{:name}}
              (->> (api-request :get "/reverse-schema" nil header json? json?)
                   (reduce (fn [m [k attrs]]
                             (assoc m k (into #{} (map keyword attrs))))
                           {}))))))))

(deftest reverse-schema-test-edn
  (reverse-schema-test))

(deftest reverse-schema-test-json
  (reverse-schema-test true))

(defn index-range-test
  ([] (index-range-test false false))
  ([json-req? json-ret?]
   (testing "/index-range: return `:avet` index between optionally specified`[_ attr start]` and `[_ attr end]`"
     (let [header (get-test-header "default")]
       (add-test-schema-and-data (last schema-data-map) header)
       (testing "Specify attrid only"
         (is (= [[:name "Alice"] [:name "Bob"] [:name "Chris"]]
                (->> (api-request :get "/index-range" {:attrid :name} header json-req? json-ret?)
                     (map #(update (subvec % 1 3) 0 keyword))))))
       (testing "Specify both start and end values"
         (let [data {:attrid :db/ident, :start :age, :end :name}]
           (is (= [[:db/ident :age] [:db/ident :name]]
                  (->> (api-request :get "/index-range" data header json-req? json-ret?)
                       (map #(map keyword (subvec % 1 3))))))))))))

(deftest index-range-test-edn
  (index-range-test))

(deftest index-range-test-json
  (index-range-test true true))

(defn load-entities-test
  ([] (load-entities-test false false))
  ([json-req? json-ret?]
   (testing "/load-entities: loading entities into a new database"
     (let [schema (conj (:schema (nth schema-data-map 1)) alias-symbol-schema)
           schema-entity-count (count schema)
           data [[1 :name "Alice" 1 true]
                 [1 :age 20 1 true]
                 [1 :alias :alice 1 true]
                 [2 :name "Bob" 1 true]
                 [2 :age 21 1 true]
                 [2 :alias :bob 1 true]]
           xf-data-with-schema-to-expected (fn [data eid-shift]
                                             (map (fn [d] (-> (update d 0 #(+ % eid-shift))
                                                              (assoc 3 (+ dc/tx0 2))))
                                                  data))
           filter-datoms-result (fn [result] (filter #(> (nth % 3) (+ dc/tx0 1)) result))]
       (testing "Without attribute refs"
         (testing "Without schema"
           (let [req-data (if json-req?
                            (mapv #(-> (if (keyword? (nth % 2)) (update % 2 str) %)
                                       (update 1 str))
                                  data)
                            data)
                 header (get-test-header "schema-on-read")]
             (api-request :post "/load-entities" {:entities req-data} header json-req? json-ret?)
             (is (= (set (map #(assoc % 3 (+ dc/tx0 1)) data))
                    (set (api-request :post "/datoms" {:index :eavt} header))))))
         (testing "With schema"
           (let [header (get-test-header "default")]
             (transact-request schema header)
             (api-request :post "/load-entities" {:entities data} header json-req? json-ret?)
             (is (= (set (xf-data-with-schema-to-expected data schema-entity-count))
                    (set (filter-datoms-result (api-request :post "/datoms" {:index :eavt} header))))))))
       (testing "With attribute refs"
         (let [header (get-test-header "attr-refs")
               max-schema-eid (+ max-system-schema-eid schema-entity-count)
               schema-tx-result (transact-request schema header)
               schema-ident-refs (get-schema-ident-refs schema-tx-result false)
               data (mapv (fn [d] (update d 1 #(% schema-ident-refs))) data)]
           (api-request :post "/load-entities" {:entities data} header json-req? json-ret?)
           (is (= (set (xf-data-with-schema-to-expected data max-schema-eid))
                  (set (filter-datoms-result (api-request :post "/datoms" {:index :eavt} header)))))))))))

(deftest load-entities-test-edn
  (load-entities-test))

(deftest load-entities-test-json
  (load-entities-test true true))

(defn history-test
  ([] (history-test false false))
  ([json-req? json-ret?]
   (let [schema-data (first schema-data-map)
         header1 (get-test-header "default")
         header1-for-q (assoc-in header1 [:headers :db-history-type] "history")
         q1 {:query '[:find ?e ?n ?s :in $ ?n :where [?e :name ?n _ ?s]]
             :args ["Alice"]}
         header2 (assoc-in (get-test-header "no-history") [:headers :db-history-type] "history")
         q2 {:query '[:find ?e ?x ?s
                      :where [?e :foo ?x _ ?s]]}]
     (testing "History with removed entries"
       (transact-request (:schema schema-data) header1)
       (transact-request (:data schema-data) header1)
       (transact-request [[:db/retractEntity [:name "Alice"]]] header1)
       (is (= #{[2 "Alice" false] [2 "Alice" true]}
              (api-request :post "/q" q1 header1-for-q json-req? json-ret? true))))
     (testing "History on non-temporal database"
       (is (= {:message "history is only allowed on temporal indexed databases."}
              (api-request :post "/q" q2 header2 json-req? json-ret? true)))))))

(deftest history-test-edn
  (history-test))

(deftest history-test-json
  (history-test true true))

(defn as-of-test
  ([] (as-of-test false false))
  ([json-req? json-ret?]
   (let [schema-data (first schema-data-map)
         header1 (get-test-header "default")]
     (testing "As-of with removed entries"
       (transact-request (:schema schema-data) header1)
       (transact-request (:data schema-data) header1)
       (transact-request [[:db/retractEntity [:name "Alice"]]] header1)
       (let [tx-q {:query '[:find ?t :where [?t :db/txInstant _ ?t]]}
             tx-id (first (second (api-request :post "/q" tx-q header1)))
             e-q {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]], :args ["Alice"]}
             header1-as-of (update-header header1 {:db-timepoint tx-id, :db-history-type "as-of"})]
         (is (= #{[2 "Alice"]}
                (api-request :post "/q" e-q header1-as-of json-req? json-ret? true)))
         (is (= #{} (api-request :post "/q" e-q header1 json-req? json-ret? true)))))
     (testing "As-of on non-temporal database"
       (let [q-2 {:query '[:find ?e ?x ?s
                           :where [?e :foo ?x _ ?s]]}
             header2 (update-header (get-test-header "no-history")
                                    {:db-history-type "as-of", :db-timepoint 1})]
         (is (= {:message "as-of is only allowed on temporal indexed databases."}
                (api-request :post "/q" q-2 header2 json-req? json-ret? true))))))))

(deftest as-of-test-edn
  (as-of-test))

(deftest as-of-test-json
  (as-of-test true true))

(defn since-test
  ([] (since-test false false))
  ([json-req? json-ret?]
   (let [schema-data (first schema-data-map)
         header1 (get-test-header "default")]
     (testing "Since with removed and new entries"
       (transact-request (:schema schema-data) header1)
       (transact-request (:data schema-data) header1)
       (transact-request [{:name "Charlie"}] header1)
       (let [tx-q {:query '[:find ?t :where [?t :db/txInstant _ ?t]]}
             tx-id (first (last (api-request :post "/q" tx-q header1)))
             q1 {:query '[:find ?n :in $ ?n :where [?e :name ?n]]
                 :args []}
             header1-since (update-header header1 {:db-timepoint tx-id, :db-history-type "since"})]
         (is (= #{["Charlie"]}
                (api-request :post "/q" q1 header1-since json-req? json-ret? true)))
         (is (= #{["Alice"] ["Bob"] ["Charlie"]}
                (api-request :post "/q" q1 header1 json-req? json-ret? true)))))
     (testing "Since on non-temporal database"
       (let [q2 {:query '[:find ?e ?x ?s
                          :where [?e :foo ?x _ ?s]]}
             header2 (update-header (get-test-header "no-history")
                                    {:db-history-type "since", :db-timepoint 1})]
         (is (= {:message "since is only allowed on temporal indexed databases."}
                (api-request :post "/q" q2 header2 json-req? json-ret? true))))))))

(deftest since-test-edn
  (since-test))

(deftest since-test-json
  (since-test true true))
