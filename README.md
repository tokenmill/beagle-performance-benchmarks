<a href="http://www.tokenmill.lt">
      <img src=".github/tokenmill-logo.svg" width="125" height="125" align="right" />
</a>

# beagle-performance-benchmarks

Performance benchmarks for Beagle library and a comparison with other stored query solutions.

## Benchmarking

```bash
clj -m bench.phrases
```

To see the available options run

```bash
clojure -m bench.phrases -h
```

It outputs something like this:
```
  -d, --dictionary-file DICTIONARY     resources/top-10000.csv  Path to the dictionary file
  -o, --output OUTPUT                  vals-1569915647794.json  Path to the output file
  -t, --texts-file TEXTS_CSV_FILE                               Path to the CSV file with texts
  -s, --dictionary-step STEP           5000                     Step size for increase in dictionary
  -p, --parallel PARALLEL              true                     Should the benchmark be run in parallel
  -c, --concurrency CONCURRENCY        16                       Number of concurrent executions.
  -k, --key KEY                        :content                 CSV header key to select
  -i, --implementation IMPLEMENTATION  :beagle                  Highlighter implementation
  -w, --warm-up WARM-UP                true                     Should the warm-up be run
      --es-host ES_HOST                http://127.0.0.1:9200    Elasticsearch hostname
      --slop SLOP                      0                        Phrase slop for dictionary entries
      --case-sensitive CASE_SENSITIVE  true                     Should matching be case sensitive
      --ascii-fold ASCII_FOLD          false                    Should matching be ascii folded
      --stem STEM                      false                    Should matching be stemmed
      --stemmer STEMMER                :english                 which stemmer should be used
  -h, --help

```

The results of the benchmark are written to a file specified with an `-o` option. By default, output is written to
the current dir in a file `(str "vals-" (System/currentTimeMillis) ".json")`.

## Preview benchmark results

```bash
clojure -m bench.view BENCHMARK_OUTPUT_FILE 
```

## Download news dataset from

We run the benchmark on a news dataset downloaded from [Kaggle](https://www.kaggle.com/snapcrack/all-the-news/downloads/all-the-news.zip/4).

## Performance

![alt text](resources/average-per-doc.png)

![alt text](resources/min-max-per-doc.png)

## License

Copyright &copy; 2019 [TokenMill UAB](http://www.tokenmill.lt).

Distributed under the The Apache License, Version 2.0.
