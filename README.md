# Natural Language Parse and Feature Generation

A Clojure language library to parse natural language text into features useful
for machine learning model.

Features include:

* Wraps several Java natural language parsing libraries.
* Gives access the data structures rendered by the parsers.
* Provides utility functions to create features.

This framework combines the results of the following frameworks:
* [Stanford CoreNLP 3.8.0](https://github.com/stanfordnlp/CoreNLP)
* [ClearNLP 2.0.2](https://github.com/emorynlp/nlp4j)
* [Stop Word Annotator](https://github.com/plandes/stopword-annotator)

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
## Table of Contents

- [Features](#features)
- [Obtaining](#obtaining)
- [Documentation](#documentation)
    - [API Documentation](#api-documentation)
    - [Annotation Definitions](#annotation-definitions)
- [Example Parse](#example-parse)
- [Setup](#setup)
    - [Download and Install POS Tagger Model Manually](#download-and-install-pos-tagger-model-manually)
    - [REPL](#repl)
- [Usage](#usage)
    - [Usage Example](#usage-example)
    - [Parsing an Utterance](#parsing-an-utterance)
    - [Utility Functions](#utility-functions)
    - [Feature Creation](#feature-creation)
    - [Stopword Filtering](#stopword-filtering)
    - [Dictionary Utility](#dictionary-utility)
    - [Pipeline Configuration](#pipeline-configuration)
        - [Pipeline Usage](#pipeline-usage)
        - [Convenience Namespace](#convenience-namespace)
    - [Command Line Usage](#command-line-usage)
- [Building](#building)
- [Changelog](#changelog)
- [Citation](#citation)
- [References](#references)
- [License](#license)

<!-- markdown-toc end -->


## Features

* [Callable](https://dzone.com/articles/java-clojure-interop-calling) from Java
* [Callable](https://github.com/plandes/clj-nlp-serv) from REST
* Callable from REST in a [Docker Image](https://hub.docker.com/r/plandes/nlpservice/)
* Completely customize.
* Easily extendable.
* Combines all annotations as pure Clojure data structures.
* Provides a feature creation libraries:
  - [Character](https://plandes.github.io/clj-nlp-feature/codox/zensols.nlparse.feature.char.html)
  - [Dictionary, Word Lists](https://plandes.github.io/clj-nlp-feature/codox/zensols.nlparse.feature.word.html)
  - [Language (SRL, POS, etc)](https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.feature.lang.html)
  - [Word Counts](https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.feature.word-count.html)
* Stitches multiple frameworks to provide the following features:
  - [Tokenizing](https://en.wikipedia.org/wiki/Lexical_analysis#Token)
  - Grouping Tokens into Sentences
  - [Lemmatisation](https://en.wikipedia.org/wiki/Lemmatisation)
  - [Part of Speech Tagging](https://en.wikipedia.org/wiki/Part-of-speech_tagging)
  - [Stop Words](https://en.wikipedia.org/wiki/Stop_words) (both word and
    lemma)
  - [Named Entity Recognition](https://en.wikipedia.org/wiki/Named-entity_recognition)
  - [Syntactic Parse Tree](https://en.wikipedia.org/wiki/Parse_tree)
  - [Fast Shift Reduce Parse Tree](https://en.wikipedia.org/wiki/Shift-reduce_parser)
  - [Dependency Tree](https://en.wikipedia.org/wiki/Dependency_grammar)
  - [Co-reference Graph](https://en.wikipedia.org/wiki/Coreference)
  - [Sentiment Analysis](https://en.wikipedia.org/wiki/Sentiment_analysis)
  - [Semantic Role Labeler](https://en.wikipedia.org/wiki/Semantic_role_labeling)
* Seamless itegration with other feature creation libraries:
  * [General NLP feature creation]
  * [Word vector feature creation]


## Obtaining

In your `project.clj` file, add:

[![Clojars Project](https://clojars.org/com.zensols.nlp/parse/latest-version.svg)](https://clojars.org/com.zensols.nlp/parse/)


## Documentation

### API Documentation

* [Clojure](https://plandes.github.io/clj-nlp-parse/codox/index.html)
* [Java](https://plandes.github.io/clj-nlp-parse/apidocs/index.html)


### Annotation Definitions

The utterance parse annotation tree
definitions is [given here](doc/annotation-definitions.md).


## Example Parse

An example of a full annotation parse is [given here](doc/example-parse.md).

## Setup

The NER model is included in the Stanford CoreNLP dependencies, but you still
have to download the POS model.  To download (or create a symbolic link if
you've set the `ZMODEL` environment variable):
```bash
$ make model
```

If this doesn't work, follow
the [manual](#download-and-install-pos-tagger-model-manually) steps.  Otherwise
you can optionally move the model to a shared location on the file system and
skip to [configuring the REPL](#repl).


### Download and Install POS Tagger Model Manually

If the [normal setup](#setup) failed, you'll have to manually download the POS
tagger model.

The library can be configured to use any POS model (or NER for that matter),
but by default it expects
the
[english-left3words-distsim.tagger model](http://nlp.stanford.edu/software/pos-tagger-faq.shtml).

1. Create a directory where to put the model
   ```bash
   $ mkdir -p path-to-model/stanford/pos
   ```

2. Download the [english-left3words-distsim.tagger model](http://nlp.stanford.edu/software/stanford-postagger-2015-12-09.zip)
   the or [similar](http://nlp.stanford.edu/software/tagger.shtml#Download) model.

3. Install the model file:
   ```bash
   $ unzip stanford-postagger-2015-12-09.zip
   $ mv stanford-postagger-2015-12-09/models/english-left3words-distsim.tagger path-to-model/stanford/pos
   ```

### REPL

If you download the model in to any other location other that the current start
directory (see [setup](#setup)) you will have to tell the REPL where the model
is kept on the file system.

Start the REPL and configure:
   ```clojure
   user> (System/setProperty "zensols.model" "path-to-model")
   ```

Note that system properties can be passed via `lein` to avoid having to repeat
this for each REPL instance.


## Usage

This package supports:
* [Parsing an Utterance](#parsing-an-utterance)
* [Utility Functions](#utility-functions)
* [Dictionary Utility](#dictionary-utility)
* [Stopword Filtering](#stopword-filtering)
* [Command Line Usage](#command-line-usage)


### Usage Example

See the [example repo](https://github.com/plandes/clj-example-nlp-ml) that
illustrates how to use this library and contains the code from where these
examples originate.  It's highly recommended to clone it and follow along as
you peruse this README.


### Parsing an Utterance
```clojure
user> (require '[zensols.nlparse.parse :refer (parse)])
user> (clojure.pprint/pprint (parse "I am Paul Landes."))
=> {:text "I am Paul Landes.",
 :mentions
 ({:entity-type "PERSON",
   :token-range [2 4],
   :ner-tag "PERSON",
   :sent-index 0,
   :char-range [5 16],
   :text "Paul Landes"}),
 :sents
 ({:text "I am Paul Landes.",
   :sent-index 0,
   :parse-tree
   {:label "ROOT",
    :child
    ({:label "S",
      :child
      ({:label "NP",
        :child ({:label "PRP", :child ({:label "I", :token-index 1})})}
...
   :dependency-parse-tree
   ({:token-index 4,
     :text "Landes",
     :child
     ({:dep "nsubj", :token-index 1, :text "I"}
      {:dep "cop", :token-index 2, :text "am"}
      {:dep "compound", :token-index 3, :text "Paul"}
      {:dep "punct", :token-index 5, :text "."})}),
...
   :tokens
   ({:token-range [0 1],
     :ner-tag "O",
     :pos-tag "PRP",
     :lemma "I",
     :token-index 1,
     :sent-index 0,
     :char-range [0 1],
     :text "I",
     :srl
     {:id 1,
      :propbank nil,
      :head-id 2,
      :dependency-label "root",
      :heads ({:function-tag "PPT", :dependency-label "A1"})}}
...
```


### Utility Functions

There utility function to have with getting around the parsed data, as it can
be pretty large.  For example, to find the head of the dependency head tree:
```clojure
(def panon (parse "I am Paul Landes."))
=> {:text...
user> (->> panon :sents first p/root-dependency :text)
=> "Landes"
```

In this case, the last name is the head of tree and happens to be a named
entity as detected by the Stanford CoreNLP NER system.  Named entities are
annotatated at the token level, but also included in the *mentions* top level
with the entire set of concatenated tokens (for cases where an NER contains
more than one token like in this case).  To get the full mention text:
```clojure
user> (->> panon :sents first p/root-dependency
                (p/mention-for-token panon)
                first :text))
=> "Paul Landes"
```

### Feature Creation

This library was written to generate features for a machine learning
algoritms.  There are some utility functions for doing this.

Other feature libraries the integrate with this library:
* [General NLP feature creation]
* [Word vector feature creation]

Below are examples of feature creation with just this library.

Get the first propbank parsed from the SRL:
```clojure
user> (->> panon f/first-propbank-label)
=> "be.01"
```

Get stats on features:
```clojure
user> (->> panon p/tokens (f/token-features panon))
=> {:utterance-length 17,
    :mention-count 1,
	:sent-count 1,
	:token-count 5,
	:token-average-length 14/5,
	:is-question false}
```

Each function `X` has an analog function `X-feature-keys` that describes the
features generates and their types, which can be used directly as Weka
attributes:
```clojure
user> (clojure.pprint/pprint (f/token-feature-metas))
=> [[:utterance-length numeric]
    [:mention-count numeric]
	[:sent-count numeric]
	[:token-count numeric]
	[:token-average-length numeric]
	[:is-question boolean]]
```

Get in/out-of-vocabulary ratio:
```clojure
user> (->> panon p/tokens f/dictionary-features)
=> {:in-dict-ratio 4/5}
```

Word count features provide distributions over word counts.
See the [unit test](test/zensols/nlparse/word_count_test.clj).


### Stopword Filtering

Filter 
```clojure
user> (require '[zensols.nlparse.parse :as p])
user> (require '[zensols.nlparse.stopword :as st])
user> (->> (p/parse "This is a test.  This will filter 5 semantically significant words.")
           p/tokens
           st/go-word-forms)
=> ("test" "filter" "semantically" "significant" "words")
```

See the [unit test](test/zensols/nlparse/stopword_test.clj).


### Dictionary Utility

See the [NLP feature library](https://github.com/plandes/clj-nlp-feature) for
more information on dictionary specifics.


### Pipeline Configuration

You can not only configure the natural language processing pipeline and which
specific components to use, but you can also define and add your own plugin
library.  See the
[config namespace](https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.config.html)
for more information.


#### Pipeline Usage

For example, if all you need is tokenization and sentence chunking create a
context and parse it using macro `with-context` and the context you create with
specific components:
```clojure
(require '[zensols.nlparse.config :as conf :refer (with-context)]
         '[zensols.nlparse.parse :refer (parse)])

(let [ctx (->> (conf/create-parse-config
                :pipeline [(conf/tokenize)
                           (conf/sentence)])
               conf/create-context)]
  (with-context ctx
    (parse "I love Clojure.  I enjoy it.")))
```

You can also specify the configuration in the form of a string:
```clojure
(let [ctx (conf/create-context "tokenize,sentence,part-of-speech")]
  (with-context ctx
    (parse "I love Clojure.  I enjoy it.")))
```

The configuration string can also take parameters (ex the `en` parameter to the
tokenizer specifying English as the natural language):
```clojure
(let [ctx (conf/create-context "tokenize(en),sentence,part-of-speech")]
  (with-context ctx
    (parse "I love Clojure.  I enjoy it.")))
```

For an example on how to configure the pipeline, see
[this test case](https://github.com/plandes/clj-nlp-parse/blob/master/test/zensols/nlparse/ner_test.clj#L12-L20).
For more information on the DSL itself see the
[DSL parser](https://github.com/plandes/clj-nlp-parse/blob/master/src/clojure/zensols/nlparse/config_parse.clj).


#### Convenience Namespace

If you use a particular configuration that doesn't change often consider your
own utility parse namespace:

```clojure
(ns example.nlp.parse
  (:require [zensols.nlparse.parse :as p]
            [zensols.nlparse.config :as conf :refer (with-context)]))

(defonce ^:private parse-context-inst (atom nil))

(defn- create-context []
  (->> ["tokenize"
        "sentence"
        "part-of-speech"
        "morphology"
        "named-entity-recognizer"
        "parse-tree"]
       (clojure.string/join ",")
       conf/create-context))

(defn- context []
  (swap! parse-context-inst #(or % (create-context))))

(defn parse [utterance]
  (with-context (context)
    (p/parse utterance)))
```

Now in your application namespace:

```clojure
(ns example.nlp.core
  (:require [example.nlp.parse :as p]))

(defn somefn []
  (p/parse "an utterance"))
```


### Command Line Usage

The command line usage of this project has moved to
the [NLP server](https://github.com/plandes/clj-nlp-serv#comand-line-usage).


## Building

To build from source, do the folling:

- Install [Leiningen](http://leiningen.org) (this is just a script)
- Install [GNU make](https://www.gnu.org/software/make/)
- Install [Git](https://git-scm.com)
- Download the source: `git clone --recurse-submodules https://github.com/plandes/clj-nlp-parse && cd clj-nlp-parse`
- Build the software: `make jar`
- Build the distribution binaries: `make dist`

Note that you can also build a single jar file with all the dependencies with: `make uber`


## Changelog

An extensive changelog is available [here](CHANGELOG.md).


## Citation

If you use this software in your research, please cite with the following
BibTeX:

```jflex
@misc{plandes-clj-nlp-parse,
  author = {Paul Landes},
  title = {Natural Language Parse and Feature Generation},
  year = {2018},
  publisher = {GitHub},
  journal = {GitHub repository},
  howpublished = {\url{https://github.com/plandes/clj-nlp-parse}}
}
```


## References

See the [General NLP feature creation] library for additional references.

```jflex
@phdthesis{choi2014optimization,
  title = {Optimization of natural language processing components for robustness and scalability},
  author = {Choi, Jinho D},
  year = {2014},
  school = {University of Colorado Boulder}
}

@InProceedings{manning-EtAl:2014:P14-5,
  author = {Manning, Christopher D. and  Surdeanu, Mihai  and  Bauer, John  and  Finkel, Jenny  and  Bethard, Steven J. and  McClosky, David},
  title = {The {Stanford} {CoreNLP} Natural Language Processing Toolkit},
  booktitle = {Association for Computational Linguistics (ACL) System Demonstrations},
  year = {2014},
  pages = {55--60},
  url = {http://www.aclweb.org/anthology/P/P14/P14-5010}
}
```


## License

Copyright (c) 2016 - 2024 Paul Landes

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
