(ns bench.phrases
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]
            [clojure.core.async :refer [chan pipeline-blocking to-chan <!! close!]]
            [jsonista.core :as json]
            [beagle.readers :as readers]
            [beagle.phrases :as phrases]))

(defn read-news-articles [source]
  (with-open [reader (io/reader source)]
    (let [[header & lines] (csv/read-csv reader :separator \, :quote \")
          kvs (map (fn [attr-name] (keyword (if (s/blank? attr-name)
                                              "nr"
                                              attr-name))) header)]
      (doall (map #(apply hash-map (interleave kvs %)) lines)))))

(defn annotate [annotator-fn article key]
  (let [start (System/nanoTime)]
    (try
      (let [annotations (annotator-fn (get article key))]
        {:annotations annotations
         :duration    (- (System/nanoTime) start)})
      (catch NullPointerException _
        {:annotations []
         :duration    -1})
      (catch Exception e
        (.printStackTrace e)
        {:annotations []
         :duration    -1}))))

(defn bench-concurrent*
  ([dictionary articles key]
   (bench-concurrent* dictionary articles key (* 2 (.availableProcessors (Runtime/getRuntime)))))
  ([dictionary articles key parallelism]
   (let [annotator-fn (phrases/annotator dictionary)
         out (chan (or parallelism 16))
         xf (map #(annotate annotator-fn % key))
         start (System/nanoTime)]
     (pipeline-blocking
       (or parallelism 16)
       out
       xf
       (to-chan articles))
     (let [output (doall (map (fn [_] (<!! out)) (range (count articles))))]
       (close! out)
       (log/infof "Annotated in %s ns" (- (System/nanoTime) start))
       output))))

(defn bench-one-thread [dictionary articles key]
  (let [annotator-fn (phrases/annotator dictionary)
        start (System/nanoTime)
        rez (doall (map #(annotate annotator-fn % key) articles))
        _ (log/infof "Annotated in %s ns" (- (System/nanoTime) start))]
    rez))

(defn bench* [{:keys [warm-up bench-fn dictionary-file dictionary-step dictionary-entry-opts articles-file key]}]
  (let [dictionary (map #(merge % dictionary-entry-opts) (readers/read-csv dictionary-file))
        articles (read-news-articles articles-file)]
    (let [step (min (or dictionary-step 5000) (count dictionary))]
      (when warm-up
        (log/infof "Doing a warm-up run.")
        (bench-fn dictionary articles key))
      (loop [cnt step
             result []]
        (if (<= cnt (count dictionary))
          (let [start (System/nanoTime)
                rez (bench-fn (take cnt dictionary) articles key)
                succeeded (filter pos-int? (map :duration rez))
                failed (filter neg-int? (map :duration rez))]
            (recur (+ cnt step)
                   (conj result {:dictionary-size (count (take cnt dictionary))
                                 :articles-count  (count articles)
                                 :failed          (count failed)
                                 :total-time      (float (/ (- (System/nanoTime) start) 1000000))
                                 :per-second      (float (/ (count articles) (/ (/ (- (System/nanoTime) start) 1000000) 1000)))
                                 :min             (float (/ (apply min succeeded) 1000000))
                                 :max             (float (/ (apply max succeeded) 1000000))
                                 :average         (float (/ (reduce + succeeded) (count succeeded) 1000000))})))
          result)))))

(defn bench [{:keys [warm-up dictionary texts step parallel key slop case-sensitive stem ascii-fold stemmer] :as opts}]
  (log/infof "Started benchmark with opts: '%s'" opts)
  (let [bench-fn (if parallel bench-concurrent* bench-one-thread)
        benchmark {:meta {:opts    opts
                          :system  (System/getProperties)
                          :runtime {:total-memory (.totalMemory (Runtime/getRuntime))
                                    :cpu          (.availableProcessors (Runtime/getRuntime))}}
                   :data (bench.phrases/bench* {:warm-up               warm-up
                                                :bench-fn              bench-fn
                                                :dictionary-step       step
                                                :dictionary-entry-opts {:slop            slop
                                                                        :case-sensitive? case-sensitive
                                                                        :ascii-fold?     ascii-fold
                                                                        :stem?           stem
                                                                        :stemmer         stemmer}
                                                :dictionary-file       dictionary
                                                :articles-file         texts
                                                :key                   key})}]
    (spit (str "vals-" (System/currentTimeMillis) ".json") (json/write-value-as-string benchmark))))

(def cli-options
  [["-d" "--dictionary DICTIONARY" "Path to the dictionary file"
    :default "resources/top-10000.csv"]
   [:short-opt "-t"
    :long-opt "--texts"
    :desc "Path to the CSV file with texts"
    :required "TEXTS_CSV_FILE"]
   ["-s" "--step STEP" "Step size for increase in dictionary"
    :parse-fn #(Integer/parseInt %)
    :default 5000]
   ["-p" "--parallel PARALLEL" "Should the benchmark be run in parallel"
    :parse-fn #(Boolean/parseBoolean %)
    :default true]
   ["-k" "--key KEY" "CSV header key to select"
    :parse-fn #(keyword %)
    :default :content]
   ["-w" "--warm-up WARM-UP" "Should the warm-up be run"
    :parse-fn #(Boolean/parseBoolean %)
    :default true]
   [nil "--slop SLOP" "Phrase slop for dictionary entries"
    :parse-fn #(Integer/parseInt %)
    :default 0]
   [nil "--case-sensitive CASE_SENSITIVE" "Should matching be case sensitive"
    :parse-fn #(Boolean/parseBoolean %)
    :default true]
   [nil "--ascii-fold ASCII_FOLD" "Should matching be ascii folded"
    :parse-fn #(Boolean/parseBoolean %)
    :default false]
   [nil "--stem STEM" "Should matching be stemmed"
    :parse-fn #(Boolean/parseBoolean %)
    :default false]
   [nil "--stemmer STEMMER" "which stemmer should be used"
    :parse-fn #(keyword %)
    :default :english]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options summary errors]}
        (cli/parse-opts args cli-options :strict true)]
    (when (seq errors)
      (println errors)
      (println summary)
      (System/exit 0))
    (when-not (get-in options [:options :texts])
      (log/error "Please specify texts file.")
      (println summary)
      (System/exit 0))
    (if (:help options)
      (do
        (println summary)
        (System/exit 0))
      (do
        (bench options)
        (shutdown-agents)
        (System/exit 0)))))
