(ns bench.phrases
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [beagle.readers :as readers]
            [beagle.phrases :as phrases]))

(defn read-news-articles [source]
  (with-open [reader (io/reader source)]
    (let [[header & lines] (csv/read-csv reader :separator \, :quote \")
          kvs (map (fn [attr-name] (keyword (if (s/blank? attr-name)
                                              "nr"
                                              attr-name))) header)]
      (doall (map #(apply hash-map (interleave kvs %)) lines)))))

(defn bench-one-cpu [dictionary-file articles-file]
  (let [annotator-fn (phrases/annotator (readers/read-csv dictionary-file))
        articles (read-news-articles articles-file)]
    (prn "Articles: " (count articles))
    (time
      (doseq [article articles]
        (try
          (annotator-fn (:content article))
          (catch Exception e (prn "Failed on" article)))))))

(defn -main [& _]
  (let [dictionary-file "resources/top-10.csv"
        articles "resources/articles1.csv"]))
