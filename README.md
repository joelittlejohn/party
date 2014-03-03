# eureka

A Clojure library that wraps the Curator service discovery/registration API and provides a set of idiomatic set of Clojure functions to register and discover services. Eureka also manages the lifecycle of the CuratorFramework internally, creating a connection to zookeeper instances as necessary and closing those connections when your application terminates.

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

and services that use `eureka.registration` should add a call to `(eureka/healthy?)` to their healthcheck.
