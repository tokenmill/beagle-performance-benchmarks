(ns bench.es
  (:require [org.httpkit.client :as http]
            [jsonista.core :as json]))

(def index-name "percolator")

(defn create-index! []
  @(http/request
     {:method  :put
      :url     (str "http://localhost:9200/" index-name)
      :headers {"Content-Type" "application/json"}
      :body    (slurp "resources/percolator.json")}))

(defn store-query! [{:keys [id query]}]
  @(http/request
     {:method  :put
      :url     (format "http://localhost:9200/%s/_doc/%s?refresh" index-name id)
      :headers {"Content-Type" "application/json"}
      :body    (json/write-value-as-string
                 {:query {:match_phrase {:text query}}})}))

(defn percolate [text]
  @(http/request
     {:method  :post
      :url     (format "http://localhost:9200/%s/_search" index-name)
      :headers {"Content-Type" "application/json"}
      :body    (json/write-value-as-string
                 {:query     {:percolate
                              {:field    "query"
                               :document {:text text}}}
                  :highlight {:fields {:text {}}}})}
     (fn [{:keys [status body] :as resp}]
       (json/read-value body (json/object-mapper {:decode-key-fn true})))))