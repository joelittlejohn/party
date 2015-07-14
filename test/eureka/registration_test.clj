(ns eureka.registration-test
  (:require [eureka.registration :as eureka])
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [zookem.core :refer [with-zk *zk-client* *zk-connect-string*]]
            [zookeeper :as zk]))

(def environment-name "dev")

(fact "health check succeeds when service discovery can list instances"
      (with-zk {:nodes {"/dev/instances" nil}}
        (eureka/connect! *zk-connect-string* environment-name)
        (eureka/healthy?) => truthy
        (eureka/disconnect!)))

(fact "health check fails when service discovery cannot list instances"
      (with-zk {}
        (eureka/connect! *zk-connect-string* environment-name)
        (eureka/healthy?) => falsey
        (eureka/disconnect!)))

(fact "health check fails when not connected"
      (eureka/healthy?) => falsey)

(fact "register! creates a new registration"
      (with-zk {:nodes {"/dev/instances" nil}}
        (eureka/connect! *zk-connect-string* "dev")
        (let [node-id (eureka/register! {:name "foo" :uri-spec "/1.x/users/{userid}" :port 5000})
              node (str "/dev/instances/foo/" (first (zk/children *zk-client* "/dev/instances/foo")))
              data (-> (zk/data *zk-client* node) :data (String.) (json/parse-string true))]
          (zk/exists *zk-client* "/dev/instances/foo") => truthy
          data => (contains {:name "foo"
                             :id node-id
                             :address string?
                             :port 5000
                             :uriSpec truthy}))
        (eureka/disconnect!)))

(fact "register! with healthcheck fails when healthcheck fails"
      (with-zk {:nodes {"/poke/instances" nil
                        "/cq1/instances" nil
                        "/cq3/instances" nil}}
        (eureka/connect! *zk-connect-string* "poke")
        (eureka/register! {:name "foo"
                           :uri-spec "/1.x/users/{userid}"
                           :port 5000} (constantly false) 1) => (throws Exception)
        (eureka/disconnect!)))

(declare healthy?)

(fact "register! with healthcheck succeeds when healthcheck eventually returns true"
      (with-zk {:nodes {"/poke/instances" nil
                        "/cq1/instances" nil
                        "/cq3/instances" nil}}
        (eureka/connect! *zk-connect-string* "poke")
        (eureka/register! {:name "foobar"
                           :uri-spec "/1.x/users/{userid}"
                           :port 5000} healthy? 2)
        => string?
        (provided
         (healthy?) =streams=> [false true])
        (zk/children *zk-client* "/poke/instances/foobar") => (one-of string?)
        (eureka/disconnect!)))

(fact "uri-spec is optional for register!"
      (with-zk {:nodes {"/dev/instances" nil}}
        (eureka/connect! *zk-connect-string* "dev")
        (eureka/register! {:name "foo"
                           :port 5000})
        (zk/children *zk-client* "/dev/instances/foo") => (one-of string?)
        (eureka/disconnect!)))

(fact "expose! attaches data to registered service"
      (with-zk {:nodes {"/dev/instances" nil}}
        (let [service {:name "foo"
                       :uri-spec "/1.x/users/{userid}"
                       :port 5000}]
          (eureka/connect! *zk-connect-string* "dev")
          (eureka/register! service)
          (eureka/expose! service [:get :post] {:role ["user"]})
          (let [data (-> (zk/data *zk-client* "/dev/instances/foo") :data (String.) (json/parse-string true))]
            data => (contains {:path "/1.x/users/{userid}"
                               :restrict {:role ["user"]}
                               :methods ["get" "post"]}))
          (eureka/disconnect!))))
