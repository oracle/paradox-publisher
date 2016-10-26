#!/bin/bash
// 2>/dev/null; /usr/bin/env groovy "$0" "$@"; exit $?;

// Description: This script takes results from Webtesting and imports them into couchdb

autoUrl=''
couchUrl=''
jiraUrl=''

// Job parameters
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.+' )
import groovyx.net.http.RESTClient
assert (assemblyName = args[0]) && 'assemblyName is required'
assert (executionGuid = args[1]) && 'executionGuid is required'

auto = new RESTClient(autoUrl, 'application/json')
couch = new RESTClient(couchUrl, 'application/json')

println "Fetching results from ${auto.defaultURI}results/$assemblyName/$executionGuid ..."
assert (results = auto.get(path: "results/$assemblyName/$executionGuid").data)

println "Putting results to ${couch.defaultURI}automation/$executionGuid"
couch.put(path: "automation/$executionGuid", body: results)
