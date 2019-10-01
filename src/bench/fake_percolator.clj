(ns bench.fake-percolator
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [jsonista.core :as json]
            [bench.percolator :as real-percolator]))

(defn store-dictionary! [es-host index-name dictionary]
  @(http/request
     {:method  :put
      :url     (format "%s/%s" es-host index-name)
      :headers {"Content-Type" "application/json"}
      :body    (json/write-value-as-string {:dictionary dictionary})}))

(defn highlighter [dictionary opts]
  (let [es-host (:es-host opts "http://localhost:9200")
        index-name (str "percolator-" (System/currentTimeMillis))]
    (log/infof "OPTS: %s" opts)
    (store-dictionary! es-host index-name dictionary)
    (log/infof "Created FAKE percolator at '%s' in index '%s'" es-host index-name)
    (fn [text] (real-percolator/percolate es-host index-name text))))
