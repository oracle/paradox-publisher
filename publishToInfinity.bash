#!/bin/bash
// 2>/dev/null; /usr/bin/env groovy "$0" "$@"; exit $?;

// Description:  This script takes results from Webtrends/Paradox-testing and imports them into Infinity

autoUrl = 'http://pdxauto03.englab.webtrends.corp:8080/webtesting/'
scsUrl = 'http://scs.webtrends.com/dcsqm14ha10000s142fw1a8lw_6m3f/dcs.gif'
publisherVersion = '0.1'

// Job parameters
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.+' )
import groovyx.net.http.*
import groovy.json.*
assert (assemblyName = args[0]) && 'assemblyName is required'
assert (executionGuid = args[1]) && 'executionGuid is required'
isDebug = args[2]?.toBoolean()

def cof = executionGuid + (isDebug ? new Random().nextInt().toString() : '')
auto = new RESTClient(autoUrl, 'application/json')
scs = new RESTClient().with { parser.'image/gif' = parser.defaultParser; it }

println "Fetching results from ${auto.defaultURI}results/$assemblyName/$executionGuid ..."
assert (results = auto.get(path: "results/$assemblyName/$executionGuid").data)
println "Results = $results"

def sendEvent(Map event) {
    event << [
        dcsref: '()aradox',
        publisherVersion: publisherVersion,
        isDebug: "$isDebug",
    ]
    event = event.findAll { it.value }.collectEntries { [it.key, URLEncoder.encode(it.value, 'UTF-8')] }
    def queryString = event.collect { "$it.key=$it.value" }.join('&')
    scs.get(uri: "$scsUrl?$queryString", headers: ['User-Agent': 'paradoxRestClient'])
}

//Send Test Suite
sendEvent(
    'wt.co_f': cof,
    dcsuri: assemblyName,
    suiteName: assemblyName,
    environment: results.environment,
    date: results.date,
    time: results.time,
    commandLine: results.commandline,
    dscsip: InetAddress.localHost.hostName,
    dcsaut: System.getProperty('user.name')
    )

//Send Test Results
for(test in results.tests) {
    sendEvent(
        'wt.co_f': cof,
        dcsuri: test.name.replaceAll('\\.', '/'),
        testName: test.name,
        'wt.cg_n': test.labels.join(';'),
        state: test.state,
        performance: test.performance.toString(),
        defect: test.defect,
    )
}
