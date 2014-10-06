# eureka

A Clojure library that wraps the Curator service discovery/registration API and provides a set of idiomatic Clojure functions to register and discover services. Eureka also manages the lifecycle of the CuratorFramework internally, creating a connection to zookeeper instances as necessary and closing those connections when your application terminates.

## Usage

A typical **registration** example, a backend service registering a resource:

```clj
(ns heartbeat.setup
  (:require [eureka.registration :as eureka])

(defn register-public-resources []
  (eureka/connect!)
  (eureka/register! {:name "user-sessions"
                     :port (Integer. (env :service-port))
                     :uri-spec "/1.x/{territory}/users/{userid}/sessions/{devicetype}/{app}"}))
```

A typical **discovery** example, _foo_ service finding an instance of _bar_ handle a request:

```clj
(ns foo.setup
  (:require [eureka.registration :as eureka])

(defn setup []
  ...
  (eureka/connect!))
```

```clj
(ns foo.bar
  (:require [eureka.registration :as eureka])

(defn fn-that-calls-bar [territory]
  (with-open [service-provider (eureka/service-provider "bar")]
            (let [instance (.getInstance service-provider)]
              ;; do something with instance
              )))

```

and services that use `eureka.registration` should add a call to `(eureka.discovery/healthy?)` to their healthcheck.

## Graceful shutdown

When you register for service discovery it's essential that your instances unregister gracefully (e.g. when your sevice is deployed and old instances are shut down, you shouldn't see a short period of downtime). You must unregister from service discovery **before** Jetty begins rejecting incoming requests.

If you're using [instrumented-ring-jetty](http://github.brislabs.com/libraries/instrumented-ring-jetty) then you can create a safe shutdown like:

```clj
(defn configure-server [server]
  (doto server
    (.setStopAtShutdown true)
    (.setGracefulShutdown (Integer/valueOf (env :service-jetty-gracefulshutdown-millis)))))

(defn unregister-public-resources
  []
  (eureka/disconnect!)
  (Thread/sleep (Integer/valueOf (env :service-curator-gracefulunregister-millis))))

(defn start-server []
  (instrumented/run-jetty #'web/app {...
                                     :configurator configure-server
                                     :on-stop unregister-public-resources}))
```
