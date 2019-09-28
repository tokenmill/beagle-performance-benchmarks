(ns bench.cli-options)

(def options
  [["-d" "--dictionary-file DICTIONARY" "Path to the dictionary file"
    :default "resources/top-10000.csv"]
   ["-o" "--output OUTPUT" "Path to the output file"
    :default (str "vals-" (System/currentTimeMillis) ".json")]
   [:short-opt "-t"
    :long-opt "--texts-file"
    :desc "Path to the CSV file with texts"
    :required "TEXTS_CSV_FILE"]
   ["-s" "--dictionary-step STEP" "Step size for increase in dictionary"
    :parse-fn #(Integer/parseInt %)
    :default 5000]
   ["-p" "--parallel PARALLEL" "Should the benchmark be run in parallel"
    :parse-fn #(Boolean/parseBoolean %)
    :default true]
   ["-c" "--concurrency CONCURRENCY" "Number of concurrent executions."
    :parse-fn #(Integer/parseInt %)
    :default 16]
   ["-k" "--key KEY" "CSV header key to select"
    :parse-fn #(keyword %)
    :default :content]
   ["-i" "--implementation IMPLEMENTATION" "Highlighter implementation"
    :parse-fn #(keyword %)
    :default :beagle]
   ["-w" "--warm-up WARM-UP" "Should the warm-up be run"
    :parse-fn #(Boolean/parseBoolean %)
    :default true]
   [nil "--es-host ES_HOST" "Elasticsearch hostname"
    :default "http://127.0.0.1:9200"]
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
   ["-h" "--help"]])