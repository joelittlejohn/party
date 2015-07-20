(ns party.registration
  "Service registration via Curator. Register resources and expose them
  to the outside world via Gatekeeper."
  (:require [party.curator-utils :as c])
  (:require [cheshire.core :as json]
            [clojure.string :refer [lower-case]]
            [clojure.tools.logging :refer [info warn]]
            [environ.core :refer [env]]
            [flatland.useful.map :refer [map-to]])
  (:import [java.util.concurrent TimeUnit]))

(def ^:dynamic *curator-framework* nil)

(def ^:private ^:dynamic *environment* nil)

(def ^:private ^:dynamic *service-discovery* nil)

(defn disconnect!
  "Disconnect from service registration, closing any connection to
  Zookeeper and removing any registrations for this node."
  []
  (when *curator-framework*
    (.close *curator-framework*)
    (alter-var-root #'*curator-framework* (constantly nil))
    (alter-var-root #'*service-discovery* (constantly nil))
    (alter-var-root #'*environment* (constantly nil))))

(defn connect!
  "Connect to Zookeeper and initialize service registration"
  ([]
     (connect! (or (env :environment-zookeeper-connectionstring)
                   (env :zookeeper-connectionstring))
               (env :environment-name)))
  ([connection-string environment-name]
   (disconnect!)
   (alter-var-root #'*environment* (constantly (lower-case environment-name)))
   (alter-var-root #'*curator-framework* (constantly (c/curator-framework connection-string)))
   (alter-var-root #'*service-discovery* (constantly (c/service-discovery *curator-framework* *environment*)))))

(defn unregister!
  "Unregister a service running on this host so that it can no longer by
  discovered by other applications.

  If your container is shutting down, there's no need to unregister
  individual services, you can simply call disconnect!."
  [service]
  (try
    (.unregisterService *service-discovery* (c/service-instance service))
    (catch Exception e (warn e "Service discovery failed to unregister"))))

(defn register!
  "Register a service running on this host so that it can be
  discovered by other applications. A typical service might look like:

  {:name \"care\", :port \"8080\", :uri-spec \"/1.x/care/*\"}

  If registering with a healthcheck fn, party will wait one second between
  attempts. If not specified by :party-registration-attempts, there will be
  10 attempts to register before an exception is thrown."
  ([service]
   (register! service (constantly true)))
  ([service healthy?]
   (register! service healthy? (Integer/valueOf (env :party-registration-attempts "10"))))
  ([service healthy? attempts]
   (when (< attempts 1)
     (throw (Exception. (str "Failed to register service: " service))))
   (if (healthy?)
     (let [instance (c/service-instance service)]
       (.registerService *service-discovery* instance)
       (. instance getId))
     (do (info "Not yet healthy, can't register" service)
         (.sleep TimeUnit/SECONDS 1)
         (recur service healthy? (dec attempts))))))

(defn expose!
  "**NOT YET SUPPORTED**

  Expose a registered service publicly (through Gatekeeper) so that it can
  be accessed by clients via api/sapi/private domains. Be sure to think
  hard about what restrictions to apply to your resource *before*
  exposing it.

  An example for a careops resource:

  (expose! service [:get :post] {:role [\"care\"]
                                 :domain [\"private\"]
                                 :ip [\"internal\"]})

  An example user-authenticated resource (supports oauth1, noa oauth2,
  jagus oauth2, etc):

  (expose! service [:get :post] {:role [\"user\"]})

  *Note*: You must (register!) your service before it can be exposed."
  [service methods restrictions]
  {:pre [(:uri-spec service) (seq methods)]}
  (let [node-path (str "/" *environment* "/instances/" (:name service))
        node-data (json/generate-string {:path (:uri-spec service)
                                         :methods methods
                                         :restrict restrictions})]
    (.. *curator-framework* setData (forPath node-path (.getBytes node-data "utf-8")))))

(defn healthy?
  "Is service registration healthy? Are we able to connect to Zookeeper "
  []
  (boolean
    (try
      (.queryForNames *service-discovery*)
      (catch Exception e
        (warn e "Service discovery failed to get service names from Zookeeper")))))
