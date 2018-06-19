# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]


## [0.1.5] - 2018-06-18
### Changed
- Clojure 1.9 deps.
- Move to new zenbuild.
- Automate POS tagger model download.
- Separate threadsafe tests from others.
- Add full parse test to travis!


## [0.1.4] - 2017-10-17
### Changed
- Open Semantic Role Labeler functions (i.e. the [label function]) for external
  specific processing.
- Move to actioncli 0.0.19.


## [0.1.3] - 2017-09-01
### Added
- Global parsing timeout.  See the [create-context] function.

### Removed
- Moved the trunc functionality to the [actioncli util] namespace.


## [0.1.2] - 2017-07-19
### Changed
- Add pessimistic locking around parsing resource creation for Spark.


## [0.1.1] - 2017-07-13
### Added
- Support for
  [fast shift reduce parser](https://nlp.stanford.edu/software/srparser.shtml).
  See the [parse tree](https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.config.html#var-parse-tree)
  component configuration function.
- Support for
  [natural logic](https://stanfordnlp.github.io/CoreNLP/natlog.html) annotation.
- More annotation documentation.

### Changed
- Upgrade to Stanford CoreNLP version 3.8.0.

### Removed
- Java class `TokenRegexEntityMentionsAnnotator.java` that is now supplanted by
  the [pull request](https://github.com/stanfordnlp/CoreNLP/pull/323) to fold
  in its changes.


## [0.1.0] - 2017-06-26
### Added
- Add map parsing configuration.

### Changed
- Update default stanford pipeline.


## [0.0.17] - 2017-06-09
### Changed
- More robust processing of mentions.
- Part of speech model resource loading is more flexible and add language
  config.


## [0.0.16] - 2017-04-27
### Added
- Sentiment features.
- Travis build.


## [0.0.15] - 2017-02-07
### Added
- Changelog.
- Pipeline configuration parsing DSL.
- Added sentiment.

### Changed
- Upgrade to lein-git 1.2.7


## [0.0.14] - 2017-01-14
### Added
- More robust token regular expression configuration.

### Changed
- Upgraded to Stanford CoreNLP 3.7


[Unreleased]: https://github.com/plandes/clj-nlp-parse/compare/v0.1.5...HEAD
[0.1.5]: https://github.com/plandes/clj-nlp-parse/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/plandes/clj-nlp-parse/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/plandes/clj-nlp-parse/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/plandes/clj-nlp-parse/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/plandes/clj-nlp-parse/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.17...v0.1.0
[0.0.17]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.16...v0.0.17
[0.0.16]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.15...v0.0.16
[0.0.15]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.14...v0.0.15
[0.0.14]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.13...v0.0.14

[create-context]: https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.config.html#var-create-context
[actioncli util]: https://plandes.github.io/clj-actioncli/codox/zensols.actioncli.util.html
[label function]: https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.srl.html#var-label
