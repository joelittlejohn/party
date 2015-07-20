(ns party.curator-utils
  "Utilities for connecting/reading/writing to Curator service discovery."
  (:require [cemerick.url :as url]
            [clojure.string :refer [lower-case]])
  (:import [java.util UUID]
           [org.apache.curator.framework CuratorFrameworkFactory]
           [org.apache.curator.retry RetryOneTime]
           [org.apache.curator.x.discovery ServiceDiscoveryBuilder ServiceInstance ServiceProvider UriSpec]
           [org.apache.curator.x.discovery.strategies RandomStrategy]))

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
  (-> (ServiceInstance/builder)
      (.id (or (:id s) (str (UUID/randomUUID))))
      (.name (:name s))
      (.port (:port s))
      (cond-> (:uri-spec s) (.uriSpec (UriSpec. (:uri-spec s))))
      (cond-> (:address s) (.address (:address s)))
      (.build)))

(defn service-provider [service-discovery name]
  (doto (.. service-discovery
            (serviceProviderBuilder)
            (serviceName name)
            (providerStrategy (RandomStrategy.))
            (build))
    (.start)))

(defn fake-service-provider [name service-url]
  (let [url-parts (url/url service-url)]
    (reify ServiceProvider
      (getInstance [this]
        (service-instance {:name name
                           :port (:port url-parts)
                           :uri-spec (:path url-parts)
                           :address (:host url-parts)}))
      (close [this]))))
