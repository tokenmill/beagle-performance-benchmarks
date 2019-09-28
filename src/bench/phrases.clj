(ns bench.phrases
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]
            [clojure.core.async :refer [chan pipeline to-chan <!! close!]]
            [jsonista.core :as json]
            [beagle.readers :as readers]
            [beagle.phrases :as phrases]
            [bench.percolator :as percolator]
            [bench.cli-options :as cli-options]))

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
  ([highlighter-fn dictionary articles key]
   (bench-concurrent* highlighter-fn dictionary articles key
                      {:concurrency (* 2 (.availableProcessors (Runtime/getRuntime)))}))
  ([highlighter-fn dictionary articles key opts]
   (let [concurrency (:concurrency opts)
         annotator-fn (highlighter-fn dictionary opts)
         out (chan (or concurrency 16))
         xf (map #(annotate annotator-fn % key))
         start (System/nanoTime)]
     (pipeline
       (or concurrency 16)
       out
       xf
       (to-chan articles))
     (let [output (doall (map (fn [_] (<!! out)) (range (count articles))))]
       (close! out)
       (log/infof "Annotated in %s ns" (- (System/nanoTime) start))
       output))))

(defn bench-one-thread [highlighter-fn dictionary articles key opts]
  (let [annotator-fn (highlighter-fn dictionary opts)
        start (System/nanoTime)
        rez (doall (map #(annotate annotator-fn % key) articles))
        _ (log/infof "Annotated in %s ns" (- (System/nanoTime) start))]
    rez))

(defn bench* [{:keys [warm-up bench-fn highlighter-fn texts-file key
                      dictionary-file dictionary-step dictionary-entry-opts]
               :as   opts}]
  (let [dictionary (map #(merge % dictionary-entry-opts) (readers/read-csv dictionary-file))
        articles (read-news-articles texts-file)]
    (let [step (min (or dictionary-step 5000) (count dictionary))]
      (when warm-up
        (log/infof "Doing a warm-up run.")
        (bench-fn highlighter-fn dictionary articles key opts))
      (loop [cnt step
             result []]
        (if (<= cnt (count dictionary))
          (let [start (System/nanoTime)
                rez (bench-fn highlighter-fn (take cnt dictionary) articles key opts)
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

(defn get-highlighter [kw]
  (kw {:beagle     phrases/highlighter
       :percolator percolator/highlighter}
      beagle.phrases/highlighter))

(defn bench [{:keys [implementation output parallel slop case-sensitive stem ascii-fold stemmer] :as opts}]
  (log/infof "Started benchmark with opts: '%s'" opts)
  (let [bench-fn (if parallel bench-concurrent* bench-one-thread)
        highlighter-fn (get-highlighter implementation)
        benchmark {:meta {:opts    opts
                          :system  (System/getProperties)
                          :runtime {:total-memory (.totalMemory (Runtime/getRuntime))
                                    :cpu          (.availableProcessors (Runtime/getRuntime))}}
                   :data (bench.phrases/bench* (assoc opts
                                                 :tokenizer             :whitespace
                                                 :highlighter-fn        highlighter-fn
                                                 :bench-fn              bench-fn
                                                 :dictionary-entry-opts {:slop            slop
                                                                         :case-sensitive? case-sensitive
                                                                         :ascii-fold?     ascii-fold
                                                                         :stem?           stem
                                                                         :stemmer         stemmer}))}]
    (log/infof "Results stored in: %s" output)
    (spit output (json/write-value-as-string benchmark))))

(defn -main [& args]
  (let [{:keys [options summary errors]}
        (cli/parse-opts args cli-options/options :strict true)]
    (when (seq errors)
      (println errors)
      (println summary)
      (System/exit 1))
    (if (:help options)
      (do
        (when-not (get-in options [:options :texts])
          (log/error "Please specify texts file.")
          (println summary)
          (System/exit 1))
        (println summary)
        (System/exit 0))
      (do
        (bench options)
        (shutdown-agents)
        (System/exit 0)))))
