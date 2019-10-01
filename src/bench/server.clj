(ns bench.server
  (:require [clojure.tools.logging :as log]
            [jsonista.core :as json]
            [reitit.ring :as ring]
            [org.httpkit.server :as server]
            [beagle.phrases :as phrases]))

(def highlighters (atom {}))

(defn index-creation-handler [request]
  (let [index-name (-> request :path-params :index-name)
        old-highlighter (get @highlighters index-name)
        body (json/read-value (-> request :body) (json/object-mapper {:decode-key-fn true}))
        highlighter-fn (phrases/highlighter (:dictionary body))]
    (swap! highlighters assoc index-name highlighter-fn)
    (log/infof "Created percolator: %s" index-name)
    {:status 200, :body (if old-highlighter "Updated" "Created")}))

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
      [["/:index-name" {:put        index-creation-handler
                        :parameters {:query {:index-name String}}
                        :name       ::percolator-setup}]
       ["/:index-name/_search" {:post       percolation-handler
                                :parameters {:query {:index-name String}}
                                :name       ::percolate}]])
    (ring/create-default-handler
      {:not-found (constantly {:status 404, :body "kosh"})
       :method-not-allowed (constantly {:status 405, :body "kosh"})
       :not-acceptable (constantly {:status 406, :body "kosh"})})))

(defn -main [& args]
  (server/run-server #'app {:port 9200 :max-body Integer/MAX_VALUE})
  (println "Fake percolator server running in port 9200"))
