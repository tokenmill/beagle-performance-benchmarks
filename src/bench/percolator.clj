(ns bench.percolator
  (:require [clojure.core.async :refer [chan pipeline-blocking <!! to-chan close!]]
            [clojure.tools.logging :as log]
            [jsonista.core :as json]
            [org.httpkit.client :as http]))

(defn refresh! [es-host index-name]
  @(http/request
     {:url     (format "%s/%s/_refresh" es-host index-name)
      :method  :get
      :headers {"Content-Type" "application/json"}}
     (fn [{:keys [status error] :as resp}]
       (when error
         (log/errorf "Failed to update ES index='%s' with error '%s'" index-name error))
       (log/debugf "ES index='%s' refresh status: '%s'" index-name status)
       (dissoc resp :opts :client))))

(defn create-index! [es-host index-name]
  @(http/request
     {:method  :put
      :url     (format "%s/%s" es-host index-name)
      :headers {"Content-Type" "application/json"}
      :body    (slurp "resources/percolator.json")}))

(defn store-query! [es-host index-name {:keys [id query]}]
  @(http/request
     {:method  :put
      :url     (format "%s/%s/_doc/%s" es-host index-name id)
      :headers {"Content-Type" "application/json"}
      :body    (json/write-value-as-string
                 {:query {:match_phrase {:text query}}})}))

(defn percolate [es-host index-name text]
  @(http/request
     {:method  :post
      :url     (format "%s/%s/_search" es-host index-name)
      :headers {"Content-Type" "application/json"}
      :body    (json/write-value-as-string
                 {:query     {:percolate
                              {:field    "query"
                               :document {:text text}}}
                  :highlight {:fields {:text {}}}})}
     (fn [{:keys [status body error] :as resp}]
       (log/errorf "Str %s : %s" body status)
       (when (or (not= 200 status) error)
         (throw (RuntimeException. (format "Error: %s" error))))
       (json/read-value body (json/object-mapper {:decode-key-fn true})))))

(defn store-dictionary! [es-host index-name dictionary]
  (create-index! es-host index-name)
  (let [out (chan 200)
        xf (map (fn [dict-entry]
                  (store-query! es-host index-name {:id    (:idx dict-entry)
                                                    :query (:text dict-entry)})))]
    (pipeline-blocking
      200
      out
      xf
      (to-chan (map (fn [d idx] (assoc d :idx idx)) dictionary (range))))
    (let [output (doall (map (fn [_] (<!! out)) (range (count dictionary))))]
      (close! out)
      output))
  (refresh! es-host index-name))

(defn highlighter [dictionary opts]
  (let [es-host (:es-host opts "http://localhost:9200")
        index-name (str "percolator-" (System/currentTimeMillis))]
    (log/infof "OPTS: %s" opts)
    (store-dictionary! es-host index-name dictionary)
    (log/infof "Created percolator at '%s' in index '%s'" es-host index-name)
    (fn [text] (percolate es-host index-name text))))
