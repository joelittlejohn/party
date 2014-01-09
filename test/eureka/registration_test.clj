(ns eureka.registration-test
  (:require [eureka.registration :as eureka])
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [zookem.core :refer [with-zk *zk-client* *zk-connect-string*]]
            [zookeeper :as zk]))

(fact "register! creates a new registration"
      (with-zk {:nodes {"/dev/instances" nil}}
        (eureka/connect! *zk-connect-string* "dev")
        (eureka/register! {:name "foo"
                           :uri-spec "/1.x/users/{userid}"
                           :port 5000})
        (zk/exists *zk-client* "/dev/instances/foo") => truthy
        (let [node (str "/dev/instances/foo/" (first (zk/children *zk-client* "/dev/instances/foo")))
              data (-> (zk/data *zk-client* node) :data (String.) (json/parse-string true))]
          data => (contains {:name "foo"
                             :id string?
                             :address string?
                             :port 5000
                             :uriSpec truthy}))
        (eureka/disconnect!)))

(fact "register! creates a new registration in all relevant environments"
      (with-zk {:nodes {"/poke/instances" nil
                        "/cq1/instances" nil
                        "/cq3/instances" nil}}
        (eureka/connect! *zk-connect-string* "poke")
        (eureka/register! {:name "foo"
                           :uri-spec "/1.x/users/{userid}"
                           :port 5000})
        (zk/children *zk-client* "/poke/instances/foo") => (one-of string?)
        (zk/children *zk-client* "/cq1/instances/foo") => (one-of string?)
        (zk/children *zk-client* "/cq3/instances/foo") => (one-of string?)
        (eureka/disconnect!)))
