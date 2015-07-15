# eureka

![latest version](http://clojars.brislabs.com/eureka/latest-version.svg)

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
                     :uri-spec "/1.x/{territory}/users/{userid}/sessions/{devicetype}/{app}"}
                     #(-> (web/healthcheck) :success))
                     
(defn start []
  (setup)
  (reset! server (start-server))
  (register-public-resources))
```

`eureka.registration/connect!` uses the properties :zookeeper-connectionstring and :environment-name. An alternative to setting them in your project.clj file (or as system variables via a bash script) is to provide these two values directly as arguments to the function call.

Services that use `eureka.registration` should add a call to `(eureka.registration/healthy?)` to their healthcheck.

**Note: The `uri-spec` is optional, if you want to register an entire service (rather than an individual resource) and allow clients to construct the paths themselves, then omit the `uri-spec`.**

You can also use a healthcheck function which must return truthy before registration will succeed. If the healthcheck function returns falsey, 10 attemps will be made to register, one second apart (override with `:eureka-registration-attempts` environ key):

```clj
(defn register-public-resources []
  (eureka/connect!)
  (eureka/register! {:name "user-sessions"
                     :port (Integer. (env :service-port))
                     :uri-spec "/1.x/{territory}/users/{userid}/sessions/{devicetype}/{app}"}
                     #(-> (web/healthcheck) :success))
```

A typical **discovery** example, service _X_ finding an instance of service _Y_ to handle a request:

```clj
(ns x.setup
  (:require [eureka.discovery :as eureka])

(defn setup []
  ...
  (eureka/connect!))
```

```clj
(ns x.core
  (:require [clj-http.client :as http]
            [eureka.discovery :as eureka])

(defn fn-that-calls-y [territory]
  ;; if you want to construct your own path
  (http/get (eureka/base-url+ "y" "/1.x/" territory "/foo"))

  ;; if you want to build the path using the registered uri-spec
  (http/get (eureka/url "y" {:territory territory)))

  ;; if you want to do something completely bespoke
  (with-open [service-provider (eureka/service-provider "y")]
    (let [instance (.getInstance service-provider)]
      ;; do something with instance
      )))

```

Services that use `eureka.discovery` should add a call to `(eureka.discovery/healthy?)` to their healthcheck.

## Graceful shutdown

When you register for service discovery it's essential that your instances disconnect gracefully (e.g. when your sevice is deployed and old instances are shut down, you shouldn't see a short period of downtime). You must disconnect from service discovery **before** Jetty begins rejecting incoming requests.

If you're using [instrumented-ring-jetty-adapter](https://github.com/mixradio/instrumented-ring-jetty-adapter) then you can create a safe shutdown like:

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
