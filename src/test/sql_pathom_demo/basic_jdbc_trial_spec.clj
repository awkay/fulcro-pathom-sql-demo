(ns sql-pathom-demo.basic-jdbc-trial-spec
  (:require
    [fulcro-sql.test-helpers :refer [with-database]]
    [fulcro-sql.core :as sql]
    [clojure.java.jdbc :as jdbc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [fulcro-spec.core :refer [specification provided behavior assertions component]]
    [sql-pathom-demo.common :refer [test-database schema]]
    [clojure.set :as set]))

(specification "Raw JDBC" :integration
  (component "Simple (non-recursive) Queries"
    (with-database [db test-database]
      (let [{:keys [id/joe]} (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})])
            row    (jdbc/query db ["SELECT id, name FROM account where id = ?" joe]
                     {:result-set-fn first})
            result (set/rename-keys row {:id :db/id :name :account/name})]
        (assertions
          "Can insert and find a seeded account row"
          result => {:db/id joe :account/name "Joe"})))))
