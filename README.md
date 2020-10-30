### elasticsearch-stored-synonyms

An Elasticsearch plugin which provides APIs for uploading synonym files and making
synonyms available to analysis immediately.  The synonym rules are stored in an index
and changes are dispatched to the cluster as invalidation messages via the internal transport.

#### Create / Update a Synonym Ruleset

```
curl -XPUT -H'content-type: application/json' '0:9200/_stored_synonyms' -d '{
  "name": "my_synonyms",
  "rules": [
    "one => bs",
    "foo, bar => baz"
  ]
}'
```

Then refer to that ruleset by name in the `index.analysis.filter` section:

```
curl -XPUT -H'content-type: application/json' '0:9200/my_items' -d '{
  "settings": {
    "index" : {
        "analysis" : {
            "analyzer" : {
                "name_analyzer" : {
                    "tokenizer" : "whitespace",
                    "filter" : ["name_synonyms"]
              }
            },
            "filter" : {
                "name_synonyms" : {
                    "type" : "stored_synonyms",
                    "name" : "my_synonyms",
                    "ignore_case": true
                }
            }
        }
    }
  },
 "mappings": {
    "properties": {
      "name": {
        "type": "text",
        "analyzer": "standard",
        "search_analyzer": "name_analyzer"
      }
    }
 }
}'
```

Now, if you re-upload a valid synonym set named my_synonyms, the search
analyzer will reflect changes in near real time.

You can verify by using the analysis API

```
curl -XGET -H'content-type: application/json' '0:9200/my_items/_analyze' -d '{
  "analyzer": "name_analyzer",
  "text": "foo one"
}'
```

And the output looks like:

```json
{
  "tokens": [
    {
      "token": "baz",
      "start_offset": 0,
      "end_offset": 3,
      "type": "SYNONYM",
      "position": 0
    },
    {
      "token": "uno",
      "start_offset": 4,
      "end_offset": 7,
      "type": "SYNONYM",
      "position": 1
    },
    {
      "token": "one",
      "start_offset": 4,
      "end_offset": 7,
      "type": "word",
      "position": 1
    }
  ]
}
```

### Fetching and Deleting Synonym Rulesets

Fetching a ruleset with name `my_synonyms`

```
curl '0:9200/_stored_synonyms/my_synonyms'
```

Deleting a ruleset with name `my_synonyms`

```
curl -XDELETE '0:9200/_stored_synonyms/my_synonyms'
```

#### Generating a Plugin Zip

```
./gradlew assemble
```

If you need a different patch level, you can also override the version.  This won't always
work from minor to minor, or major to major, so branches are used for breaking changes.

```
esVersion=7.7.8 ./gradlew assemble
```

#### Installation on local machine

Find the URL for your version of elasticsearch here, and invoke the following command:

```
cd /path/to/elasticsearch && ./bin/elasticsearch-plugin install <RELEASE_URL>
```


##### Other Stuff

http://blog.mikemccandless.com/2012/04/lucenes-tokenstreams-are-actually.html