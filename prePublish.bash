#!/bin/bash
// 2>/dev/null; /usr/bin/env groovy "$0" "$@"; exit $?;

// Description: This script takes results from Webtrends/webtesting and imports them into couchdb

autoUrl=''
couchUrl=''
jiraUrl=''

// Job parameters
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.+' )
import groovyx.net.http.RESTClient
assert (assemblyName = args[0]) && 'assemblyName is required'
assert (executionGuid = args[1]) && 'executionGuid is required'

// first try reading from stdin, else rundeck replacement, else do not add credentials to request
publishCredentials = '@option.publishCredentials@'
reader = new BufferedReader(new InputStreamReader(System.in))
if (reader.ready()) {
  publishCredentials = reader.readLine()
}

assert(!publishCredentials.startsWith('@option')) && 'publish credentials are required'

def basicAuth = 'Basic ' + publishCredentials.bytes.encodeBase64()
auto = new RESTClient(autoUrl, 'application/json')
couch = new RESTClient(couchUrl, 'application/json')
jira = new RESTClient(jiraUrl, 'application/json')
jira.headers += [Authorization: basicAuth]

println "Fetching results from ${auto.defaultURI}results/$assemblyName/$executionGuid ..."
assert (results = auto.get(path: "results/$assemblyName/$executionGuid").data)

// We do not want a failure to contact jira to prevent results from showing in couch
try {
    println 'augmenting with jira info'
    def keysWithDefects = results.tests.findResults { it.defect }.unique().join(',')
    println "keysWithDefects: " + keysWithDefects

    if (keysWithDefects) {
        def jiraResponse = jira.get(path: 'search', query: [jql: "key in($keysWithDefects)", fields: 'status'])
        println "jiraResponse: " + jiraResponse
        def keysWithStatuses = jiraResponse.data.issues.collectEntries { [(it.key): it.fields.status.name] }
        println "keysWithStatuses: " + keysWithStatuses

        results.tests = results.tests.collect { it << [jiraStatus: keysWithStatuses[it.defect]] }
    }
} catch (Exception ex) {
    println 'unable to augment results with jira status ' + ex
}

results.tests.find { it.performance }.each {
    def perfResult = couch.get(
        path: "automation/_design/testResults/_view/performance",
        query: [startkey: /["${it.name}","",""]/, endkey: /["${it.name}_","",""]/]
    ).data

    if (perfResult) {
        def lastPerfTest = perfResult.rows[-1].value as StatisticalSummary
        def currentPerfTest = it.performance as StatisticalSummary

        if (currentPerfTest && lastPerfTest) {
            def tTestResult = TestUtils.tTest(lastPerfTest, currentPerfTest, 0.05d)

            if (!tTestResult) {
                it.state = 'FAIL'
                it.comment = "Statistically significant difference of means detected:" +
                    " Previous mean: " + (lastPerfTest.sum() / lastPerfTest.size()) +
                    " Current mean: " + (currentPerfTest.sum() / currentPerfTest.size())
            }
        }
    }
}

auto.put(path: "results/$assemblyName/$executionGuid", body: results)
