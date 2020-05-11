(ns datahike-dashboard.core
  (:require [ajax.core :refer [GET PUT DELETE POST]]
            [cljs.reader :refer [read-string]]
            ["@material-ui/core" :refer [Container Typography AppBar Toolbar IconButton]]
            ["@material-ui/icons/Menu" :as MenuIcon]
            [reagent.core :as r]))

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
                  :db/noHistory {:db/valueType :db.type/boolean
                                 :db/unique :db.unique/identity
                                 :db/cardinality :db.cardinality/one}
                  :db/doc {:db/valueType :db.type/string
                           :db/index true
                           :db/cardinality :db.cardinality/one}})

(def state (r/atom {:schema core-schema
                    :last-datoms []
                    :tx-input [{:db/id -1}]
                    :notifications []
                    :last-q nil}))

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
    :db.type/boolean pos?
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
                      (swap! state assoc-in [:tx-input] [{:db/id -1}])
                      (swap! state update-in [:notifications] conj {:header "Transaction result" :body [:code (str r)]}))
           :params {:tx-data typed-data}
           :headers {"Content-Type" "application/transit+json"
                     "Accept" "application/transit+json"}})))

(defn fetch-schema []
  (GET "http://localhost:3000/schema"
       {:handler (fn [r] (swap! state update-in [:schema] #(merge r core-schema)))
        :headers {"Content-Type" "application/transit+json"
                  "Accept" "application/transit+json"}}))


#_(defn toast [header body]
  [:> Toast {:show true
             :autohide true
             :delay 5000
             :onClose (fn [] (swap! state update :notifications (fn [old] (remove #(= {:header header :body body}) old))))}
   [:> (.-Header Toast) header]
   [:> (.-Body Toast) body]])

#_(defn datoms-page []
  [:> Container
   [:h1 "Datoms"]
   [:> Table
    [:thead
     [:tr
      [:th "Entity"]
      [:th "Attribute"]
      [:th "Value"]
      [:th "Transaction"]]]
    [:tbody
     (doall
      (for [[e a v t _] (:last-datoms @state)]
        ^{:key (str "datoms-" e "-" a "-" v)}
        [:tr
         [:td e]
         [:td a]
         [:td v]
         [:td t]]))]]]
  )

#_(defn transactions-page [tx-type]
  (let [local-state (r/atom {:selected-type nil})]
    (fn []
      (let [table-headers (->> (:schema @state)
                               keys
                               (filter keyword?)
                               ((case tx-type
                                  :data remove
                                  :schema filter
                                  remove) (into #{} (keys core-schema)))
                               (#(conj % :db/id))
                               (into #{})
                               vec)
            user-schema? (-> table-headers count pos?)]
        [:> Container {:fluid true}
         [:h1 "Transactions"]
         [:> Table {:responsive true
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
                                :on-change #(swap! state assoc-in [:tx-input i th]
                                                   (case input-type
                                                     "checkbox" (.. % -target -checked)
                                                     (.. % -target -value)))}]))]))
               [:td [:> Button {:variant "danger"
                                :color "danger"
                                :on-click #(swap! state update-in [:tx-input] (fn [old]
                                                                                (vec (concat (subvec old 0 i)
                                                                                             (subvec old (inc i))))))}
                     "Delete"]]]))]]
         [:> Button {:variant "secondary"
                     :color "secondary"
                     :on-click (fn [_]
                                 (swap! state update-in [:tx-input] #(conj % {:db/id (- (inc (count %)))})))}
          "Add another entity"]
         [:> Button {:variant "primary"
                     :color "primary"
                     :on-click #(transact (:tx-input @state))} "Transact"]
         ]))))

#_(defn schema-page []
  (let [schema (:schema @state)
        table-headers [:db/ident :db/valueType :db/cardinality :db/doc :db/index :db/unique :db/noHistory :db/isComponent]
        schema-attrs (->> schema
                          keys
                          (filter keyword?)
                          (remove (into #{} (keys core-schema)))
                          vec)]
    [:> Container
     [:h1 "Schema"]
     [:> Table
      [:thead
       [:tr
        (for [th table-headers] ^{:key (str "schema-h-" th)} [:th th])]]
      [:tbody
       (for [attr schema-attrs]
         ^{:key (str "schema-a" attr)}
         [:tr
          (doall
           (for [th table-headers]
             ^{:key (str "schema-a-" attr "-" th)}
             [:td
              (if (= (get-in core-schema [attr]) :db.type/boolean)
                (when-let [v (get-in schema [attr th])]
                  [:input {:type :checkbox
                           :value v
                           :disabled true}])
                (str (get-in schema [attr th] "")))]))])]]]))

#_(defn nav-item
  [child]
  [:> (.-Item Nav) child])

#_(defn nav-link
  ([event-key title]
   [nav-link event-key title nil])
  ([event-key title callback]
   [nav-item [:> (.-Link Nav) {:eventKey event-key :on-click callback} title]]))

#_(defn nav-header [title]
  [nav-item [:strong title]])

#_(defn sidebar []
  [:> Nav {:className "justify-content-center flex-column"}
   [nav-header "Transactions"]
   [nav-link :schema-transactions "Schema"]
   [nav-link :data-transactions "Data" fetch-schema]
   [nav-header "Queries"]
   [nav-link :datoms "Datoms" #(all-datoms :eavt)]
   [nav-link :q "Q" #(js/alert "Q!")]
   [nav-link :schema "Schema" fetch-schema]])

#_(defn wrapper-component []
  [:div.wrapper
   [:div { :style {:position :absolute
                   :z-index 99999
                   :bottom 0
                   :left 10}}
    (for [{:keys [header body] :as item} (:notifications @state)]
      ^{:key (str "toast-" (str body)) }
      [toast header body])]
   [:> Navbar {:bg :dark :variant :dark}
    [:> (.-Brand Navbar) "Datahike Dashboard"]
    [:> (.-Toggle Navbar) {:aria-controls :basic-navbar-nav}]]
   [:> (.-Container Tab) {:defaultActiveKey :data-transactions}
    [:> Row
     [:> Col {:md 1} [sidebar]]
     [:> Col {:md 11}
      [:> (.-Content Tab)
       [:> (.-Pane Tab) {:eventKey :schema-transactions} [transactions-page :schema]]
       [:> (.-Pane Tab) {:eventKey :data-transactions} [transactions-page :data]]
       [:> (.-Pane Tab) {:eventKey :datoms} [datoms-page]]
       [:> (.-Pane Tab) {:eventKey :schema} [schema-page]]]]]]])

(defn menu-icon [])

(defn wrapper []
  [:div
   [:> AppBar {:position :static}
    [:> Toolbar
     [:> IconButton {:edge :start :color :inherit :aria-label "menu"}]
     [:> Typography {:variant :h6} "Datahike"]]]
   [:> Container {:maxWidth :sm}]])

(defn init! []
  (print "[main]: initializing...")
  (r/render
   [wrapper]
   (js/document.getElementById "root")))

(defn reload! []
  (println "[main]: reloading...")
  (r/render
   [wrapper]
   (js/document.getElementById "root")))

(comment

  (all-datoms :eavt)

  (POST "http://localhost:3000/q"
        {:handler (fn [r] (swap! state assoc-in [:last-q] r))
         :params {:query '[:find ?e ?b :where [?e :name ?b]]}
         :headers {"Content-Type" "application/transit+json"
                   "Accept" "application/transit+json"}})

  (init!)

  (reload!)

  (fetch-schema)

  (-> @state :tx-input)

  (-> (:schema @state) :booar)

  (let [table-headers (->> (:schema @state)
                           keys
                           (filter keyword?)
                           (filter (into #{} (keys core-schema)))
                           (#(conj % :db/id))
                           (into #{})
                           vec)
        schema-attrs ]
    )

  (->> (:schema @state)
       keys
       (filter keyword?)
       (remove (into #{} (keys core-schema)))
       (into #{})
       vec)



  

)

