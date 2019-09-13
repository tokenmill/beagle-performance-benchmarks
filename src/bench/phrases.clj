(ns bench.phrases
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]
            [clojure.core.async :refer [chan pipeline-blocking to-chan <!! close!]]
            [oz.core :as oz]
            [beagle.readers :as readers]
            [beagle.phrases :as phrases]))

(defn read-news-articles [source]
  (with-open [reader (io/reader source)]
    (let [[header & lines] (csv/read-csv reader :separator \, :quote \")
          kvs (map (fn [attr-name] (keyword (if (s/blank? attr-name)
                                              "nr"
                                              attr-name))) header)]
      (doall (map #(apply hash-map (interleave kvs %)) lines)))))

(defn annotate [annotator-fn article]
  (try
    (annotator-fn (:content article))
    (catch NullPointerException e
      [])
    (catch Exception e
      (log/warnf "Failed on %s with %s" article e)
      (.printStackTrace e)
      [])))

(defn multithreaded [dictionary-file articles-file parallelism]
  (let [annotator-fn (time (phrases/annotator (readers/read-csv dictionary-file)))
        articles (take 1000 (read-news-articles articles-file))
        out (chan (or parallelism 16))
        xf (map #(annotate annotator-fn %))]
    (pipeline-blocking
      (or parallelism 16)
      out
      xf
      (to-chan articles))
    (let [output (doall (mapcat (fn [_] (<!! out)) (range (count articles))))]
      (close! out)
      output)))

(defn bench-one-cpu [dictionary articles]
  (let [start (System/nanoTime)
        annotator-fn (phrases/annotator dictionary)
        setup-time (- (System/nanoTime) start)
        start (System/nanoTime)
        rez (doall
              (map (fn [article]
                     (let [start (System/nanoTime)]
                       (try
                         (let [annotations (annotator-fn (:content article))]
                           {:annotations annotations
                            :setup-time  setup-time
                            :duration    (- (System/nanoTime) start)})
                         (catch NullPointerException _
                           {:annotations []
                            :duration    -1})
                         (catch Exception e
                           (.printStackTrace e)
                           {:annotations []
                            :duration    -1}))))
                   articles))
        _ (log/infof "Annotated in %s ns" (- (System/nanoTime) start))]
    rez))

(defn bench-one-cpu* [dictionary-file articles-file]
  (let [dictionary (take 50000 (readers/read-csv dictionary-file))
        articles (take 5000 (read-news-articles articles-file))]
    (let [step (min 1000 (count dictionary))]
      (loop [cnt step
             result []]
        (if (<= cnt (count dictionary))
          (let [start (System/nanoTime)
                rez (bench-one-cpu (take cnt dictionary) articles)
                succeeded (filter pos-int? (map :duration rez))
                failed (filter neg-int? (map :duration rez))]
            (recur (+ cnt step)
                   (conj result {:dictionary-size (count (take cnt dictionary))
                                 :articles-count  (count articles)
                                 :failed          (count failed)
                                 :total-time      (float (/ (- (System/nanoTime) start) 1000000))
                                 :min             (float (/ (apply min succeeded) 1000000))
                                 :max             (float (/ (apply max succeeded) 1000000))
                                 :average         (float (/ (reduce + succeeded) (count succeeded) 1000000))})))
          result)))))

(defn preview-vals [vals]
  (oz/view!
    [:div
     [:h1 "Beagle performance"]
     [:h2 "Average per doc and total time per doc in ms"]
     [:div {:style {:display "flex" :flex-direction "row"}}
      [:vega-lite {:data {:values vals}
                   :width 400 :height 300
                   :encoding {:x {:field "dictionary-size"}
                              :y {:field "average"}
                              :color {:field "articles-count" :type "nominal"}}
                   :mark "line"}]]
     [:div {:style {:display "flex" :flex-direction "row"}}
      [:vega-lite {:data {:values vals}
                   :width 400 :height 300
                   :encoding {:x {:field "dictionary-size"}
                              :y {:field "total-time"}
                              :color {:field "articles-count" :type "nominal"}}
                   :mark "line"}]]
     [:div
      [:h2 (format "MIN and MAX time spent per article for %s articles" (:articles-count (first vals)))]
      [:vega-lite {:data     {:values vals}
                   :width    400 :height 300
                   "resolve" {"scale" {"y" "independent"}}
                   :layer    [{:encoding {:x {:field "dictionary-size"
                                              :axis   {"title" "Dictionary size"}}
                                          :y {:field "max"
                                              :axis   {"title"      "MAX time spent on one article in ms"
                                                       "titleColor" "red"}}}
                               :mark     {:type "line" :color "red"}}
                              {:encoding {:x     {:field "dictionary-size"}
                                          :y     {:field "min"
                                                  :axis   {"title"      "MIN time spent on one article in ms"
                                                           "titleColor" "green"}}}
                               :mark     {:type "line" :color "green"}}]}]]
     [:h2 "Summary"]
     [:p "This sums up the performance benchmarks."]]))

(defn -main [& _]
  (let [dictionary-file "resources/top-10000.csv"
        articles "resources/articles1.csv"
        vals (bench.phrases/bench-one-cpu* dictionary-file articles)]
    (preview-vals vals)))
