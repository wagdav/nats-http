(ns net.thewagner.nats-http.core
  (:require
    [clojure.edn :as edn]
    [net.thewagner.nats-http.http :as http])
  (:import
    [io.nats.client Nats]
    [io.nats.service Service ServiceBuilder ServiceEndpoint ServiceMessageHandler Group])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn- nats-url ^String []
  (let [nats-url-env (System/getenv "NATS_URL")]
    (if nats-url-env
      nats-url-env
      "nats://127.0.0.1:4222")))

(defn start [service-name opts]
  (let [url (nats-url)
        nc (Nats/connect url)
        service-group (Group. service-name)
        send-endpoint (-> (ServiceEndpoint/builder)
                          (.endpointName "http")
                          (.group service-group)
                          (.handler
                            (reify ServiceMessageHandler
                              (onMessage [this smsg]
                                (let [request (-> (.getData smsg)
                                                  (String. "UTF-8")
                                                  (edn/read-string)
                                                  (merge (select-keys
                                                           opts
                                                           [:body
                                                            :headers
                                                            :query-string
                                                            :request-method
                                                            :scheme
                                                            :server-name
                                                            :server-port
                                                            :uri])))
                                      response (http/send-request request)]
                                  (.respond smsg nc (-> response str .getBytes))))))
                          (.build))
        service (-> (ServiceBuilder.)
                    (.name service-name)
                    (.version "0.0.1")
                    (.description "NATS HTTP bridge")
                    (.addServiceEndpoint send-endpoint)
                    (.connection nc)
                    (.build))]
    (.startService service)
    {::service service}))

(defn stop [{::keys [^Service service]}]
  (.stop service))

(defn -main [& args]
  (doseq [config-path args]
    (let [services (-> config-path slurp edn/read-string)]
      (doseq [[service-name opts] services]
        (start service-name opts)))))

(comment
  (nats-url)

  (def service (start "nats-http" {}))
  (stop service))
