(defproject eureka "0.1.10"

  :description "A Clojure library that wraps the Curator service discovery/registration API."

  :dependencies [[cheshire "5.3.1"]
                 [environ "0.5.0"]
                 [org.flatland/useful "0.11.2"]
                 [org.apache.curator/curator-x-discovery "2.7.0" :exclusions [org.slf4j/slf4j-log4j12]]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.3.0"]
                 [zookeeper-clj "0.9.3"]]

  :profiles {:dev {:dependencies [[zookem "0.1.2"]
                                  [midje "1.6.3"]
                                  [org.slf4j/log4j-over-slf4j "1.7.7"]
                                  [org.slf4j/slf4j-simple "1.7.7"]]
                   :plugins [[lein-midje "3.0.1"]
                             [lein-release "1.0.5"]
                             [codox "0.8.9"]]
                   :jvm-opts ["-Dorg.slf4j.simpleLogger.defaultLogLevel=ERROR"]}}

  :lein-release {:deploy-via :clojars
                 :clojars-url "clojars@clojars.brislabs.com:"})
