(defproject sql-pathom-demo "0.1.0-SNAPSHOT"
  :description "My Cool Project"
  :license {:name "MIT" :url "https://opensource.org/licenses/MIT"}
  :min-lein-version "2.7.0"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [fulcrologic/fulcro "2.2.1"]
                 [com.wsscode/pathom "2.0.0-beta2-SNAPSHOT"]
                 [fulcrologic/fulcro-sql "0.3.2"]
                 [org.clojure/java.jdbc "0.7.5"]
                 [com.h2database/h2 "1.4.196"]

                 ; logging all over timbre
                 [com.taoensso/timbre "4.10.0"]
                 [org.slf4j/log4j-over-slf4j "1.7.25" :scope "provided"]
                 [org.slf4j/jul-to-slf4j "1.7.25" :scope "provided"]
                 [org.slf4j/jcl-over-slf4j "1.7.25" :scope "provided"]
                 [com.fzakaria/slf4j-timbre "0.3.7" :scope "provided"]

                 ; testing
                 [fulcrologic/fulcro-spec "2.0.3" :scope "test" :exclusions [fulcrologic/fulcro]]]

  :source-paths ["src/dev"]
  :test-paths ["src/test"]

  :test-refresh {:report       fulcro-spec.reporters.terminal/fulcro-report
                 :with-repl    true
                 :changes-only true}

  :jvm-opts ["-XX:-OmitStackTraceInFastThrow" "-client" "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"
             "-Xmx1g" "-XX:+UseConcMarkSweepGC" "-XX:+CMSClassUnloadingEnabled" "-Xverify:none"]

  :plugins [[com.jakemccrary/lein-test-refresh "0.21.1"]])
