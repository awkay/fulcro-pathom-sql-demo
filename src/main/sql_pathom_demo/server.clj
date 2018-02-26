(ns sql-pathom-demo.server
  (:require
    [fulcro.easy-server :refer [make-fulcro-server]]
    ; MUST require these, or you won't get them installed.
    [sql-pathom-demo.api.read]
    [sql-pathom-demo.api.mutations]
    [fulcro-sql.core :as sql]
    [com.stuartsierra.component :as component]))

(defn build-server
  [{:keys [config] :or {config "config/dev.edn"}}]
  (make-fulcro-server
    :components {:databases (component/using (sql/build-db-manager {}) [:config])}
    :parser-injections #{:config :databases}
    :config-path config))



