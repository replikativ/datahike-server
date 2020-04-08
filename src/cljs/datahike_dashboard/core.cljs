(ns datahike-dashboard.core
  (:require [ajax.core :refer [GET PUT DELETE POST]]
            [cljs.reader :refer [read-string]]
            [reagent.core :as r]
            ["react-bootstrap" :refer [Button Container Form Table Dropdown Row Col DropdownButton]]))

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
                    :last-datoms []
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
    :db.type/cardinality #{:db.cardinality/one :db.cardinality/many}
    :db.type/boolean "checkbox"
    :db.type/unique #{:db.unique/identity :db.unique/value}
    "text"))

(defn schema->type-fn [schema]
  (case schema
    :db.type/string identity
    :db.type/keyword keyword
    :db.type/long js/parseInt
    :db.type/id identity
    :db.type/value #(keyword "db.type" %)
    :db.type/cardinality #(keyword "db.cardinality" %)
    :db.type/index pos?
    :db.type/isComponent pos?
    :db.type/unique #(keyword "db.unique" %)
    identity))



(defn all-datoms [index]
  (POST "http://localhost:3000/datoms"
        {:handler (fn [r]
                    (swap! state assoc-in [:last-datoms] r))
         :params {:index :eavt}
         :headers {"Content-Type" "application/transit+json"
                   "Accept" "application/transit+json"}}))

(defn schema-reducer [entity]
  (reduce (fn [m [k v]]
            (assoc m k ((schema->type-fn (-> @state :schema k :db/valueType)) v))) {} entity))

(defn transact [data]
  (let [typed-data (mapv schema-reducer data)]
    (POST "http://localhost:3000/transact"
          {:handler (fn [r]
                      (swap! state assoc-in [:last-tx] r)
                      (swap! state assoc-in [:tx-input] [{:db/id -1}]))
           :params {:tx-data typed-data}
           :headers {"Content-Type" "application/transit+json"
                     "Accept" "application/transit+json"}})))

(defn fetch-schema []
  (GET "http://localhost:3000/schema"
       {:handler (fn [r] (swap! state update-in [:schema] #(merge r core-schema)))
        :headers {"Content-Type" "application/transit+json"
                  "Accept" "application/transit+json"}}))


(defn datoms-page []
  [:> Container
   [:h1 "Datoms"]
   [:> Table
    [:thead
     [:tr
      [:th "Entity"]
      [:th "Attribute"]
      [:th "Value"]
      [:th "Transaction"]]
     ]
    [:tbody
     (doall
      (for [[e a v t _] (:last-datoms @state)]
        ^{:key (str "datoms-" e "-" a "-" v)}
        [:tr
         [:td e]
         [:td a]
         [:td v]
         [:td t]]))]]])

(defn simple-component []
  (let [local-state (r/atom {:selected-type nil})]
    (fn []
      (let [table-headers (->> (:schema @state)
                               keys
                               (filter keyword?)
                               ((case (:selected-type @local-state)
                                  :user remove
                                  :core filter
                                  remove) (into #{} (keys core-schema)))
                               (#(conj % :db/id))
                               (into #{})
                               vec)
            user-schema? (-> table-headers count pos?)]
        [:> Container
         [:> Row
          [:> Col {:sm 8} [:h1 "Transactions"]]
          [:> Col {:sm 4
                   :className "d-flex justify-content-end align-items-center"}
           [:> DropdownButton {:id "tx-dropdown"
                               :title "Select Transaction type"
                               :variant "secondary"}
            [:> (.-Item Dropdown) {:on-click (fn [e]
                                               (swap! local-state assoc-in [:selected-type] :user)
                                               (fetch-schema))
                                   :active (= (:selected-type @local-state) :user)} "User"]
            [:> (.-Item Dropdown) {:on-click #(do
                                                (println "CLICK0R!")
                                                (swap! local-state assoc-in [:selected-type] :core)
                                                (println @local-state))
                                   :active (= (:selected-type @local-state) :core)} "Core"]]]]
         [:> Table {:responsive :sm
                    :size :sm}
          [:thead
           [:tr
            (if user-schema?
              (for [th table-headers]
                ^{:key (str "tx-attribute-" th)}
                [:th (str th)]))
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
                                     :on-click #(swap! state assoc-in [:tx-input i th] (.. % -target -value))} (str option)]))]
                       [:input {:type input-type
                                :value (-> @state :tx-input (get i) th str)
                                :disabled (= :db/id th)
                                :on-change #(swap! state assoc-in [:tx-input i th] (.. % -target -value))}]))]))
               [:td [:> Button {:variant "danger"
                                :color "danger"
                                :on-click #(swap! state update-in [:tx-input] (fn [old]
                                                                                (vec (concat (subvec old 0 i)
                                                                                             (subvec old (inc i))))))}
                     "Delete"]]]))
           [:tr
            [:td [:> Button {:variant "secondary"
                             :color "secondary"
                             :on-click (fn [_]
                                         (swap! state update-in [:tx-input] #(conj % {:db/id (- (inc (count %)))})))}
                  "Add another entity"]]
            ]]]
         [:> Button {:variant "primary"
                     :color "primary"
                     :on-click #(transact (:tx-input @state))} "Transact"]
         [:br]
         [:p "Transaction result : " ]
         [:code (str (:last-tx @state))]]))))

(defn init! []
  (print "[main]: initializing...")
  (r/render
   [simple-component]
   (js/document.getElementById "root")))

(defn reload! []
  (println "[main]: reloading...")
  (r/render
   [simple-component]
   (js/document.getElementById "root")))

(comment

  (all-datoms :eavt)
  
  (POST "http://localhost:3000/q"
        {:handler (fn [r] (swap! state assoc-in [:last-q] r))
         :params {:query '[:find ?e ?b :where [?e :foo ?b]]}
         :headers {"Content-Type" "application/transit+json"
                   "Accept" "application/transit+json"}})

  (POST "http://localhost:3000/transact"
       {:handler (fn [r] (swap! state assoc-in [:last-tx] r))
        :params {:tx-data [{:db/ident :bar
                            :db/valueType :db.type/long
                            :db/cardinality :db.cardinality/many}
                           {:db/ident :foo
                            :db/valueType :db.type/string
                            :db/cardinality :db.cardinality/one}
                           ]}
        :headers {"Content-Type" "application/transit+json"
                  "Accept" "application/transit+json"}})

  (init!)

  (reload!)

  (fetch-schema)

  (-> @state :tx-input first schema-reducer)

  (-> @state :schema :db/ident)

  (:tx-input @state)


)

