(ns sql-pathom-demo.intro
  (:require [devcards.core :as rc :refer-macros [defcard]]
            [sql-pathom-demo.ui.components :as comp]))

(defcard SVGPlaceholder
  "# SVG Placeholder"
  (comp/ui-placeholder {:w 200 :h 200}))
