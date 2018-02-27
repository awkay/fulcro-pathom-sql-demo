(ns user
  (:require
    [fulcro-spec.suite :as suite]
    [fulcro-spec.selectors :as sel]
    [taoensso.timbre :refer [set-level!]]))

(set-level! :info)

; Run (start-server-tests) in a REPL to start a runner that can render results in a browser
(suite/def-test-suite start-server-tests
  {:config       {:port 8888}
   :test-paths   ["src/test"]
   :source-paths ["src/main"]}
  {:available #{:focused :integration}
   :default   #{::sel/none :focused}})

