#!/bin/bash

sbt "runMain deductions.runtime.sparql_cache.PopulateRDFCache"

echo You may now want to run "download-dbpedia.sh" and "populate_with_dbpedia.sh"
