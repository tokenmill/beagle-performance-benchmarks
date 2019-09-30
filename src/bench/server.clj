(ns bench.server
  (:require [reitit.ring :as ring]
            [org.httpkit.server :as server]))

(defn handler [_]
  {:status 200, :body "okssss"})

(defn wrap [handler id]
  (fn [request]
    (clojure.pprint/pprint (:path-params request))
    (update (handler request) :wrap (fnil conj '()) id)))

(defn index-creation-handler [request]
  (clojure.pprint/pprint (:body request))
  {:status 200, :body "put handler"})

(def app
  (ring/ring-handler
    (ring/router
      [["/:index-name" {:middleware [[wrap :api]]
                        :get        handler
                        :put        index-creation-handler
                        :parameters {:query {:index-name String}}
                        :name       ::ping}]])))

(defn -main [& args]
  (server/run-server #'app {:port 3000})
  (println "server running in port 3000"))
