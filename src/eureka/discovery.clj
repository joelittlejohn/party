(ns eureka.discovery
  (:require [eureka.curator-utils :as c])
  (:require [clojure.string :refer [lower-case]]
            [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]]))

(declare ^:private ^:dynamic *curator-framework*)

(declare ^:private ^:dynamic *service-discovery*)

(defn disconnect! []
  (when (bound? #'*curator-framework*)
    (.close *curator-framework*)
    (.unbindRoot #'*curator-framework*)
    (.unbindRoot #'*service-discovery*)))

(defn connect!
  "Connect to Zookeeper and initialize service discovery"
  ([]
     (connect! (env :environment-zookeeper-connectionstring) (env :environment-name)))
  ([connection-string environment-name]
     (disconnect!)
     (alter-var-root #'*curator-framework* (constantly (c/curator-framework connection-string)))
     (alter-var-root #'*service-discovery* (constantly (c/service-discovery *curator-framework* (lower-case environment-name))))))

(defn service-provider [name]
  (c/service-provider *service-discovery* name))

(defn healthy? []
  (try
    (.queryForNames *service-discovery*)
    true
    (catch Exception e
      (warn e "Service discovery failed to get service names from Zookeeper")
      false)))
