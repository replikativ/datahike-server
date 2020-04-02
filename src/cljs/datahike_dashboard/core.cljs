(ns datahike-dashboard.core
  (:require [ajax.core :refer [GET PUT DELETE POST]]
            [cljs.reader :refer [read-string]]
            [reagent.core :as r]
            ["react-bootstrap" :refer [Button Container Form Table]]))

(def core-schema {:db/ident {:db/valueType   :db.type/keyword
                             :db/unique      :db.unique/identity
                             :db/cardinality :db.cardinality/one}
                  :db/valueType {:db/valueType   :db.type/value
                                 :db/unique      :db.unique/identity
                                 :db/cardinality :db.cardinality/one}
                  :db/id {:db/valueType   :db.type/id
                          :db/unique      :db.unique/identity
                          :db/cardinality :db.cardinality/one}
                  :db/cardinality {:db/valueType   :db.type/cardinality
                                   :db/unique      :db.unique/identity
                                   :db/cardinality :db.cardinality/one}
                  :db/index {:db/valueType   :db.type/boolean
                             :db/unique      :db.unique/identity
                             :db/cardinality :db.cardinality/one}
                  :db/unique {:db/valueType   :db.type/unique
                              :db/unique      :db.unique/identity
                              :db/cardinality :db.cardinality/one}
                  :db/isComponent {:db/valueType :db.type/boolean
                                   :db/unique :db.unique/identity
                                   :db/cardinality :db.cardinality/one}
                  :db/doc {:db/valueType :db.type/string
                           :db/index true
                           :db/cardinality :db.cardinality/one}})

(def state (r/atom {:last-tx nil
                    :schema nil
                    :tx-input [{:db/id -1}]
                    :last-q nil}))

(def core-schema-keys #{:db/ident :db/valueType :db/cardinality :db/doc :db/unique :db/index})

(defn schema->input-type [schema]
  (case schema
    :db.type/string "text"
    :db.type/long "number"
    :db.type/id "number"
    :db.type/value #{:db.type/bigdec
                     :db.type/bigint
                     :db.type/boolean
                     :db.type/double
                     :db.type/float
                     :db.type/number
                     :db.type/instant
                     :db.type/keyword
                     :db.type/long
                     :db.type/ref
                     :db.type/string
                     :db.type/symbol
                     :db.type/uuid
                     :db.type/value}
    :db/cardinality #{:db.cardinality/one :db.cardinality/many}
    :db/index "checkbox"
    :db/isComponent "checkbox"
    :db/unique #{:db.unique/identity :db.unique/value}
    "text"))

(defn schema->type-fn [schema]
  (case schema
    :db.type/string identity
    :db.type/long js/parseInt
    :db.type/id identity
    :db.type/value keyword
    :db/cardinality keyword
    :db/index pos?
    :db/isComponent pos?
    :db/unique pos?
    identity))

(defn transact [data]
  (let [typed-data (mapv (fn [entity]
                           (reduce (fn [m [k v]]
                                     (assoc m k ((schema->type-fn (-> @state :schema k :db/valueType)) v))) {} entity))
                         data)]
    (POST "http://localhost:3000/transact"
          {:handler (fn [r]
                      (swap! state assoc-in [:last-tx] r)
                      (swap! state assoc-in [:tx-input] [{:db/id -1}]))
             :params {:tx-data typed-data}
             :headers {"Content-Type" "application/transit+json"
                       "Accept" "application/transit+json"}})))

(defn simple-component []
  (let [table-headers (->> (:schema @state)
                           keys
                           (filter keyword?)
                           (remove (into #{} (keys core-schema)))
                           (#(conj % :db/id))
                           vec)
        user-schema? (-> table-headers count pos?)]
    [:> Container
     [:> Table
      [:thead
       [:tr
        (if user-schema?
          (for [th table-headers]
            ^{:key (str "tx-attribute-" th)}
            [:td th]))
        [:td]]]
      [:tbody
       (doall
        (for [i (-> @state :tx-input count range vec)]
          ^{:key (str "tx-input-row-" i)}
          [:tr
           (doall
            (for [th table-headers]
              ^{:key (str "tx-input-" i "-" th)}
              [:td
               (let [input-type (-> @state :schema th :db/valueType schema->input-type)]
                 (if (set? input-type)
                   [:select
                    (doall
                     (for [option input-type]
                       ^{:key (str "tx-input-" i "-" th "-" option)}
                       [:option {:value option
                                 :on-click #(swap! state assoc-in [:tx-input i th] (.. % -target -value))} option]))]
                   [:input {:type input-type
                            :value (-> @state :tx-input (get i) th)
                            :disabled (= :db/id th)
                            :on-change #(swap! state assoc-in [:tx-input i th] (.. % -target -value))}]))]))
           [:td [:> Button {:variant "danger"
                            :color "danger"
                            :on-click #(swap! state update-in [:tx-input] (fn [old]
                                                                            (vec (concat (subvec old 0 i)
                                                                                         (subvec old (inc i))))))}
                 "Delete"]]]))
       [:tr
        [:td [:> Button {:variant "primary"
                         :color "primary"
                         :on-click (fn [_]
                                     (swap! state update-in [:tx-input] #(conj % {:db/id (- (inc (count %)))})))}
              "Add another entity"]]
        ]]]
     [:> Button {:variant "primary"
                 :color "primary"
                 :on-click #(transact (:tx-input @state))} "Engage!"]
     [:br]
     [:p "Transaction result : " ]
     [:code (str (:last-tx @state))]
     ]))

(defn init! []
  (GET "http://localhost:3000/schema"
       {:handler (fn [r] (swap! state update-in [:schema] #(merge core-schema r)))
        :headers {"Content-Type" "application/transit+json"
                  "Accept" "application/transit+json"}})
  (r/render
   [simple-component]
   (js/document.getElementById "root")))

(defn reload! []
  (println "[main]: reloading...")
  (GET "http://localhost:3000/schema"
       {:handler (fn [r] (swap! state update-in [:schema] #(merge core-schema r)))
        :headers {"Content-Type" "application/transit+json"
                  "Accept" "application/transit+json"}})
  (r/render
   [simple-component]
   (js/document.getElementById "root")))

(comment
  

  (POST "http://localhost:3000/q"
        {:handler (fn [r] (swap! state assoc-in [:last-q] r))
         :params {:query '[:find ?e ?b :where [?e :foo ?b]]}
         :headers {"Content-Type" "application/transit+json"
                   "Accept" "application/transit+json"}})

  (POST "http://localhost:3000/transact"
       {:handler (fn [r] (swap! state assoc-in [:last-tx] r))
        :params {:tx-data [{:db/ident :bar
                            :db/valueType :db.type/long
                            :db/cardinality :db.cardinality/many}]}
        :headers {"Content-Type" "application/transit+json"
                  "Accept" "application/transit+json"}})

  (init!)


  (cljs.pprint/pprint (:schema @state))


)

