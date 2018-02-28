(ns sql-pathom-demo.fulcro-sql-trial-spec
  (:require
    [fulcro-sql.test-helpers :refer [with-database]]
    [fulcro-sql.core :as sql]
    [clojure.java.jdbc :as jdbc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [fulcro-spec.core :refer [specification provided behavior assertions]]
    [sql-pathom-demo.common :refer [test-database schema]]
    [clojure.set :as set]))

; Tests for both client and server
(specification "Fulcro SQL Queries" :integration
  (with-database [db test-database]
    (let [{:keys [list-1 item-1 item-2 item-1-1 item-1-1-1 item-2-1 item-2-2 list-2]}
          (sql/seed! db schema [(sql/seed-row :todo_list {:id :list-1 :name "Things to do"})
                                (sql/seed-row :todo_list {:id :list-2 :name "Other things to do"})
                                (sql/seed-row :todo_list_item {:id :item-1 :label "A" :todo_list_id :list-1})
                                (sql/seed-row :todo_list_item {:id :item-1-1 :label "A.1" :parent_item_id :item-1})
                                (sql/seed-row :todo_list_item {:id :item-1-1-1 :label "A.1.1" :parent_item_id :item-1-1})
                                (sql/seed-row :todo_list_item {:id :item-2 :label "B" :todo_list_id :list-1})
                                (sql/seed-row :todo_list_item {:id :item-2-1 :label "B.1" :parent_item_id :item-2})
                                (sql/seed-row :todo_list_item {:id :item-2-2 :label "B.2" :parent_item_id :item-2})])
          simple-query    [:db/id :todo-list/name {:todo-list/items [:db/id :todo-list-item/label]}]
          recursive-query '[:db/id :todo-list/name {:todo-list/items [:db/id :todo-list-item/label {:todo-list-item/subitems ...}]}]
          simple-list     (jdbc/with-db-transaction [atomicdb db {:isolation :serializable}]
                            (sql/run-query atomicdb schema :todo-list/id simple-query #{list-1}))
          recursive-list  (jdbc/with-db-transaction [atomicdb db {:isolation :serializable}]
                            (sql/run-query atomicdb schema :todo-list/id recursive-query #{list-1}))]
      (assertions
        "Simple List"
        simple-list => [{:db/id           list-1
                         :todo-list/name  "Things to do"
                         :todo-list/items [{:db/id item-1 :todo-list-item/label "A"} {:db/id item-2 :todo-list-item/label "B"}]}]
        "Recursive List"
        recursive-list => [{:db/id           list-1
                            :todo-list/name  "Things to do"
                            :todo-list/items [{:db/id                   item-1
                                               :todo-list-item/label    "A"
                                               :todo-list-item/subitems [{:db/id                   item-1-1
                                                                          :todo-list-item/label    "A.1"
                                                                          :todo-list-item/subitems [{:db/id item-1-1-1 :todo-list-item/label "A.1.1"}]}]}
                                              {:db/id                   item-2
                                               :todo-list-item/label    "B"
                                               :todo-list-item/subitems [{:db/id item-2-1 :todo-list-item/label "B.1"}
                                                                         {:db/id item-2-2 :todo-list-item/label "B.2"}]}]}]))))
