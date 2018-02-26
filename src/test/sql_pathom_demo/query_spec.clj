(ns sql-pathom-demo.query-spec
  (:require
    [fulcro-sql.test-helpers :refer [with-database]]
    [fulcro-sql.core :as sql]
    [clojure.java.jdbc :as jdbc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [fulcro-spec.core :refer [specification provided behavior assertions]]
    [clojure.set :as set]))

(def test-database {:hikaricp-config "config/db-connection-pool.properties"
                    :auto-migrate?   true
                    :create-drop?    true
                    :migrations      ["classpath:migrations"]})

(def schema
  {::sql/joins      {:account/members         (sql/to-many [:account/id :member/account_id])
                     :account/settings        (sql/to-one [:account/settings_id :settings/id])
                     :account/spouse          (sql/to-one [:account/spouse_id :account/id])
                     :member/account          (sql/to-one [:member/account_id :account/id])
                     :account/invoices        (sql/to-many [:account/id :invoice/account_id])
                     :invoice/account         (sql/to-one [:invoice/account_id :account/id])
                     :invoice/items           (sql/to-many [:invoice/id :invoice_items/invoice_id :invoice_items/item_id :item/id])
                     :item/invoices           (sql/to-many [:item/id :invoice_items/item_id :invoice_items/invoice_id :invoice/id])
                     :todo-list/items         (sql/to-many [:todo_list/id :todo_list_item/todo_list_id])
                     :todo-list-item/subitems (sql/to-many [:todo_list_item/id :todo_list_item/parent_item_id])}
   ::sql/graph->sql {}
   ::sql/pks        {}})

(defmulti entity-resolver (fn [env {:keys [::pc/sym] :as resolver} entity] sym))

(defmethod entity-resolver :default [_ _ _]
  (println :resolving-fail)
  {})

(defmethod entity-resolver `account-resolver
  [{:keys [db]} resolver {:keys [account/id]}]
  (if-let [row (jdbc/query db ["SELECT name, settings_id FROM account WHERE id = ?" id] {:result-set-fn first})]
    (set/rename-keys row {:name :account/name :settings_id :account/settings-id})
    {}))

(def indexes (-> {}
               (pc/add `account-resolver {::pc/input  #{:account/id}
                                          ::pc/output [:account/name :account/settings-id]})))

(def parser
  (p/parser {::p/plugins [(p/env-plugin {::p/reader             [p/map-reader pc/all-readers]
                                         ::pc/resolver-dispatch entity-resolver
                                         ::pc/indexes           indexes})]}))

(specification "Setup Validation" :integration
  (with-database [db test-database]
    (let [{:keys [id/joe]} (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})])
          inserted-row (jdbc/query db ["SELECT name FROM account where id = ?" joe]
                         {:result-set-fn first})]
      (assertions
        "Can insert and find a seeded account row"
        inserted-row => {:name "Joe"}))))

(specification "Simple pathom resolver with connect" :integration :focused
  (with-database [db test-database]
    (let [{:keys [id/joe]} (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})])
          row (parser {:db db} [{[:account/id joe] [:account/id :account/settings-id :account/name]}])]
      (assertions
        "Can insert and find a seeded account row"
        row => {[:account/id joe] {:account/id joe :account/name "Joe"}}))))
