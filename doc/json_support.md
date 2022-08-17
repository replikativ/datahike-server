
# Table of Contents

1.  [Introduction](#org1682d8c)
2.  [Requests](#org4229ac5)
    1.  [No arguments required](#orgfa63cab)
    2.  [Out-of-the-box JSON](#org7a0e6c5)
    3.  [Clojure / Datalog required](#org976cc58)
        1.  [Special characters and strings](#org0534ccc)
        2.  [Examples](#orga315f53)
    4.  [Hybrid](#org57338a2)
3.  [Responses](#org9e61134)
4.  [Limitations and future work](#org1b04adc)


<a id="org1682d8c"></a>

# Introduction

[Datahike Server](<https://github.com/replikativ/datahike-server>) supports JSON requests and responses across its API endpoints. This is done via a two-tier approach: one tier with out-of-the-box JSON support for simple API calls, with transparent conversion between strings and Clojure keywords and symbols, sufficient to cover most casual usage; and another, requiring JSON strings containing Clojure and Datalog syntax, for more advanced usage, including calls to `pull` and `query`.

Both tiers encode and decode keywords (including namespaced keywords), symbols, and lists. Sets are also supported in server responses, but not requests, since they are to the best of our knowledge rarely or never used there.

In terms of database-specific expressions, our JSON support includes cardinality-many attributes, lookup refs, and attribute refererences.

The &ldquo;advanced&rdquo; tier is written to support arbitrarily complex expressions, with no loss of expressivity relative to EDN. As for the &ldquo;simple&rdquo; or rather out-of-the-box tier, its functionality should be largely identical to that available using EDN data, with limitations largely relevant to the `transact` endpoint, which does not support tuple-valued attributes, transacting datoms directly, and the (as far as we know, rarely used) operations `:db.fn/call`, `:db/cas` or `:db.fn/cas`, `:db.install/_attribute`, `:db.install/attribute`, and `:db.entity/preds`.

At the moment, most endpoints belong only to one or the other (or neither, if no arguments are required), as documented below. Endpoint functionality corresponds to that of the eponymous Datahike API function where one exists, which is generally the case. Information on functionality is provided here for exceptions; otherwise, please refer to [Datahike documentation](<https://cljdoc.org/d/io.replikativ/datahike/0.5.1506/api/datahike.api>) for further information.


<a id="org4229ac5"></a>

# Requests

The following section illustrates JSON data accepted by server API endpoints with side-by-side examples of equivalent JSON and EDN request data.


<a id="orgfa63cab"></a>

## No arguments required

Use `null` in all cases:

-   `swagger.json`: Returns [Swagger](<https://swagger.io/>) [specification](<https://github.com/OAI/OpenAPI-Specification/blob/main/versions/2.0.md>) documenting the Datahike Server API.
-   `databases`: List available databases.
-   `db`: Returns `meta`, `config`, `hash`, `max-tx`, and `max-eid` of each database.
-   `tempid`
-   `schema`
-   `reverse-schema`


<a id="org7a0e6c5"></a>

## Out-of-the-box JSON

1.  `transact`

        {:tx-data [{:name "Alice", :age 20}]
         :tx-meta []}
        {"tx-data": [{"name": "Alice", "age": 20}],
         "tx-meta": []}
        
        {:tx-data [[:db/add -1 :name "Bob"]
                   [:db/add -1 :age 21]]}
        {"tx-data": [["db/add", -1, "name", "Bob"],
                     ["db/add", -1, "age", 21]]}
        
        {:tx-data [[:db/add 5 :alias :alice]]}
        {"tx-data": [["db/add", 5, "alias", "alice"]]}
        
        {:tx-data [{:name "Chris", :age 5, :parents 5}]}
        {"tx-data": [{"name": "Chris", "age": 5, "parents": 5}]}
        
        {:tx-data [[:db/add [:name "Chris"] :parents [:alias :bob]]]}
        {"tx-data": [["db/add", ["name","Chris"], "parents", ["alias", "bob"]]]}
        
        {:tx-data [[:db/retract [:name "Chris"] :parents 6]]}
        {"tx-data": [["db/retract", ["name", "Chris"], "parents", 6]]}
        
        {:tx-data [[:db.fn/retractAttribute [:name "Chris"] :parents]]}
        {"tx-data": [["db.fn/retractAttribute", ["name", "Chris"], "parents"]]}
        
        {:tx-data [[:db/retractEntity [:name "Chris"]]]}
        {"tx-data": [["db/retractEntity", ["name", "Chris"]]]}

2.  `datoms`, `seek-datoms`

    With lookup ref:
    
        {:index :eavt, :components [[:name "Alice"] 40]}
        {"index": "eavt", "components": [["name", "Alice"], 40]}
    
    With attribute reference:
    
        {:index :aevt, :components [41 44 43]}
        {"index": "aevt", "components": [41,44,43]}

3.  `entity`

        {:eid 4}
        {"eid": 4}
        
        {:eid [:name "Alice"] :attr :age}
        {"eid": ["name", "Alice"], "attr": "age"}

4.  `index-range`

        {:attrid :name}
        {"attrid": "name"}
        
        {:attrid :db/ident :start :age :end :name}
        {"attrid": "db/ident", "start": "age", "end": "name"}


<a id="org976cc58"></a>

## Clojure / Datalog required


<a id="org0534ccc"></a>

### Special characters and strings

-   ***:***: A single occurrence at the start of a string denotes a **keyword**, e.g. &ldquo;:kw&rdquo; denotes `:kw`. To denote a string starting with `:`, escape with an extra occurrence, e.g. &ldquo;$$a&rdquo; for &ldquo;$a&rdquo;, and &ldquo;\\$\\$\\$string&rdquo; for &ldquo;\\$\\$string". Note that a standalone occurrence, i.e. "$&rdquo;, is illegal.
-   ***&***: A single occurrence at the start of a string indicates that the *remaining characters* should be interpreted as a **symbol**, e.g. &ldquo;&or&rdquo; denotes `or`, and &ldquo;&a-sym&rdquo; denotes `a-sym`. As with &ldquo;:&rdquo;, a standalone occurrence, i.e. &ldquo;&&rdquo;, is illegal; and to denote a string starting with `&`, escape with an extra occurrence, e.g. &ldquo;&&a&rdquo; for &ldquo;&a&rdquo;.
-   ***?***, ***$***: A single occurrence on its own or at the start of a string causes the *entire string* to be interpreted as a symbol, e.g. &ldquo;?n&rdquo; denotes `?n`, &ldquo;$&rdquo; `$`, and &ldquo;$db&rdquo; `$db`. Escape with an extra occurrence to denote a string starting with either of these characters, e.g. &ldquo;??qn&rdquo; for &ldquo;?qn&rdquo;.
-   ***.***, ***&#x2026;***: Interpreted as a symbol when used as-is. Does not require escaping, e.g. &ldquo;.hidden&rdquo; represents itself i.e. &ldquo;.hidden&rdquo;.
-   ***+***, ***-***, ***\****, ***/***, ***\_***, ***%***: As with `.` and `...`, used on their own to represent symbols, and don&rsquo;t require escaping.
-   ***nil***: Represents `nil`.
-   ***!list***: As the first element within a pair of square brackets, indicates that the remaining elements form a Clojure list, e.g. `["!list", 1, 2, "c"]` translates into `(1 2 "c")`.

Note that where escaping is required, it is only at the beginning of strings; occurrences elsewhere are treated literally, e.g. &ldquo;$abc&rdquo; must be encoded as &ldquo;$$abc&rdquo;, but &ldquo;e$c&rdquo; is encoded as itself i.e. &ldquo;e$c&rdquo;.


<a id="orga315f53"></a>

### Examples

1.  `q`

        {:query '[:find ?e ?n :in $ ?n
                  :where [?e :name ?n]]
          :args ["Alice"]}
        {":query": [":find", "?e", "?n", ":in", "$", "?n",
                    ":where", ["?e", ":name", "?n"]],
          ":args": ["Alice"]}
        
        {:query '[:find ?n . :in $ ?a
                  :where [?e :name ?n] [?e :age ?a]]
          :args [0]}
        {":query": [":find", "?n", ".", ":in", "$", "?a",
                    ":where", ["?e", ":name", "?n"], ["?e", ":age", "?a"]],
         ":args": [0]}
        
        {:query '[:find [?pe ...] :in $
                  :where [?ce :name ?cn]
                         [?ce :parents ?pe]]}
        {":query": [":find", ["?pe", "..."], ":in", "$",
                    ":where", ["?ce", ":name", "?cn"],
                              ["?ce", ":parents", "?pe"]]}
        
        {:query '[:find ?n ?a :in $
                  :where
                  [?e :name ?n]
                  [?e :age ?a]
                  [(> ?a 20)]]}
        {":query": [":find", "?n", "?a", ":in", "$",
                    ":where",
                    ["?e", ":name", "?n"],
                    ["?e", ":age", "?a"],
                    [["!list", "&>", "?a", 20]]]}
        
        {:query '[:find ?e :where
                  (or [?e :name "::not-a-keyword"]
                      [?e :name "$$not-a-symbol"]
                      [?e :name ""???""]
                      [?e :name "/just-a-string"])]}
        {":query": [":find", "?e", ":where",
                    ["!list", "&or", ["?e", ":name", ":::not-a-keyword"],
                                     ["?e", ":name", "$$$not-a-symbol"],
                                     ["?e", ":name", "????"],
                                     ["?e", ":name", "/just-a-string"]]]}
        
        {:query '[:find [(min ?a) (max ?a)]
                  :in $
                  :where [_ :age ?a]]}
        {":query": [":find", [["!list", "&min", "?a"], ["!list", "&max", "?a"]],
                    ":in", "$",
                    ":where", ["_", ":age", "?a"]]}

2.  `pull`

        {:selector '[* {:parents [:name :age]}]
         :eid [:name "Chris"]}
        {":selector": ["*", {":parents": [":name", ":age"]}],
         ":eid": [":name", "Chris"]}
        
        {:selector [:name :age {'(limit :parents nil) [:name :age]}]
         :eid [:name "Chris"]}
        {":selector": [":name", ":age", {["!list", "&limit", ":parents", "nil"]: [":name", ":age"]}],
         ":eid": [":name", "Chris"]}

3.  `pull-many`

        {:selector [:name], :eids '(1 2 3 4)}
        {":selector": [":name"], ":eids": ["!list", 1, 2, 3, 4]}


<a id="org57338a2"></a>

## Hybrid

1.  `load-entities`

    Without schema: requires Clojure syntax, e.g.:
    
        {:entities [[1 :name "Alice" 1 true]
                    [1 :age 20 1 true]
                    [1 :alias :alice 1 true]
                    [2 :name "Bob" 1 true]
                    [2 :age 21 1 true]
                    [2 :alias :bob 1 true]]}
        {"entities": [[1, ":name", "Alice", 1, true],
                      [1, ":age", 20, 1, true],
                      [1, ":alias", ":alice", 1, true],
                      [2, ":name", "Bob", 1, true],
                      [2, ":age", 21, 1, true],
                      [2, ":alias", ":bob", 1, true]]}
    
    With schema: uses out-of-the-box JSON, e.g.:
    
        {:entities [[1 :name "Alice" 1 true]
                    [1 :age 20 1 true]
                    [1 :alias :alice 1 true]
                    [2 :name "Bob" 1 true]
                    [2 :age 21 1 true]
                    [2 :alias :bob 1 true]]}
        {"entities": [[1, "name", "Alice", 1, true],
                      [1, "age", 20, 1, true],
                      [1, "alias", "alice", 1, true],
                      [2, "name", "Bob", 1, true],
                      [2, "age", 21, 1, true],
                      [2, "alias", "bob", 1, true]]}


<a id="org9e61134"></a>

# Responses

At the moment, the Server returns plain JSON responses, with sets denoted by `["!set" ...]` for `#{...}`. Note, unfortuantely, that this means responses are in a sense asymmetric with requests containing Clojure syntax, e.g. transacting a value of `:val` and `"val"` will produce `"val"` alike in response `tx-data`.


<a id="org1b04adc"></a>

# Limitations and future work

Types and tagged literals are not yet fully supported, though that is a high-priority to-do.

We plan to extend endpoints in the out-of-the-box tier, to allow &ldquo;advanced&rdquo; Clojure-inclusive syntax as well.

In addition, there may be scope for improving performance via minimizing handler-level parsing by maximizing usage of [Muuntaja](<https://github.com/metosin/muuntaja>) and [Jsonista](<https://github.com/metosin/jsonista>) functionality in the middleware chain.

