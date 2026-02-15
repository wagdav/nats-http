(ns net.thewagner.nats-http.core
  (:require
    [clojure.edn :as edn]
    [net.thewagner.nats-http.http :as http])
  (:import
    [io.nats.client Nats]
    [io.nats.service ServiceBuilder ServiceEndpoint ServiceMessageHandler Group]))

(defn- nats-url []
  (let [nats-url-env (System/getenv "NATS_URL")]
    (if nats-url-env
      nats-url-env
      "nats://127.0.0.1:4222")))

(defn- nats-service-name []
  (let [service-name (System/getenv "NATS_HTTP_NAME")]
    (if service-name
      service-name
      "nats-http")))

(defn start []
  (let [url (nats-url)
        service-name (nats-service-name)
        nc (Nats/connect url)
        service-group (Group. service-name)
        echo-endpoint (-> (ServiceEndpoint/builder)
                          (.endpointName "echo")
                          (.group service-group)
                          (.handler
                            (reify ServiceMessageHandler
                              (onMessage [this smsg]
                                (.respond smsg nc (.getData smsg)))))
                          (.build))
        send-endpoint (-> (ServiceEndpoint/builder)
                          (.endpointName "http-request")
                          (.group service-group)
                          (.handler
                            (reify ServiceMessageHandler
                              (onMessage [this smsg]
                                (let [request (-> (.getData smsg)
                                                  (String. "UTF-8")
                                                  (edn/read-string))
                                      response (http/send-request request)]
                                  (.respond smsg nc (-> response str .getBytes))))))
                          (.build))
        service (-> (ServiceBuilder.)
                    (.name service-name)
                    (.version "0.0.1")
                    (.description "NATS HTTP bridge")
                    (.addServiceEndpoint echo-endpoint)
                    (.addServiceEndpoint send-endpoint)
                    (.connection nc)
                    (.build))]
    (.startService service)
    {::service service}))

(defn stop [{::keys [service]}]
  (.stop service))

(comment
  (nats-url)

  (def service (start))
  (stop service))
