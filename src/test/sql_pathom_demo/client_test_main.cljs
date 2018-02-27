(ns sql-pathom-demo.client-test-main
  (:require [fulcro-spec.selectors :as sel]
            [fulcro-spec.suite :as suite]))

(suite/def-test-suite client-tests {:ns-regex #"sql-pathom-demo.*-spec"}
  {:default   #{::sel/none :focused}
   :available #{:focused}})
