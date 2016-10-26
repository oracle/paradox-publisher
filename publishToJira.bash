#!/bin/bash
// 2>/dev/null; /usr/bin/env groovy "$0" "$@"; exit $?;

// Description: This script takes results from Webtrends/webtesting and imports them into a new jira test cycle

autoUrl=''
couchUrl=''
zapiUrl=''
projectKey = ''
projectId = ''

// Job parameters
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.+' )
import groovyx.net.http.*
import groovy.json.*
assert (versionName = args[0]) && 'versionName is required'
assert (assemblyName = args[1]) && 'assemblyName is required'
assert (executionGuid = args[2]) && 'executionGuid is required'

// first try reading from stdin, else rundeck replacement, else do not add credentials to request
publishCredentials = '@option.publishCredentials@'
reader = new BufferedReader(new InputStreamReader(System.in))
if (reader.ready()) {
  publishCredentials = reader.readLine()
}

assert(!publishCredentials.startsWith('@option')) && 'publish credentials are required'

// Setup RESTClients
auto = new RESTClient(autoUrl, 'application/json')
jira = new RESTClient(jiraUrl, 'application/json')
zapi = new RESTClient(zapiUrl, 'application/json')

//https://answers.atlassian.com/questions/939/using-jira-rest-api-from-groovy
//rest.auth.basic(user, pass) doesn't work with jira because jira doesn't send a challenge with its 401 response
[jira, zapi]*.defaultRequestHeaders*.Authorization = 'Basic ' +  publishCredentials.bytes.encodeBase64()

println 'Fetching versionId'
assert (versionId = jira.get(path: "project/$projectKey/versions").data.findAll { it.name.contains(versionName) }.max { it.startDate }.id)
println "VersionId = $versionId"

println "Fetching results from ${auto.defaultURI}results/$assemblyName/$executionGuid ..."
assert (results = auto.get(path: "results/$assemblyName/$executionGuid").data)
assert (environment = results.environment)
assert (date = results.date)
date = Date.parse('yyyy-MM-dd', date).format('d/MMM/yy')
println "Results = $results"

println "Fetching documentation from ${auto.defaultURI}doc/$assemblyName ..."
def doc = [:]
try {
  def docList = auto.get(path: "doc/$assemblyName").data
  docList.collectEntries(doc) { [(it.name): it] }
} catch(e) {
  println "Unable to get documentation for $assemblyName"
}

println 'Creating a new execution cycle'
assert (cycleId = zapi.post(
  path: 'cycle',
  body: [name: date, environment: environment, startDate: date, projectId: projectId, versionId: versionId]
).data.id)
println "Created new cycle with id $cycleId"

println 'Adding tests to cycle'
cache = [:]
for (test in results.tests) {
    def nameNoArgs = test.name.takeWhile {it != '('}
    test.comment = test.comment?.take(766) //Max size for jira execution comment
    test.labels = test.labels ?: doc[nameNoArgs]?.categories ?: []
    test.summary = doc[nameNoArgs]?.summary ?: ''
    test.testSteps = doc[nameNoArgs]?.testSteps ?: []
    test.assertions = doc[nameNoArgs]?.assertions ?: []

    println "Adding $test to the cycle"
    if (test.id) {
        println "Issue has JiraID(${test.id}) associated with it already"
        issue = jira.get(path: "issue/${test.id}").data
    } else if (cache[test.name]) {
        println "Found issue in cache"
        issue = cache[test.name]
    } else {
        println "Searching for issue by name of $nameNoArgs"
        def issues = jira.get(path: 'search', query: [jql: "summary~\"${nameNoArgs}*\"", maxResults: 1000, fields: 'summary,description,labels,priority']).data.issues
        issues.collectEntries(cache) { [(it.fields.summary): it] }
        issue = cache[test.name]
    }

    def priority = 'smoke' in test.labels*.toLowerCase() ? '2' : '3'
    def body = [
        fields: [
            project: [key: projectKey],
            summary: test.name,
            issuetype: [name: 'Test'],
            description: test.summary,
            labels: test.labels,
            priority: [id: priority],
            customfield_11401: [[value: 'Yes']],
            assignee: [name: 'automation']]]
    if (!issue) {
        println "Creating new jira issue"
        println "Priority = $priority"
        issue = jira.post(path: 'issue', body: body).data
        test.testSteps.each { zapi.post(path: "teststep/${issue.id}", body: [step: it] ) }
        test.assertions.each { zapi.post(path: "teststep/${issue.id}", body: [result: it] ) }
    } else {
        // Has the issue been changed?
	def changed = false
	if (issue.fields.summary != test.name) {
            println "${issue.key}: Updating summary from ${issue.fields.summary} to ${test.name}"
            changed = true
	}

        if (issue.fields.labels != test.labels) {
            println "${issue.key}: Updating labels from ${issue.fields.labels} to ${test.labels}"
            changed = true
	}

        if (issue.fields.description != test.summary && test.summary) {
            println "${issue.key}: Updating description from ${issue.fields.description} to ${test.summary}"
            changed = true
        }

        if (issue.fields.priority.id != priority) {
            println "${issue.key}: Updating priority from ${issue.fields.priority.id} to ${priority}"
            changed = true
	}

        if (changed) {
            jira.put(path: "issue/${issue.key}", body: body)
        }

        steps = zapi.get(path: "teststep/$issue.id").data
        issue.testSteps = steps*.htmlStep.findAll { it }
        issue.assertions = steps*.htmlResult.findAll { it }

        // Have the steps been changed
        if (test.testSteps != issue.testSteps || test.assertions != issue.assertions) {
	    println "${issue.key}: Updating testSteps from ${test.testSteps} to ${issue.testSteps} and assertions from ${test.assertions} to ${issue.assertions}"
	    steps*.id.each { zapi.delete(path: "teststep/${issue.id}/$it") }
            test.testSteps.each { zapi.post(path: "teststep/${issue.id}", body: [step: it] ) }
            test.assertions.each { zapi.post(path: "teststep/${issue.id}", body: [result: it] ) }
        }
    }

    def status = [PASS: 1, FAIL: 2, WIP: 3, BLOCKED: 4, KNOWN_FAIL: 5, UNEXECUTED: -1][test.state] ?: 0
    if (!status) { println "${issue.key}: ${test.state} not recognized as one of PASS, FAIL, WIP, BLOCKED, KNOWN_FAIL, or UNEXECUTED" }

    def executionId = zapi.post(path: 'execution', body: [issueId: issue.id, versionId: versionId, cycleId: cycleId, projectId: projectId, executionStatus: status]).data*.key[0]

    println "${issue.key}: Updating status to $status"
    zapi.put(
      path: "execution/$executionId/execute",
      body: [status: status, defectList: [test.defect], comment: test.comment, updateDefectList:'true']
    )
}

println 'Done publishing all tests to jira'
println JsonOutput.toJson([cycleId: cycleId, projectId: projectId, versionId: versionId])

/*
tests
//Get versionId
versionId = jira.get(path: "project/QA/versions").data[0].id

//Create cycle
def cycleId = zapi.post(
  path: 'cycle',
  body: [
      name: 'publishToJiraTest',
      environment: "env",
      startDate: '01/Jan/00',
      projectId: '11400',
      versionId: '-1']
).data.id

//Create issue
def issue = jira.post(
  path: 'issue',
  body: [
    fields: [
        project: [key: 'QA'],
        summary: 'publishToJiraTest',
        issuetype: [name: 'Test'],
        description: 'delete me',
        labels: ['label1', 'label2'],
        priority: [id: 5],
        customfield_11401: [[value: 'Yes']],
        assignee: [name: 'automation']]]).data

issue = jira.get(path: "issue/${issue.key}").data

//Update issue
jira.put(path: "issue/${issue.key}", body: [fields: [labels: ['label3', 'label4'], description: 'UPDATED publishToJiraTest']])

//Create test step
zapi.post(path: "teststep/${issue.id}", body: [step: 'A publish to jira test step'] )

//Get test steps
def stepId = zapi.get(path: "teststep/$issue.id").data[-1].id

//Delete step
zapi.delete(path: "teststep/${issue.id}/${step.id}")

//Add issue to cycle
def executionId = zapi.post(
  path: 'execution',
  body: [
      issueId: issue.id,
      versionId: versionId,
      cycleId: cycleId,
      projectId: projectId,
      executionStatus: status]).data*.key[0]

    println "Updating status to $status"
    zapi.put(
      path: "execution/$executionId/execute",
      body: [status: status, defectList: [test.defect], comment: test.comment, updateDefectList:'true']
    )

//Delete issue
jira.delete(path: "issue/$issue.id/$id")

//Delete cycle
zapi.delete(path: "cycle/$id")
*/
