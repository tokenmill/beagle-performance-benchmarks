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

(defn -main [& _]
  (let [annotator-fn (phrases/annotator (readers/read-csv "resources/top-10.csv"))
        articles (read-news-articles "resources/articles1.csv")]
    (time (doseq [article (take 10000 articles)]
            (annotator-fn (:content article))))))
