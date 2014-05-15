(defproject eureka "0.1.4"

  :description "A Clojure library that wraps the Curator service discovery/registration API."

  :dependencies [[cheshire "5.2.0"]
                 [environ "0.4.0"]
                 [org.apache.curator/curator-x-discovery "2.4.2"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [zookeeper-clj "0.9.3"]]

  :profiles {:dev {:dependencies [[zookem "0.1.0-SNAPSHOT"]
                                  [midje "1.5.1"]]
                   :plugins [[lein-midje "3.0.1"]
                             [lein-release "1.0.5"]]}}

  :lein-release {:deploy-via :clojars
                 :clojars-url "clojars@clojars.brislabs.com:"})
