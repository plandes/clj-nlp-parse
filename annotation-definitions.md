# Annotation Definitions

The definition of annotations returned from the
[parse function](https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.parse.html#var-parse) is
given below.  Each node in the tree represents the keyword in the tree (nested
maps), the type and any domain information and the description.  When the
domain is given conventional math notation is given.  For example:

> (tuple: *[start, end)*)

is a range of numbers that is inclusive on for starting range and exclusive and
the ending range.  Each index or range is either based on an "absolute"
_utterance_ or _sentence_.  If the latter it starts at the beginning of each
sentence in which it is contained.

An example of an annotation parse data structure
is [given here](example-parse.md).

The annotation definitions follow:


* **text** (sting): Entire _utterance_ text that creates the complete annotation
  tree.
* **sentiment** (integer: *[-2, 2]*): The sentiment score of the entire
  _utterance_.
* **mentions** (list): List of NER mentions of the _utterance_.
  * **char-range** (tuple: *[start, end)*): The 0-based _utterance_ index
	character range.
  * **token-range** (tuple: *[start, end)*): The 1-based _utterance_ index
	token range.
  * **ner-tag** (string): The named entity tag of the mention (ex:
    `ORGANIZATION`).
  * **entity-type** (string): The named entity type of the mention, which is
	usually the **ner-tag** (ex: `PERSON`).
  * **sent-index** (integer): The 0-based index of the sentence the mention
	is found.
  * **text** (string): The text of the full mention.
* **tok-re-mentions** (list): List of custom NER mentions of the _utterance_
  using the
  [token regular expression](https://plandes.github.io/clj-nlp-parse/codox/zensols.nlparse.tok-re.html) namespace.
  This has all the same children nodes as **mentions**.
* **sents** (list): Contains a list of sentences.
  * **text** (string): The text of the sentence.
  * **sent-index** (intgeer): The 0-based index of the _sentence_.
  * **sentiment** (integer: *[-2, 2]*): The sentiment score of the _sentence_.
  * **parse-tree** (list): List of composite parse children nodes.
	* **index-range** (tuple: *[start, end)*): The 0-based _sentence_ index
	token range of the current node and all children.
	* **token-index** (integer): The 1-based _sentence_ index of the token.
	* **sentiment** (integer: *[-2, 2]*): The node's sentiment score.  If a
	  non-leaf the score is aggregated from children nodes.
	* **label** (string): The node's token text for leaf nodes or type of
	  branch node (ie. a *noun phrase* uses `NP`).
	* **child** (list): list of composite children nodes (see **parse-tree**)
  * **dependency-parse-tree** (list): List of composite parse children nodes.
	* **dep** (string): The dependency type of this node to the parent.
	* **token-index** (integer): The 1-based _sentence_ index of the token.
	* **text** (string): The token text the node represents.
  * **tokens** (list): The list of tokens for the sentence.
	* **text** (string): The token text.
	* **sent-index** (integer): The 0-based _utterance_ index of the sentence
	  for which the token is contained.x
	* **index-range** (tuple: *[start, end)*): The 0-based _sentence_ index
	token range of the current node and all children.
	* **token-index** (integer): The 1-based _sentence_ index of the token.
	* **token-range** (tuple: *[start, end)*): The 1-based _utterance_ index
	  token range.
	* **char-range** (tuple: *[start, end)*): The 0-based _utterance_ index
	character range.
	* **lemma** (string)
	  the [lemmatized](https://en.wikipedia.org/wiki/Lemmatisation) token.
	* **stopword** (boolean): `true` if the surface form of the word is a stop
      token or `false` otherwise.
	* **stoplemma** (boolean): `true` if the lemmatized surface form of the
      token is a stop word or `false` otherwise.
	* **sentiment** (integer: *[-2, 2]*): The sentiment score of the token.
	* **pos-tag** (string): The part of speech tag (ex: `NN`).
	* **ner-tag** (string): The named entity tag of the token (ex: `PERSON`).
	* **entity-type** (string): The named entity type of the token, which is
	  usually the **ner-tag** (ex: `ORGANIZATION`).
	* **natlog** (map): Map of child nodes that represent
	  the [natural logic](https://stanfordnlp.github.io/CoreNLP/natlog.html)
	  information of the token.
	  * **polarity** The natural logic polarity of the token.
	  * **operator** Map of operator information for quantifier tokens.
	  * **object-token-range** (tuple: *[start, end)*): The 1-based
		_sentence_ index of the object token(s).
	  * **subject-token-range** (tuple: *[start, end)*): The 1-based
		_sentence_ index of the subject token(s).
	  * **quantifier-token-range** (tuple: *[start, end)*): The 1-based
		_sentence_ index of the quantifier token(s).
	  * **quantifier-token-head-index** The 0-based _sentence_ index of the
		head token.
	* **srl** (map): Semantic Role Labeler (SRL) output for the token.
	  * **id** (integer): a 1-based unique _sentence_ based identifier for
		this token.  This is used to connect SRL nodes based on their
		dependency relationship.
	  * **head-id** (integer): the SRL **id** of this node's parent
	  * **heads** (list): a lit of maps containing functional dependency
		relationship to the parent (head) of this token node.
		* **dependency-label** (string): The functional dependency between
		  this token node and the parent (head).  This also known as the
		  argument (ex: `A1`).
		* **function-tag** (string) the type tag of the dependency between
		  this toke node and the parent.  Example: `PPT`.
	  * **dependency-label** (string): the type of dependency relation
		between this token node and its parent (ex: `ccomp`).
	  * **propbank** (string): The propbank tag of the token (ex: `want.01`).
