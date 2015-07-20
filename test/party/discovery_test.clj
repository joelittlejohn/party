(ns party.discovery-test
  (:require [party.discovery :as party])
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [zookem.core :refer [with-zk *zk-connect-string*]]))

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
        (party/connect! *zk-connect-string* environment-name)
        (with-open [service-provider (party/service-provider "care")]
          (let [instance (.getInstance service-provider)]
            (.getPort instance) => 8080
            (.getAddress instance) => "10.216.141.6"
            (.getPort instance) => 8080
            (.buildUriSpec instance {"*" "abc"}) => "/1.x/care/abc"))
        (party/disconnect!)))

(fact "url can be used to build a url for an registered resource"
      (with-zk {:nodes {"/dev/instances/care/7fee1629-8e38-4c7b-b584-8e9621b43f3b"
                        (json/generate-string {:name "care"
                                               :address "10.216.141.6"
                                               :port 8080
                                               :registrationTimeUTC 1387297218261
                                               :serviceType "DYNAMIC"
                                               :uriSpec {:parts [{:value "/1.x/care/", :variable false}
                                                                 {:value "*", :variable true}]}})}}
        (party/connect! *zk-connect-string* environment-name)
        (party/url "care" {:* "users/foo"}) => "http://10.216.141.6:8080/1.x/care/users/foo"
        (party/disconnect!)))

(fact "url fails when an instance can't be found"
      (with-zk {:nodes {"/dev/instances" nil}}
        (party/connect! *zk-connect-string* environment-name)
        (party/url "care" {:* "users/foo"}) => (throws Exception)
        (party/disconnect!)))

(fact "base-url+ can be used to build a url for an instance"
      (with-zk {:nodes {"/dev/instances/foo/7fee1629-8e38-4c7b-b584-8e9621b43f3b"
                        (json/generate-string {:name "foo"
                                               :address "10.216.141.6"
                                               :port 8080
                                               :registrationTimeUTC 1387297218261
                                               :serviceType "DYNAMIC"})}}
        (party/connect! *zk-connect-string* environment-name)
        (party/base-url+ "foo" "/1.x/users/" "foo" "/bar") => "http://10.216.141.6:8080/1.x/users/foo/bar"
        (party/disconnect!)))

(fact "base-url+ fails when an instance can't be found"
      (with-zk {:nodes {"/dev/instances" nil}}
        (party/connect! *zk-connect-string* environment-name)
        (party/base-url+ "foo" "/1.x/users/" "foo" "/bar") => (throws Exception)
        (party/disconnect!)))

(fact "discovery can be overriden with a config property"
      (against-background (before :facts (alter-var-root #'env assoc :discovery-override-foo "http://x:1234/path"))
                          (after :facts (alter-var-root #'env dissoc :discovery-override-foo)))

      (party/base-url+ "foo" "/1.x/users") => "http://x:1234/1.x/users"
      (party/url "foo" {}) => "http://x:1234/path")
