(ns sql-pathom-demo.pathom1-spec
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
  (let [{:keys [name]} (jdbc/query db ["SELECT name FROM account WHERE id = ?" id] {:result-set-fn first})]
    {:account/id id :account/name name}))

(def indexes (-> {}
               (pc/add `account-resolver {::pc/input  #{:account/id}
                                          ::pc/output [:account/name]})))

(def parser
  (p/parser {::p/plugins [(p/env-plugin {::p/reader             [p/map-reader pc/all-readers]
                                         ::pc/resolver-dispatch entity-resolver
                                         ::pc/indexes           indexes})]}))

(specification "Pathom with Account Resolver" :integration
  (with-database [db test-database]
    (let [{:keys [id/joe]} (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})])
          row (parser {:db db} [{[:account/id joe] [:account/id :account/name]}])]
      (assertions
        "Can find account details"
        row => {[:account/id joe] {:account/id joe :account/name "Joe"}}))))
