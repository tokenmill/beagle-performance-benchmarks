(ns bench.server
  (:require [reitit.ring :as ring]
            [org.httpkit.server :as server]
            [beagle.phrases :as phrases]
            [jsonista.core :as json]))

(def highlighters (atom {}))

(defn wrap [handler id]
  (fn [request]
    (update (handler request) :wrap (fnil conj '()) id)))

(defn index-creation-handler [request]
  (let [old-highlighter (get @highlighters (-> request :path-params :index-name))
        body (json/read-value (-> request :body) (json/object-mapper {:decode-key-fn true}))
        highlighter-fn (phrases/highlighter (:dictionary body))]
    (swap! highlighters assoc (-> request :path-params :index-name) highlighter-fn)
    {:status 200, :body (if old-highlighter
                          "Updated"
                          "Created")}))

(defn percolation-handler [request]
  (let [highlighter-fn (get @highlighters (-> request :path-params :index-name))]
    (if highlighter-fn
      (let [body (json/read-value (-> request :body) (json/object-mapper {:decode-key-fn true}))]
        {:status 200
         :body   (json/write-value-as-string {:highlights (highlighter-fn
                                                            (-> body :query :percolate :document :text))})})
      {:status 404})))

(def app
  (ring/ring-handler
    (ring/router
      [["/:index-name" {:middleware [[wrap :api]]
                        :put        index-creation-handler
                        :parameters {:query {:index-name String}}
                        :name       ::percolator-setup}]
       ["/:index-name/_search" {:middleware [[wrap :api]]
                                :post       percolation-handler
                                :parameters {:query {:index-name String}}
                                :name       ::percolate}]])))

(defn -main [& args]
  (server/run-server app {:port 9200})
  (println "Fake percolator server running in port 9200"))
