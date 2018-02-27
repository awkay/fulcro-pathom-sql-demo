(ns sql-pathom-demo.common
  (:require [fulcro-sql.core :as sql]))

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

