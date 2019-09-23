(ns bench.view
  (:require [oz.core :as oz]
            [jsonista.core :as json]))

(defn spec [{benchmark-data :data}]
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

(defn benchmark [benchmark-data]
  (oz/view! (spec benchmark-data)))

(defn -main [& args]
  (benchmark (json/read-value (slurp (first args)) (json/object-mapper {:decode-key-fn true}))))
