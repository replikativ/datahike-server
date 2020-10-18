(ns datahike-server.integration-test-dev-mode
  (:require [clojure.test :refer :all]
            [clojure.edn :as edn]
            [clj-http.client :as client]
            [datahike-server.core :refer [start-all stop-all]]))

(defn parse-body [{:keys [body]}]
  (if-not (empty? body)
    (edn/read-string body)
    ""))

(defn api-request
  ([method url]
   (api-request method url nil nil))
  ([method url data]
   (api-request method url data nil))
  ([method url data opts]
   (-> (client/request (merge {:url (str "http://localhost:3333" url)
                               :method method
                               :content-type "application/edn"
                               :accept "application/edn"}
                              (when (or (= method :post) data)
                                {:body (str data)})
                              opts))
       parse-body)))

(defn setup-db [f]
  (start-all)
  (f)
  (stop-all))

(use-fixtures :once setup-db)

(deftest swagger-test
  (testing "Swagger Json"
    (is (= {:title "Datahike API"
            :description "Transaction and search functions"}
           (:info (api-request :get
                               "/swagger.json"
                               nil))))))

(deftest databases-test
  (testing "Get Databases"
    (is (= {:databases
            [{:store {:id "sessions", :backend :mem},
              :initial-tx [{:name "Alice", :age 20}
                           {:name "Bob", :age 21}]
              :keep-history? false,
              :schema-flexibility :read,
              :name "sessions",
              :index :datahike.index/hitchhiker-tree}
             {:store {:path "/tmp/dh-file", :backend :file},
              :keep-history? true,
              :schema-flexibility :write,
              :name "users",
              :index :datahike.index/hitchhiker-tree}]}
           (api-request :get "/databases"
                        nil)))))

(deftest db-test
  (testing "Get current database as a hash"
    (is (contains? (api-request :get "/db"
                                nil
                                {:headers {:db-name "sessions"}})
                   :tx))
    (is (contains? (api-request :get "/db"
                                nil
                                {:headers {:db-name "users"}})
                   :tx))))

(deftest q-test
  (testing "Executes a datalog query"
    (is (= "Alice"
           (second (first (api-request :post "/q"
                                       {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                                        :args ["Alice"]}
                                       {:headers {:db-name "sessions"}})))))))

(deftest pull-test
  (testing "Fetches data from database using recursive declarative description."
    (is (= {:name "Alice"}
           (api-request :post "/pull"
                        {:selector '[:name]
                         :eid 1}
                        {:headers {:db-name "sessions"}})))))
(deftest pull-many-test
  (testing "Same as pull, but accepts sequence of ids and returns sequence of maps."
    (is (= [{:name "Alice"} {:name "Bob"}]
           (api-request :post "/pull-many"
                        {:selector '[:name]
                         :eids '(1 2 3 4)}
                        {:headers {:db-name "sessions"}})))))

(deftest datoms-test
  (testing "Index lookup. Returns a sequence of datoms (lazy iterator over actual DB index) which components (e, a, v) match passed arguments."
    (is (= 20
           (nth (first (api-request :post "/datoms"
                                    {:index :aevt
                                     :components [:age]}
                                    {:headers {:db-name "sessions"}}))
                2)))))

#_(deftest seek-datoms-test
    (testing "Similar to datoms, but will return datoms starting from specified components and including rest of the database until the end of the index."
      (is (= 20
             (nth (first (api-request :post "/seek-datoms"
                                      {:index :aevt
                                       :components [:age]}
                                      {:headers {:db-name "sessions"}}))
                  2)))))

(deftest tempid-test
  (testing "Allocates and returns an unique temporary id."
    (is (= {:tempid -1000001}
           (api-request :get "/tempid"
                        {}
                        {:headers {:db-name "sessions"}})))))

(deftest entity-test
  (testing "Retrieves an entity by its id from database. Realizes full entity in contrast to entity in local environments."
    (is (= {:age 21 :name "Bob"}
           (api-request :post "/entity"
                        {:eid 2}
                        {:headers {:db-name "sessions"}})))))

(deftest schema-test
  (testing "Fetches current schema"
    (is (= #:db{:ident #:db{:unique :db.unique/identity}}
           (api-request :get "/schema"
                        {}
                        {:headers {:db-name "sessions"}})))))
