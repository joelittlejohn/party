(ns party.registration-test
  (:require [party.registration :as party])
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [zookem.core :refer [with-zk *zk-client* *zk-connect-string*]]
            [zookeeper :as zk]))

(def environment-name "dev")

(fact "health check succeeds when service discovery can list instances"
      (with-zk {:nodes {"/dev/instances" nil}}
        (party/connect! *zk-connect-string* environment-name)
        (party/healthy?) => true
        (party/disconnect!)))

(fact "health check fails when service discovery cannot list instances"
      (with-zk {}
        (party/connect! *zk-connect-string* environment-name)
        (party/healthy?) => false
        (party/disconnect!)))

(fact "health check fails when not connected"
      (party/healthy?) => false)

(fact "register! creates a new registration"
      (with-zk {:nodes {"/dev/instances" nil}}
        (party/connect! *zk-connect-string* "dev")
        (let [node-id (party/register! {:name "foo" :uri-spec "/1.x/users/{userid}" :port 5000})
              node (str "/dev/instances/foo/" (first (zk/children *zk-client* "/dev/instances/foo")))
              data (-> (zk/data *zk-client* node) :data (String.) (json/parse-string true))]
          (zk/exists *zk-client* "/dev/instances/foo") => truthy
          data => (contains {:name "foo"
                             :id node-id
                             :address string?
                             :port 5000
                             :uriSpec truthy}))
        (party/disconnect!)))

(fact "register! with healthcheck fails when healthcheck fails"
      (with-zk {:nodes {"/poke/instances" nil
                        "/cq1/instances" nil
                        "/cq3/instances" nil}}
        (party/connect! *zk-connect-string* "poke")
        (party/register! {:name "foo"
                          :uri-spec "/1.x/users/{userid}"
                          :port 5000} (constantly false) 1) => (throws Exception)
                          (party/disconnect!)))

(declare healthy?)

(fact "register! with healthcheck succeeds when healthcheck eventually returns true"
      (with-zk {:nodes {"/poke/instances" nil
                        "/cq1/instances" nil
                        "/cq3/instances" nil}}
        (party/connect! *zk-connect-string* "poke")
        (party/register! {:name "foobar"
                          :uri-spec "/1.x/users/{userid}"
                          :port 5000} healthy? 2)
        => string?
        (provided
         (healthy?) =streams=> [false true])
        (zk/children *zk-client* "/poke/instances/foobar") => (one-of string?)
        (party/disconnect!)))

(fact "uri-spec is optional for register!"
      (with-zk {:nodes {"/dev/instances" nil}}
        (party/connect! *zk-connect-string* "dev")
        (party/register! {:name "no-uri-spec"
                          :port 5000})
        (zk/children *zk-client* "/dev/instances/no-uri-spec") => (one-of string?)
        (party/disconnect!)))
