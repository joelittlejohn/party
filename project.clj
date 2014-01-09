(defproject eureka "0.1.0-SNAPSHOT"

  :description "A Clojure library that wraps the Curator service discovery/registration API."

  :dependencies [[cheshire "5.2.0"]
                 [environ "0.4.0"]
                 [org.apache.curator/curator-x-discovery "2.3.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]]

  :profiles {:dev {:dependencies [[zookem "0.1.0-SNAPSHOT"]
                                  [midje "1.5.1"]]
                   :plugins [[lein-midje "3.0.1"]]}}

  :env {:environment-name "dev"
        :environment-zookeeper-connectionstring "btmgsrvzk001.brislabs.com:2181,btmgsrvzk002.brislabs.com:2181"})
