#!/bin/bash
// 2>/dev/null; /usr/bin/env groovy "$0" "$@"; exit $?;

// Description:  This script takes results from Webtrends/webtesting and imports them into a Infinity

autoUrl='http://pdxauto03.englab.webtrends.corp:8080/webtesting/'
scsUrl='http://scs.webtrends.com/dcsqm14ha10000s142fw1a8lw_6m3f/dcs.gif'
def userAgent = 'paradoxPublisherRestClient'
def publisherVersion = '0.1'

// Job parameters
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.+' )
import groovyx.net.http.*
import groovy.json.*
assert (assemblyName = args[0]) && 'assemblyName is required'
assert (executionGuid = args[1]) && 'executionGuid is required'
def isDebug = args[2]?.toBoolean()

auto = new RESTClient(autoUrl, 'application/json')
scs = new RESTClient().with { parser.'image/gif' = parser.defaultParser; it }

println "Fetching results from ${auto.defaultURI}results/$assemblyName/$executionGuid ..."
assert (results = auto.get(path: "results/$assemblyName/$executionGuid").data)
println "Results = $results"

// Send hit with Session Information
def sessionEvent = [
    dcsref: 'Paradox Publisher',
    dcsuri: assemblyName,
    publisherVersion: publisherVersion,
    isDebug: "$isDebug",
    suiteName: assemblyName,
    co_f: executionGuid + isDebug ? new Random().nextInt().toString() : '',
    environment: results.environment,
    date: results.date,
    time: results.time,
    commandLine: results.commandline,
    //TODO: machineName: dcsSip ,
    //TODO: userName: dcsAut ,
    ].findAll{ it.value }.collectEntries { [it.key, URLEncoder.encode(it.value, 'UTF-8')] }

scs.get(uri:"$scsUrl?${sessionEvent.collect{"$it.key=$it.value"}.join('&')}",headers:['User-Agent':userAgent])

// Send hit with Test Information
for(test in results.tests) {
    def testEvent = [
        dcsref: 'Paradox Publisher',
        dcsuri: test.name,
        publisherVersion: publisherVersion,
        isDebug: "$isDebug",
        testName: test.name,
        labels: test.labels.join(';') ,
        state: test.state,
        performance: test.performance.toString(),
        defect: test.defect,
    ].findAll{ it.value }.collectEntries { [it.key, URLEncoder.encode(it.value, 'UTF-8')] }

    scs.get(uri:"$scsUrl?${testEvent.collect{"$it.key=$it.value"}.join('&')}",headers:['User-Agent':userAgent])
}
