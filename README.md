Natural Language Parse
======================

This library provides generalized library to deal with natural language.
Specifically it:
* Wraps several Java natural language parsing libraries
* Gives access the data structures rendered by the parsers
* Provides utility functions to create features

Obtaining
---------
In your `project.clj` file, add:

[![Clojars Project](https://clojars.org/com.zensols.nlp/parse/latest-version.svg)](https://clojars.org/com.zensols.nlp/parse/)

Documentation
-------------
Additional [documentation](https://plandes.github.io/clj-nlp-parse/codox/index.html).

Example
-------
See the [example repo](https://github.com/plandes/clj-example-nlp-ml) that
illustrates how to use this library and contains the code from where these
examples originate.  It's highly recommended to clone it and follow along as
you peruse this README.

Usage
-----
This package supports:
* [Parsing an Utterance](#parsing-an-utterance)
* [Utility Functions](#utility-functions)
* [Dictionary Utility](#dictionary-utility)
* [Command Line Usage](#command-line-usage)

### Setup
The NER model is included in the Stanford CoreNLP dependencies, but you still
have to download the POS model.  The library can be configured to use any POS
model (or NER for that matter), but by default it expects the
[english-left3words-distsim.tagger model](http://nlp.stanford.edu/software/pos-tagger-faq.shtml).

1. Create a directory where to put the model
   ```bash
   $ mkdir -p path-to-model/pos
   ```

2. Download the [english-left3words-distsim.tagger model](http://nlp.stanford.edu/software/stanford-postagger-2015-12-09.zip)
   the or [similar](http://nlp.stanford.edu/software/tagger.shtml#Download) model.

3. Install the model file:
   ```bash
   $ unzip stanford-postagger-2015-12-09.zip
   $ mv stanford-postagger-2015-12-09/models/english-left3words-distsim.tagger path-to-model/pos
   ```

4. Start the REPL and configure:
   ```clojure
   user> (System/setProperty "zensols.model" "path-to-model")
   ```

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

### Features

This library was written to generate features for a machine learning
algoritms.  There are some utility functions for doing this.  Here are a couple
of examples.

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

### Dictionary Utility

See the [NLP feature library](https://github.com/plandes/clj-nlp-feature) for
more information on dictionary specifics.


### Command Line Usage

You can use this as a command line program to generate a prettyprint parse tree
of an utterance.  However, you have to let it know where the
[aforementioned Stanford CoreNLP libraries](#setup) are configured.

To create the command line utility, do the following:

- Install [Leiningen](http://leiningen.org) (this is just a script)
- Install [GNU make](https://www.gnu.org/software/make/)
- Install [Git](https://git-scm.com)
- Download the source:
```bash
   git clone https://github.com/clj-nlp-parse
```
- Follow the directions in [build section](#building)
- Edit and uncomment the `makefile` to set the `ZMODEL` variable, which should
  be set to a directory having the stanford POS model(s) in `standford/pos`.
- Build the distribution binaries:
```bash
   make dist
```
If everything goes well and you are lucky a new folder should show up on your
desktop with everything you need to run it.  To do that:
```bash
cd ~/Desktop/parse/bin
./nlparse -d 'I am Paul Landes'
```

*Note:* I will make the distribution binaries available on request.

Building
--------
All [leiningen](http://leiningen.org) tasks will work in this project.  For
additional build functionality (like building the command line application and
git tag convenience utility functionality) clone the
[Clojure build repo](https://github.com/plandes/clj-zenbuild) in the same
(parent of this file) directory as this project:
```bash
   cd ..
   git clone https://github.com/plandes/clj-zenbuild
```

License
--------
Copyright © 2016 Paul Landes

Apache License version 2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
