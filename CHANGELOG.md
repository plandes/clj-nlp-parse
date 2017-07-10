# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]
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


[Unreleased]: https://github.com/plandes/clj-nlp-parse/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.17...v0.1.0
[0.0.17]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.16...v0.0.17
[0.0.16]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.15...v0.0.16
[0.0.15]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.14...v0.0.15
[0.0.14]: https://github.com/plandes/clj-nlp-parse/compare/v0.0.13...v0.0.14
