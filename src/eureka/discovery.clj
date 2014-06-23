(ns eureka.discovery
  "Service discovery via Curator."
  (:require [eureka.curator-utils :as c])
  (:require [clojure.string :refer [lower-case]]
            [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]]))

(declare ^:private ^:dynamic *curator-framework*)

(declare ^:private ^:dynamic *service-discovery*)

(defn disconnect!
  "Disconnect from service discovery, closing any connection to
  Zookeeper."
  []
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

(defn service-provider
  "Create a service provider (object that is able to create service
  instances) for the given service name. A good way to use the returned
  service provider would be:

  (.getInstance service-provider)

  Service providers may be cached and reused, however service instances
  should *never* be reused."
  [name]
  (c/service-provider *service-discovery* name))

(defn healthy?
  "Is service discovery healthy? Are we connected to Zookeeper and able
  to lookup service names using Curator? This is a good thing to add to
  your healthcheck if your service depends on service discovery."
  []
  (try
    (.queryForNames *service-discovery*)
    true
    (catch Exception e
      (warn e "Service discovery failed to get service names from Zookeeper")
      false)))
