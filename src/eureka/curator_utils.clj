(ns eureka.curator-utils
  (:require [clojure.string :refer [lower-case]])
  (:import [java.util UUID]
           [org.apache.curator.framework CuratorFrameworkFactory]
           [org.apache.curator.retry RetryOneTime]
           [org.apache.curator.x.discovery ServiceDiscoveryBuilder ServiceInstance UriSpec]))

(defn curator-framework [connection-string]
  (doto (CuratorFrameworkFactory/newClient connection-string (RetryOneTime. 1))
    (.start)))

(defn service-discovery [curator-framework environment-name]
  (doto (.. ServiceDiscoveryBuilder
            (builder Void)
            (client (.usingNamespace curator-framework (lower-case environment-name)))
            (basePath "/instances")
            (build))
    (.start)))

(defn service-instance [s]
  (.. ServiceInstance
      (builder)
      (id (str (UUID/randomUUID)))
      (name (:name s))
      (port (:port s))
      (uriSpec (UriSpec. (:uri-spec s)))
      (build)))

(defn service-provider [service-discovery name]
  (doto (.. service-discovery
            (serviceProviderBuilder)
            (serviceName name)
            (build))
    (.start)))
