(ns party.discovery
  "Service discovery via Curator."
  (:require [party.curator-utils :as c])
  (:require [clojure.core.cache :as cache]
            [clojure.string :refer [lower-case blank?]]
            [clojure.tools.logging :refer [warn]]
            [clojure.walk :refer [stringify-keys]]
            [environ.core :refer [env]]))

(def ^:dynamic *curator-framework* nil)

(def ^:private ^:dynamic *service-discovery* nil)

(def ^:private service-provider-cache (atom nil))

(defn disconnect!
  "Disconnect from service discovery, closing any connection to
  Zookeeper."
  []
  (when *curator-framework*
    (.close *curator-framework*)
    (reset! service-provider-cache nil)
    (alter-var-root #'*curator-framework* (constantly nil))
    (alter-var-root #'*service-discovery* (constantly nil))))

(defn connect!
  "Connect to Zookeeper and initialize service discovery"
  ([]
   (connect! (or (env :zookeeper-discovery-connectionstring)
                 (env :zookeeper-connectionstring)
                 (env :environment-zookeeper-connectionstring))
             (env :environment-name)))
  ([connection-string environment-name]
   (disconnect!)
   (reset! service-provider-cache (cache/basic-cache-factory {}))
   (alter-var-root #'*curator-framework* (constantly (c/curator-framework connection-string)))
   (alter-var-root #'*service-discovery* (constantly (c/service-discovery *curator-framework* (lower-case environment-name))))))

(defn ^:private override-url [name]
  (env (keyword (str "discovery-override-" name))))

(defn service-provider
  "Create a service provider (object that is able to create service
  instances) for the given service name. A good way to use the returned
  service provider would be:

  (.getInstance service-provider)

  Service providers may be cached and reused, however service instances
  should *never* be reused."
  [name]
  (if-let [override-url (override-url name)]
    (c/fake-service-provider name override-url)
    (c/service-provider *service-discovery* name)))

(defn ^:private service-provider-cached
  [name]
  (if @service-provider-cache
    (do (when-not (cache/has? @service-provider-cache name)
          (swap! service-provider-cache cache/miss name (service-provider name)))
        (cache/lookup @service-provider-cache name))
    (service-provider name)))

(defn healthy?
  "Is service discovery healthy? Are we connected to Zookeeper and able
  to lookup service names using Curator? This is a good thing to add to
  your healthcheck if your service depends on service discovery."
  []
  (boolean
   (try
     (.queryForNames *service-discovery*)
     (catch Exception e
       (warn e "Service discovery failed to get service names from Zookeeper")))))

(defn url
  "Construct a URL by finding an instance and using its scheme, ip,
  port, and uri-spec. The given params will be applied to the uri-spec
  to build a path."
  [target params]
  (let [s (service-provider-cached target)]
    (if-let [instance (.getInstance s)]
      (let [scheme (if (.getSslPort instance) "https" "http")
            port (or (.getSslPort instance) (.getPort instance))
            host (.getAddress instance)
            path (.buildUriSpec instance (stringify-keys params))]
        (format "%s://%s:%s%s" scheme host port path))
      (throw (Exception. (str "Unable to find an instance of " target))))))

(defn url+
  "Construct a URL by finding an instance and using its scheme, ip,
  port, and uri-spec. The given params will be applied to the uri-spec
  to build a path. Finally the given suffix args will be added to the path"
  [target params & suffix]
  (apply str (url target params) suffix))

(defn base-url+
  "Construct a URL by finding an instance and using its schema, ip and
  port to build a base URL. The uri-spec is ignored and suffix is used
  to build a path."
  [target & suffix]
  (let [s (service-provider-cached target)]
    (if-let [instance (.getInstance s)]
      (let [scheme (if (.getSslPort instance) "https" "http")
            port (or (.getSslPort instance) (.getPort instance))
            host (.getAddress instance)]
        (format "%s://%s:%s%s" scheme host port (apply str suffix)))
      (throw (Exception. (str "Unable to find an instance of " target))))))
