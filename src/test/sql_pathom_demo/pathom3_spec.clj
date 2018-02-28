(ns sql-pathom-demo.pathom3-spec
  (:require
    [fulcro-sql.test-helpers :refer [with-database]]
    [fulcro-sql.core :as sql]
    [clojure.java.jdbc :as jdbc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [fulcro-spec.core :refer [specification provided behavior assertions]]
    [sql-pathom-demo.common :refer [test-database schema]]
    [clojure.set :as set]))

(defmulti entity-resolver (fn r [env entity] (get-in env [::pc/resolver-data ::pc/sym])))

(defmethod entity-resolver :default [_ _] {})

(defmethod entity-resolver `account-resolver
  [{:keys [db]} {:keys [account/id]}]
  (if-let [{:keys [name settings_id]} (jdbc/query db ["SELECT name, settings_id FROM account WHERE id = ?" id] {:result-set-fn first})]
    {:account/id id :account/name name :account/settings {:settings/id settings_id}}
    {}))

(defmethod entity-resolver `settings-resolver
  [{:keys [db]} {:keys [settings/id]}]
  (println :settings id)
  (if-let [row (jdbc/query db ["SELECT auto_open, keyboard_shortcuts FROM settings WHERE id = ?" id] {:result-set-fn first})]
    (set/rename-keys row {:auto_open :settings/auto-open? :keyboard_shortcuts :settings/keyboard-shortcuts?})
    {}))

(defmethod entity-resolver `invoices-that-have-an-item-resolver
  [{{{:keys [item]} :params} :ast :keys [db] :as env} entity]
  (if-let [rows (jdbc/query db ["SELECT DISTINCT invoice_id as invoice_id
                                FROM invoice_items
                                WHERE item_id = ? ORDER BY invoice_id" item])]
    {:invoices/with-item (mapv (fn [r] {:invoice/id (:invoice_id r)}) rows)}
    {}))

(defmethod entity-resolver `invoice-resolver [{:keys [db]} {:keys [invoice/id]}]
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

(defmethod entity-resolver `account-invoices-resolver [{:keys [db]} {:keys [account/id]}]
  (if-let [rows (jdbc/query db ["SELECT id FROM invoice WHERE account_id = ?" id])]
    {:account/invoices (mapv (fn [r] {:invoice/id (:id r)}) rows)}
    {}))

(def indexes (-> {}
               (pc/add `account-resolver {::pc/input  #{:account/id}
                                          ::pc/output [:account/name {:account/settings [:settings/id]}]})
               (pc/add `invoices-that-have-an-item-resolver {::pc/output [{:invoices/with-item [:invoice/id]}]})
               (pc/add `invoice-resolver {::pc/input  #{:invoice/id}
                                          ::pc/output [{:invoice/account [:account/id]}
                                                       {:invoice/items [:item/id :item/quantity :item/name]}]})
               (pc/add `account-invoices-resolver {::pc/input  #{:account/id}
                                                   ::pc/output [{:account/invoices [:invoice/id]}]})
               (pc/add `settings-resolver {::pc/input  #{:settings/id}
                                           ::pc/output [:settings/auto-open? :settings/keyboard-shortcuts?]})))

(def parser
  (p/parser {::p/plugins [(p/env-plugin {::p/reader             [p/map-reader pc/all-readers]
                                         ::pc/resolver-dispatch entity-resolver
                                         ::pc/indexes           indexes})]}))

(specification "Pathom with complex many-to-many joins" :integration
  (with-database [db test-database]
    (let [{:keys [id/joe id/item-2 id/invoice-1 id/invoice-2]}
          (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})
                                (sql/seed-row :settings {:id :id/settings :auto_open true})
                                (sql/seed-update :account :id/joe {:settings_id :id/settings})
                                (sql/seed-row :item {:id :id/item-1 :name "Widget 1"})
                                (sql/seed-row :item {:id :id/item-2 :name "Widget 2"})
                                (sql/seed-row :invoice {:id :id/invoice-1 :account_id :id/joe})
                                (sql/seed-row :invoice {:id :id/invoice-2 :account_id :id/joe})
                                (sql/seed-row :invoice_items {:id :id/ii1-1 :quantity 2 :invoice_id :id/invoice-1 :item_id :id/item-1})
                                (sql/seed-row :invoice_items {:id :id/ii1-2 :quantity 8 :invoice_id :id/invoice-1 :item_id :id/item-2})
                                (sql/seed-row :invoice_items {:id :id/ii2-1 :quantity 33 :invoice_id :id/invoice-2 :item_id :id/item-1})])
          run-query (fn [q] (parser {:db db} q))]
      (assertions
        "Can start at a specific account and navigate to invoices."
        (run-query [{[:account/id joe] [:account/name
                                        {:account/settings [:settings/auto-open?]}
                                        {:account/invoices [{:invoice/items [:item/name :item/quantity]}]}]}])
        => {[:account/id joe] {:account/name     "Joe"
                               :account/settings {:settings/auto-open? true}
                               :account/invoices [{:invoice/items [{:item/name "Widget 1" :item/quantity 2}
                                                                   {:item/name "Widget 2" :item/quantity 8}]}
                                                  {:invoice/items [{:item/name "Widget 1" :item/quantity 33}]}]}}
        "Can find all invoices that contain a given item"
        (run-query `[{(:invoices/with-item {:item ~item-2}) [:invoice/id
                                                             {:invoice/items [:item/quantity :item/name]}]}])
        => {:invoices/with-item [{:invoice/id    invoice-1
                                  :invoice/items [{:item/quantity 2 :item/name "Widget 1"}
                                                  {:item/quantity 8 :item/name "Widget 2"}]}]}
        "Can navigate backwards from an invoice to an account"
        (run-query `[{(:invoices/with-item {:item ~item-2}) [:invoice/id
                                                             {:invoice/account [:account/name {:account/settings [:settings/auto-open?]}]}]}])
        => {:invoices/with-item [{:invoice/id      invoice-1
                                  :invoice/account {:account/name "Joe" :account/settings {:settings/auto-open? true}}}]}))))
