(ns eureka.discovery-test
  (:require [eureka.discovery :as eureka])
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [zookem.core :refer [with-zk *zk-connect-string*]]))

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

(fact "service provider can be used to find an instance"
      (with-zk {:nodes {"/dev/instances/care/7fee1629-8e38-4c7b-b584-8e9621b43f3b"
                        (json/generate-string {:name "care"
                                               :id "7fee1629-8e38-4c7b-b584-8e9621b43f3b"
                                               :address "10.216.141.6"
                                               :port 8080
                                               :registrationTimeUTC 1387297218261
                                               :serviceType "DYNAMIC"
                                               :uriSpec {:parts [{:value "/1.x/care/", :variable false}
                                                                 {:value "*", :variable true}]}})}}
        (eureka/connect! *zk-connect-string* environment-name)
        (with-open [service-provider (eureka/service-provider "care")]
          (let [instance (.getInstance service-provider)]
            (.getPort instance) => 8080
            (.getAddress instance) => "10.216.141.6"
            (.getPort instance) => 8080
            (.buildUriSpec instance {"*" "abc"}) => "/1.x/care/abc"))
        (eureka/disconnect!)))
