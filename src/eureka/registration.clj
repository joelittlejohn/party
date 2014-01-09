(ns eureka.registration
  (:require [eureka.curator-utils :refer :all])
  (:require [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]]))

(declare ^:private ^:dynamic *curator-framework*)

(declare ^:private ^:dynamic *service-discoveries*)

(def ^:private environments
  "For the time being we may have to register into multiple environments
  (e.g. services in poke are used to service requests in cq1 and cq3)."
  {"dev" ["dev"]
   "poke" ["poke" "cq1" "cq3"]
   "cq1" ["cq1"]
   "cq3" ["poke" "cq3"]
   "prod" ["prod" "live"]})

(defn ^:private service-discoveries [environment-name]
  (reduce #(conj %1 (service-discovery *curator-framework* %2)) #{} (environments environment-name)))

(defn disconnect! []
  (when (bound? #'*curator-framework*)
    (.close *curator-framework*)
    (.unbindRoot #'*curator-framework*)
    (.unbindRoot #'*service-discoveries*)))

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
  (doseq [discovery *service-discoveries*]
    (try
      (.unregisterService discovery (service-instance service))
      (catch Exception e (warn e "Service discovery failed to unregister")))))

(defn register!
  "Register a service running on this host so that it can be
  discovered by other applications. A typical service might look like:

  `{:name \"care\", :port \"8080\", :uri-spec \"/1.x/care/*\"}`

  Anything registered will be automatically unregistered when the JVM
  terminates."
  [service]
  (doseq [discovery *service-discoveries*]
    (try
      (.registerService discovery (service-instance service))
      (.. Runtime (getRuntime) (addShutdownHook (proxy [Thread] [] (run [] (unregister! service)))))
      (catch Exception e (warn e "Service discovery failed to register")))))
