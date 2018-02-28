(ns sql-pathom-demo.pathom2-spec
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

(def indexes (-> {}
               (pc/add `account-resolver {::pc/input  #{:account/id}
                                          ::pc/output [:account/name {:account/settings [:settings/id]}]})
               (pc/add `settings-resolver {::pc/input  #{:settings/id}
                                           ::pc/output [:settings/auto-open? :settings/keyboard-shortcuts?]})))

(def parser
  (p/parser {::p/plugins [(p/env-plugin {::p/reader             [p/map-reader pc/all-readers]
                                         ::pc/resolver-dispatch entity-resolver
                                         ::pc/indexes           indexes})]}))

(specification "Pathom with Account and settings" :integration
  (with-database [db test-database]
    (let [{:keys [id/joe id/settings]} (sql/seed! db schema [(sql/seed-row :account {:id :id/joe :name "Joe"})
                                                             (sql/seed-row :settings {:id                 :id/settings
                                                                                      :auto_open          false
                                                                                      :keyboard_shortcuts true})
                                                             (sql/seed-update :account :id/joe {:account/settings_id :id/settings})])
          run-query (fn [q] (parser {:db db} q))]
      (assertions
        "Can find the account"
        (run-query [{[:account/id joe] [:account/name]}])
        => {[:account/id joe] {:account/name "Joe"}}

        "Can find the settings"
        (run-query [{[:settings/id settings] [:settings/auto-open?]}])
        => {[:settings/id settings] {:settings/auto-open? false}}

        "Can follow the graph to the associated settings"
        (run-query [{[:account/id joe] [:account/id :account/name {:account/settings [:settings/auto-open?]}]}])
        => {[:account/id joe] {:account/id       joe :account/name "Joe"
                               :account/settings {:settings/auto-open? false}}}))))

