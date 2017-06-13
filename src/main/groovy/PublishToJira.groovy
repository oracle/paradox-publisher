import groovy.util.logging.Log4j
import groovyx.net.http.*
import groovy.json.*

/**
 * Publishes results to jira via zapi into a new jira test cycle
 */
@Log4j
class PublishToJira {
    static auto = new RESTClient(Config.autoUrl, 'application/json')
    static jira = new RESTClient(Config.jiraUrl, 'application/json')
    static zapi = new RESTClient(Config.zapiUrl, 'application/json')
    static cache = [:]
    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'publishToJira')
        cli.with {
            a required: true, longOpt: 'assembly', args: 1, 'The name of the test suite to publish'
            g required: true, longOpt: 'guid', args: 1, 'The guid of the test suite to publish'
            h longOpt: 'help', 'Show usage information'
            v required: true, longOpt: 'version', args: 1, 'The version for the jira cycle that will be created'
        }

        if (args?.grep(['-h', '--help'])) {
            cli.usage()
            return
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        log.info JsonOutput.toJson(publish(options.a, options.g, options.v))
    }

    static publish(String assembly, String guid, String version) {
        def basicAuth = 'Basic ' + "$Config.jiraUsername:$Config.jiraPassword".bytes.encodeBase64()
        jira.headers += [Authorization: basicAuth]
        zapi.headers += [Authorization: basicAuth]
        def projectKey = Config.jiraProjectKey
        def projectId = Config.jiraProjectId
        def results = getResults(assembly, guid)
        def versionId = getVersionId(projectKey, version)
        def doc = getDocumentation(assembly)
        def cycleId = createCycle(results, projectId)

        log.info 'Adding tests to cycle'
        for (test in results.tests) {
            addTestToCycle(test, doc, cycleId, versionId)
        }

        log.info 'Done publishing all tests to jira'
        [cycleId: cycleId, projectId: projectId, versionId: versionId]
    }

    private static addTestToCycle(test, doc, cycleId, versionId) {
        String nameNoArgs = test.name.takeWhile { it != '(' }
        test.comment = test.comment?.take(766) //Max size for jira execution comment
        test.labels = test.labels ?: doc[nameNoArgs]?.categories ?: []
        test.summary = doc[nameNoArgs]?.summary ?: ''
        test.testSteps = doc[nameNoArgs]?.testSteps ?: []
        test.assertions = doc[nameNoArgs]?.assertions ?: []

        log.info "Adding $test to the cycle"
        def issue = getIssue(test, nameNoArgs)

        test.priority = 'smoke' in test.labels*.toLowerCase() ? '2' : '3'
        def body = [
            fields: [
                project: [key: Config.jiraProjectKey],
                summary: test.name,
                issuetype: [name: 'Test'],
                description: test.summary,
                labels: test.labels,
                priority: [id: test.priority],
                customfield_11401: [[value: 'Yes']],
                assignee: [name: 'automation']]]
        if (issue) {
            updateIssue(issue, test, body)
        } else {
            issue = createIssue(test, body)
        }

        def status = [PASS: 1, FAIL: 2, WIP: 3, BLOCKED: 4, KNOWN_FAIL: 5, UNEXECUTED: -1][test.state] ?: 0
        if (!status) {
            log.info "${issue.key}: ${test.state} must be one of PASS, FAIL, WIP, BLOCKED, KNOWN_FAIL, or UNEXECUTED"
        }

        def executionId = zapi.post(
            path: 'execution',
            body: [
                issueId: issue.id,
                versionId: versionId,
                cycleId: cycleId,
                projectId: Config.jiraProjectId,
                executionStatus: status
            ]
        ).data*.key[0]

        log.info "${issue.key}: Updating status to $status"
        zapi.put(
            path: "execution/$executionId/execute",
            body: [status: status, defectList: [test.defect], comment: test.comment, updateDefectList: 'true']
        )
    }

    private static getIssue(test, name) {
        if (test.id) {
            log.info "Issue has JiraID(${test.id}) associated with it already"
            return jira.get(path: "issue/${test.id}").data
        } else if (cache[test.name]) {
            log.info 'Found issue in cache'
            return cache[test.name]
        }

        log.info "Searching for issue by name of $name"
        def issues = jira.get(
            path: 'search',
            query: [
                jql: "summary~\"${name}*\"",
                maxResults: 1000,
                fields: 'summary,description,labels,priority'
            ]
        ).data.issues
        issues.collectEntries(cache) { [(it.fields.summary): it] }
        cache[test.name]
    }

    private static createIssue(test, body) {
        log.info 'Creating new jira issue'
        log.info "Priority = $test.priority"
        def issue = jira.post(path: 'issue', body: body).data
        test.testSteps.each { zapi.post(path: "teststep/${issue.id}", body: [step: it]) }
        test.assertions.each { zapi.post(path: "teststep/${issue.id}", body: [result: it]) }
        issue
    }

    private static updateIssue(issue, test, body) {
        if (hasChanged(issue, test)) {
            jira.put(path: "issue/${issue.key}", body: body)
        }

        def steps = zapi.get(path: "teststep/$issue.id").data
        issue.testSteps = steps*.htmlStep.findAll { it }
        issue.assertions = steps*.htmlResult.findAll { it }

        // Have the steps been changed
        if (test.testSteps != issue.testSteps || test.assertions != issue.assertions) {
            log.info "${issue.key}: Updating testSteps from ${test.testSteps} to ${issue.testSteps} and " +
                "assertions from ${test.assertions} to ${issue.assertions}"
            steps*.id.each { zapi.delete(path: "teststep/${issue.id}/$it") }
            test.testSteps.each { zapi.post(path: "teststep/${issue.id}", body: [step: it]) }
            test.assertions.each { zapi.post(path: "teststep/${issue.id}", body: [result: it]) }
        }
    }

    private static hasChanged(issue, test) {
        def changed = false
        if (issue.fields.summary != test.name) {
            log.info "${issue.key}: Updating summary from ${issue.fields.summary} to ${test.name}"
            changed = true
        }

        if (issue.fields.labels != test.labels) {
            log.info "${issue.key}: Updating labels from ${issue.fields.labels} to ${test.labels}"
            changed = true
        }

        if (issue.fields.description != test.summary && test.summary) {
            log.info "${issue.key}: Updating description from ${issue.fields.description} to ${test.summary}"
            changed = true
        }

        if (issue.fields.priority.id != test.priority) {
            log.info "${issue.key}: Updating priority from ${issue.fields.priority.id} to ${test.priority}"
            changed = true
        }
        changed
    }

    private static String createCycle(results, String projectId) {
        log.info 'Creating a new execution cycle'
        def date = Date.parse('yyyy-MM-dd', results.date).format('d/MMM/yy')
        def cycleId = zapi.post(
            path: 'cycle',
            body: [
                name: date,
                environment: results.environment,
                startDate: date,
                projectId: projectId,
                versionId: versionId]
        ).data.id
        log.info "Created new cycle with id $cycleId"
        cycleId
    }

    private static getDocumentation(String assembly) {
        log.info "Fetching documentation from ${auto.uri}doc/$assembly ..."
        def doc = [:]
        try {
            def docList = auto.get(path: "doc/$assembly").data
            docList.collectEntries(doc) { [(it.name): it] }
        } catch (e) {
            log.error "Unable to get documentation for $assembly", e
        }
        doc
    }

    private static String getVersionId(String projectKey, String version) {
        log.info 'Fetching versionId'
        def versionId = jira.get(path: "project/$projectKey/versions").data
            .findAll { it.name.contains(version) }
            .max { it.startDate }
            .id
        log.info "VersionId = $versionId"
        versionId
    }

    private static getResults(String assembly, String guid) {
        log.info "Fetching results from ${auto.uri}results/$assembly/$guid ..."
        def results = auto.get(path: "results/$assembly/$guid").data
        log.info "Results = $results"
        results
    }
}

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
jira.put(path: "issue/${issue.key}",
  body: [fields: [labels: ['label3', 'label4'], description: 'UPDATED publishToJiraTest']])

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
