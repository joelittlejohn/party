(ns eureka.registration
  "Service registration via Curator. Register resources and expose them
  to the outside world via Gatekeeper."
  (:require [eureka.curator-utils :refer :all])
  (:require [cheshire.core :as json]
            [clojure.string :refer [lower-case]]
            [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]]
            [flatland.useful.map :refer [map-to]]))

(def ^:dynamic *curator-framework* nil)

(def ^:private ^:dynamic *service-discoveries* nil)

(def ^:private environments
  "For the time being we may have to register into multiple environments
  (e.g. services in poke are used to service requests in cq1 and cq3)."
  {"dev"  ["dev"]
   "poke" ["poke" "cq1" "cq3"]
   "cq1"  ["cq1"]
   "cq3"  ["poke" "cq3"]
   "prod" ["prod" "live"]
   "live" ["prod" "live"]})

(defn ^:private service-discoveries [environment-name]
  (map-to (partial service-discovery *curator-framework*) (environments (lower-case environment-name))))

(defn disconnect!
  "Disconnect from service registration, closing any connection to
  Zookeeper and removing any registrations for this node."
  []
  (when *curator-framework*
    (.close *curator-framework*)
    (alter-var-root #'*curator-framework* (constantly nil))
    (alter-var-root #'*service-discoveries* (constantly nil))))

(defn connect!
  "Connect to Zookeeper and initialize service registration"
  ([]
     (connect! (env :environment-zookeeper-connectionstring) (env :environment-name)))
  ([connection-string environment-name]
     (disconnect!)
     (alter-var-root #'*curator-framework* (constantly (curator-framework connection-string)))
     (alter-var-root #'*service-discoveries* (constantly (service-discoveries environment-name)))))

(defn unregister!
  "Unregister a service running on this host so that it can no longer by
  discovered by other applications."
  [service]
  (doseq [discovery (vals *service-discoveries*)]
    (try
      (.unregisterService discovery (service-instance service))
      (catch Exception e (warn e "Service discovery failed to unregister")))))

(defn register!
  "Register a service running on this host so that it can be
  discovered by other applications. A typical service might look like:

  {:name \"care\", :port \"8080\", :uri-spec \"/1.x/care/*\"}

  Anything registered will be automatically unregistered when the JVM
  terminates."
  [service]
  (doseq [discovery (vals *service-discoveries*)]
    (.registerService discovery (service-instance service))
    (.. Runtime (getRuntime) (addShutdownHook (proxy [Thread] [] (run [] (unregister! service)))))))

(defn expose!
  "Expose a registered service publicly (through Gatekeeper) so that it can
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
  (doseq [[environment discovery] *service-discoveries*]
    (let [node-path (str "/" environment "/instances/" (:name service))
          node-data (json/generate-string {:path (:uri-spec service)
                                           :methods methods
                                           :restrict restrictions})]
      (.. *curator-framework* setData (forPath node-path (.getBytes node-data "utf-8"))))))
