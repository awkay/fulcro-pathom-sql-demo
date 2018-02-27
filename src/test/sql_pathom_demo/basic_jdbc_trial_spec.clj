(ns sql-pathom-demo.basic-jdbc-trial-spec
  (:require
    [fulcro-sql.test-helpers :refer [with-database]]
    [fulcro-sql.core :as sql]
    [clojure.java.jdbc :as jdbc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [fulcro-spec.core :refer [specification provided behavior assertions]]
    [sql-pathom-demo.common :refer [test-database schema]]
    [clojure.set :as set]))

(specification "Setup Validation" :integration
  (with-database [db test-database]
    (let [{:keys [id/joe]} (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})])
          inserted-row (jdbc/query db ["SELECT name FROM account where id = ?" joe]
                         {:result-set-fn first})]
      (assertions
        "Can insert and find a seeded account row"
        inserted-row => {:name "Joe"}))))
