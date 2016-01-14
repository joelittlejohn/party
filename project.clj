(defproject mixradio/party "1.0.4"

  :description "A Clojure library that wraps the Curator service discovery/registration API."

  :license "https://github.com/mixradio/party/blob/master/LICENSE"

  :url "https://github.com/mixradio/party"

  :dependencies [[cheshire "5.5.0"]
                 [com.cemerick/url "0.1.1"]
                 [environ "1.0.0"]
                 [org.flatland/useful "0.11.3"]
                 [org.apache.curator/curator-x-discovery "2.7.0" :exclusions [org.slf4j/slf4j-log4j12]]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [zookeeper-clj "0.9.3"]]

  :profiles {:dev {:dependencies [[zookem "0.1.2"]
                                  [midje "1.7.0"]
                                  [org.slf4j/log4j-over-slf4j "1.7.12"]
                                  [org.slf4j/slf4j-simple "1.7.12"]]
                   :plugins [[lein-midje "3.0.1"]
                             [codox "0.8.9"]]
                   :jvm-opts ["-Dorg.slf4j.simpleLogger.defaultLogLevel=ERROR"]}})
