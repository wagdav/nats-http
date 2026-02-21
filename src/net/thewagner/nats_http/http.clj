(ns net.thewagner.nats-http.http
  (:require
    [clojure.data.json :as json]
    [clojure.string :refer [upper-case]])
  (:import
    [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
    [java.net URI]))

(defn- flatten-request-headers [headers]
  (flatten
    (for [[k vs] headers]
      (if (coll? vs)
        (for [v vs]
          [(name k) v])
        [(name k) vs]))))

(defn- sanitize-response-headers [headers]
  (into {}
    (for [[k vs] (.map headers)]
      (vector
       (keyword k)
       (if (= (count vs) 1)
         (first vs)
         vs)))))

(defn decode-body [response]
  (let [body (.body response)
        content-type (some-> (.headers response)
                             (.firstValue "content-type")
                             (.orElse nil))]
    (case content-type
      "application/json" (json/read-str body :key-fn keyword)
      body)))

(defonce client (delay (-> (HttpClient/newBuilder)
                           .build)))

(defn send-request [{:keys [server-port server-name uri query-string scheme request-method headers body]
                     :or {server-name "localhost"
                          uri "/"
                          query-string ""
                          scheme :http
                          request-method :get}}]
  (let [server-port (or server-port (get {:http 80 :https 443} scheme))
        request (-> (HttpRequest/newBuilder)
                    (.uri (URI/create (str (name scheme) "://" server-name ":" server-port uri query-string)))
                    (.method (upper-case (name request-method))
                             (if body
                               (HttpRequest$BodyPublishers/ofString body)
                               (HttpRequest$BodyPublishers/noBody))))]
    (when (seq headers)
      (.headers request (into-array String (flatten-request-headers headers))))

    (let [response (.send @client (.build request) (HttpResponse$BodyHandlers/ofString))]
      {:status (.statusCode response)
       :headers (sanitize-response-headers (.headers response))
       :body (decode-body response)})))

(comment
  (send-request {:server-port 8000})
  (send-request {:scheme :https :server-name "httpbin.org" :uri "/get" :headers {"X-Test" ["v1" "v2"]}})
  (send-request
    {:server-name "nuc" :server-port 8080
     :uri "/home-thewagner-ec1"
     :request-method :post
     :body "This is is a notification, triggered from Clojure"
     :headers {"X-Tags" "tada"
               "Title" "Notification from Clojure"
               "Click" "http://nuc:3000"}}))
