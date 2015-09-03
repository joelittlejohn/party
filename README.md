# party [![Build Status](https://travis-ci.org/mixradio/party.svg?branch=master)](https://travis-ci.org/mixradio/party)

<img src="https://upload.wikimedia.org/wikipedia/commons/c/c3/Party_icon.svg" alt="Let's party!" title="Let's party!" align="right" width="250"/>

A Clojure library that wraps the Curator service discovery/registration API and provides a set of Clojure functions to register and discover services. 

Party also helps manage the lifecycle of the CuratorFramework internally, creating and maintaining connections to Zookeeper as necessary and closing those connections when your application terminates.

## Usage

#### A typical _registration_ example

A service registering:

```clj
(ns heartbeat.setup
  (:require [party.registration :as party])

(defn register-public-resources []
  (party/connect!)
  
  ;; if you want to register the entire service (no uri-spec)
  (party/register! {:name "foo"
                    :port (Integer. (env :service-port)))
                     
  ;; if you want to register a specific resource
  (party/register! {:name "user-sessions"
                    :port (Integer. (env :service-port))
                    :uri-spec "/1.x/{territory}/users/{userid}/sessions/{devicetype}/{app}"}))
                     
(defn start []
  (setup)
  (reset! server (start-server))
  (register-public-resources))
```

`party.registration/connect!` uses the [environ](https://github.com/weavejester/environ) properties `:zookeeper-connectionstring` and `:environment-name`. An alternative to setting them in your project.clj file (or as system variables via a bash script) is to provide these two values directly as arguments to the function call.

Services that use `party.registration` should add a call to `(party.registration/healthy?)` to their healthcheck. Note from the above that the `uri-spec` is optional, if you want to register an entire service (rather than an individual resource) and allow clients to construct the paths themselves, then omit the `uri-spec`.

You can also use a healthcheck function which must return truthy before registration will succeed. If the healthcheck function returns falsey, 10 attemps will be made to register, one second apart (override with `:party-registration-attempts` environ key):

```clj
(defn register-public-resources []
  (party/connect!)
  (party/register! {:name "user-sessions"
                    :port (Integer. (env :service-port))
                    :uri-spec "/1.x/{territory}/users/{userid}/sessions/{devicetype}/{app}"}
                     #(-> (web/healthcheck) :success))
```

#### A typical _discovery_ example

Service _x_ finding an instance of service _y_ to handle a request:

```clj
(ns x.setup
  (:require [party.discovery :as party])

(defn setup []
  ...
  (party/connect!))
```

```clj
(ns x.core
  (:require [clj-http.client :as http]
            [party.discovery :as party])

(defn fn-that-calls-y [territory]
  ;; if you want to construct your own path
  (http/get (party/base-url+ "y" "/1.x/" territory "/foo"))

  ;; if you want to build the path using the registered uri-spec
  (http/get (party/url "y" {:territory territory)))

  ;; if you want to do something completely bespoke
  (with-open [service-provider (party/service-provider "y")]
    (let [instance (.getInstance service-provider)]
      ;; do something with instance
      )))

```

Services that use `party.discovery` should add a call to `(party.discovery/healthy?)` to their healthcheck.

**IMPORTANT: When you use party for discovery you are building a URL using an IP address that is only valid for the current call. IP addresses can change at any time, and your calls _should_ be balanced across all IPs. Don't _ever_ save URLs produced by `url` or `base-url+` for later use. Get a new one each time.**

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
  (party/disconnect!)
  (Thread/sleep (Integer/valueOf (env :service-curator-gracefulunregister-millis))))

(defn start-server []
  (instrumented/run-jetty #'web/app {...
                                     :configurator configure-server
                                     :on-stop unregister-public-resources}))
```

## Testing

For functional testing, it's often useful to skip service discovery and provide an explicit URL. For a service `foo`, you can override the URL like:

```
:discovery-override-foo "http://localhost:8081/1.x/foo/{bar}/baz"
```

Since we use environ, the same can be achieved using `export DISCOVERY_OVERRIDE_FOO` or `-Ddiscovery.override.foo`. The path (`/1.x/foo/{bar}/baz`) is of course optional.

Since we're effectively skipping the discovery step here, it's recommended that you have a least some tests that fully integrate and _do_ exercise the discovery mechanism.

# License

Copyright © 2015 MixRadio

party is released under the 3-clause license ("New BSD License" or "Modified BSD License")
