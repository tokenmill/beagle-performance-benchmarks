(ns bench.phrases
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]
            [clojure.core.async :refer [chan pipeline-blocking to-chan <!! close!]]
            [oz.core :as oz]
            [beagle.readers :as readers]
            [beagle.phrases :as phrases]
            [jsonista.core :as json]))

(defn read-news-articles [source]
  (with-open [reader (io/reader source)]
    (let [[header & lines] (csv/read-csv reader :separator \, :quote \")
          kvs (map (fn [attr-name] (keyword (if (s/blank? attr-name)
                                              "nr"
                                              attr-name))) header)]
      (doall (map #(apply hash-map (interleave kvs %)) lines)))))

(defn annotate [annotator-fn article]
  (let [start (System/nanoTime)]
    (try
      (let [annotations (annotator-fn (:content article))]
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
  ([dictionary articles] (bench-concurrent* dictionary articles (* 2 (.availableProcessors (Runtime/getRuntime)))))
  ([dictionary articles parallelism]
   (let [annotator-fn (phrases/annotator dictionary)
         out (chan (or parallelism 16))
         xf (map #(annotate annotator-fn %))
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

(defn multithreaded [dictionary-file articles-file parallelism]
  (let [dictionary (readers/read-csv dictionary-file)
        articles (read-news-articles articles-file)]
    (log/infof "Dictionary size: %s; articles: %s" (count dictionary) (count articles))
    (bench-concurrent* dictionary articles parallelism)))

(defn bench-one-thread [dictionary articles]
  (let [annotator-fn (phrases/annotator dictionary)
        start (System/nanoTime)
        rez (doall (map #(annotate annotator-fn %) articles))
        _ (log/infof "Annotated in %s ns" (- (System/nanoTime) start))]
    rez))

(defn spec [{benchmark-data :data :as benchmark}]
  [:div
   [:h1 "Beagle performance"]
   [:h2 "Times"]
   [:div
    [:h3 "Average time per doc in ms"]
    [:vega-lite {:data     {:values benchmark-data}
                 :width    400 :height 300
                 :encoding {:x     {:field "dictionary-size"}
                            :y     {:field "average"}
                            :color {:field "articles-count" :type "nominal"}}
                 :mark     "line"}]]
   [:div
    [:h3 (format "Total time per %s documents in ms" (:articles-count (first benchmark-data)))]
    [:vega-lite {:data     {:values benchmark-data}
                 :width    400 :height 300
                 :encoding {:x     {:field "dictionary-size"}
                            :y     {:field "total-time"}
                            :color {:field "articles-count" :type "nominal"}}
                 :mark     "line"}]]
   [:div
    [:h3 (format "Docs per second")]
    [:vega-lite {:data     {:values benchmark-data}
                 :width    400 :height 300
                 :encoding {:x     {:field "dictionary-size"}
                            :y     {:field "per-second"}
                            :color {:field "articles-count" :type "nominal"}}
                 :mark     "line"}]]
   [:div
    [:h2 (format "MIN and MAX time spent per article for %s articles" (:articles-count (first benchmark-data)))]
    [:vega-lite {:data     {:values benchmark-data}
                 :width    400 :height 300
                 "resolve" {"scale" {"y" "independent"}}
                 :layer    [{:encoding {:x {:field "dictionary-size"
                                            :axis  {"title" "Dictionary size"}}
                                        :y {:field "max"
                                            :axis  {"title"      "MAX time spent on one article in ms"
                                                    "titleColor" "red"}}}
                             :mark     {:type "line" :color "red"}}
                            {:encoding {:x {:field "dictionary-size"}
                                        :y {:field "min"
                                            :axis  {"title"      "MIN time spent on one article in ms"
                                                    "titleColor" "green"}}}
                             :mark     {:type "line" :color "green"}}]}]]
   [:h2 "Summary"]
   [:p "This sums up the performance benchmarks."]])

(defn preview-vals [benchmark-data]
  (oz/view! (spec benchmark-data)))

(defn bench* [{:keys [bench-fn dictionary-file articles-file dictionary-step dictionary-entry-opts]}]
  (let [dictionary (map #(merge % dictionary-entry-opts) (readers/read-csv dictionary-file))
        articles (take 10000 (read-news-articles articles-file))]
    (let [step (min (or dictionary-step 5000) (count dictionary))]
      (loop [cnt step
             result []]
        (if (<= cnt (count dictionary))
          (let [start (System/nanoTime)
                rez (bench-fn (take cnt dictionary) articles)
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

(defn -main [& {:as opts}]
  (let [dictionary-file "resources/top-10000.csv"
        articles-file "resources/articles1.csv"
        bench-fn (if (:single-thread opts) bench-one-thread bench-concurrent*)
        benchmark {:meta {:multi-thread (true? (:single-thread opts))
                          :opts         opts
                          :system       (System/getProperties)
                          :runtime      {:total-memory (.totalMemory (Runtime/getRuntime))
                                         :cpu          (.availableProcessors (Runtime/getRuntime))}}
                   :data (bench.phrases/bench* {:bench-fn              bench-fn
                                                :dictionary-step       5000
                                                :dictionary-entry-opts {:slop            1
                                                                        :case-sensitive? false
                                                                        :stem?           true
                                                                        :ascii-fold?     true}
                                                :dictionary-file       dictionary-file
                                                :articles-file         articles-file})}]
    (spit (str "vals-" (System/currentTimeMillis) ".json") (json/write-value-as-string benchmark))
    (preview-vals benchmark)))
