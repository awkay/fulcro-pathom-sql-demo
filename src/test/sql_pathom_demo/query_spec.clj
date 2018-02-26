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

(defmethod entity-resolver :default [_ _ _] {})

(defmethod entity-resolver `account-resolver
  [{:keys [db]} resolver {:keys [account/id]}]
  (if-let [{:keys [name settings_id]} (jdbc/query db ["SELECT name, settings_id FROM account WHERE id = ?" id] {:result-set-fn first})]
    {:account/id id :account/name name :account/settings {:settings/id settings_id}}
    {}))

(defmethod entity-resolver `settings-resolver
  [{:keys [db]} resolver {:keys [settings/id]}]
  (println :settings id)
  (if-let [row (jdbc/query db ["SELECT auto_open, keyboard_shortcuts FROM settings WHERE id = ?" id] {:result-set-fn first})]
    (set/rename-keys row {:auto_open :settings/auto-open? :keyboard_shortcuts :settings/keyboard-shortcuts?})
    {}))

(defmethod entity-resolver `invoices-resolver
  [{{{:keys [have-item]} :params} :ast :keys [db] :as env} resolver entity]
  (if-let [rows (jdbc/query db ["SELECT DISTINCT invoice.id as invoice_id, invoice.account_id as account_id
                                FROM invoice
                                LEFT JOIN invoice_items ON invoice.id = invoice_items.invoice_id
                                WHERE invoice_items.id IN (SELECT id FROM invoice_items WHERE item_id = ? ORDER BY invoice.id)" have-item])]
    {:invoices (mapv (fn [r] {:invoice/id (:invoice_id r)}) rows)}
    {}))

(defmethod entity-resolver `invoice-resolver [{:keys [db]} resolver {:keys [invoice/id]}]
  (if-let [rows (jdbc/query db ["SELECT invoice.account_id as account_id, invoice_items.item_id as item_id, invoice_items.quantity as quantity, item.name as name
                                FROM invoice
                                LEFT JOIN invoice_items ON invoice_items.invoice_id = invoice.id
                                LEFT JOIN item on invoice_items.item_id = item.id
                                WHERE invoice.id = ?" id])]
    {:invoice/account {:account/id (:account_id (first rows))}
     :invoice/items   (mapv (fn [r] {:item/id       (:item_id r)
                                     :item/name     (:name r)
                                     :item/quantity (:quantity r)}) rows)}
    {}))

(def indexes (-> {}
               (pc/add `account-resolver {::pc/input  #{:account/id}
                                          ::pc/output [:account/name {:account/settings [:settings/id]}]})
               (pc/add `invoices-resolver {::pc/output [{:invoices [:invoice/id]}]})
               (pc/add `invoice-resolver {::pc/input  #{:invoice/id}
                                          ::pc/output [{:invoice/account [:account/id]}
                                                       {:invoice/items [:item/id :item/quantity :item/name]}]})
               (pc/add `settings-resolver {::pc/input  #{:settings/id}
                                           ::pc/output [:settings/auto-open? :settings/keyboard-shortcuts?]})))

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

(specification "Simple pathom resolver with connect" :integration
  (with-database [db test-database]
    (let [{:keys [id/joe id/settings]} (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})
                                                             (sql/seed-row :settings {:id                 :id/settings
                                                                                      :auto_open          false
                                                                                      :keyboard_shortcuts true})
                                                             (sql/seed-update :account :id/joe {:account/settings_id :id/settings})])
          row (parser {:db db} [{[:account/id joe] [:account/id :account/name {:account/settings [:settings/auto-open?]}]}])]
      (assertions
        "Can insert and find a seeded account row"
        row => {[:account/id joe] {:account/id       joe :account/name "Joe"
                                   :account/settings {:settings/auto-open? false}}}))))

(specification "Pathom query interpretation with join table data" :integration :focused
  (with-database [db test-database]
    (let [{:keys [id/joe id/item-1 id/invoice-1 id/invoice-2]}
          (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})
                                (sql/seed-row :item {:id :id/item-1 :name "Widget 1"})
                                (sql/seed-row :item {:id :id/item-2 :name "Widget 2"})
                                (sql/seed-row :invoice {:id :id/invoice-1 :account_id :id/joe})
                                (sql/seed-row :invoice {:id :id/invoice-2 :account_id :id/joe})
                                (sql/seed-row :invoice_items {:id :id/ii1-1 :quantity 2 :invoice_id :id/invoice-1 :item_id :id/item-1})
                                (sql/seed-row :invoice_items {:id :id/ii1-2 :quantity 8 :invoice_id :id/invoice-1 :item_id :id/item-2})
                                (sql/seed-row :invoice_items {:id :id/ii2-1 :quantity 33 :invoice_id :id/invoice-2 :item_id :id/item-1})])
          row (parser {:db db} `[({:invoices [:invoice/id
                                              {:invoice/account [:account/name]}
                                              {:invoice/items [:item/quantity :item/name]}]}
                                   {:have-item ~item-1})])]
      (assertions
        "Can insert and find a seeded account row"
        row => {:invoices [{:invoice/id      invoice-1
                            :invoice/account {:account/name "Joe"}
                            :invoice/items   [{:item/quantity 2 :item/name "Widget 1"}
                                              {:item/quantity 8 :item/name "Widget 2"}]}
                           {:invoice/id      invoice-2
                            :invoice/account {:account/name "Joe"}
                            :invoice/items   [{:item/quantity 33 :item/name "Widget 1"}]}]}))))
