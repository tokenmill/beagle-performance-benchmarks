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
clj -m bench.phrases -h
```

It outputs something like this:
```
  -d, --dictionary DICTIONARY          resources/top-10000.csv  Path to the dictionary file
  -t, --texts TEXTS                    resources/articles1.csv  Path to the CSV file with texts
  -s, --step STEP                      5000                     Step size for increase in dictionary
  -p, --parallel PARALLEL              true                     Should the benchmark be run in parallel
  -k, --key KEY                        :content                 CSV header key to select
  -w, --warm-up WARM-UP                true                     Should the warm-up be run
      --slop SLOP                      0                        Phrase slop for dictionary entries
      --case-sensitive CASE_SENSITIVE  true                     Should matching be case sensitive
      --ascii-fold ASCII_FOLD          false                    Should matching be ascii folded
      --stem STEM                      false                    Should matching be stemmed
      --stemmer STEMMER                :english                 which stemmer should be used
  -h, --help
```


## Download news dataset from

https://www.kaggle.com/snapcrack/all-the-news/downloads/all-the-news.zip/4

## Performance

![alt text](resources/average-per-doc.png)

![alt text](resources/min-max-per-doc.png)

## License

Copyright &copy; 2019 [TokenMill UAB](http://www.tokenmill.lt).

Distributed under the The Apache License, Version 2.0.
