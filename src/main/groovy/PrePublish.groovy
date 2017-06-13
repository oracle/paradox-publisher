import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient
import org.apache.commons.math3.stat.descriptive.StatisticalSummary
import org.apache.commons.math3.stat.inference.TestUtils

/**
 * Updates existing results in couchdb with augmented data such as known bug and performance information
 */
@Log4j
class PrePublish implements Publisher {
    def auto = new RESTClient(config.autoUrl, 'application/json')
    def couch = new RESTClient(config.couchUrl, 'application/json')
    def jira = new RESTClient(config.jiraUrl, 'application/json')

    static void main(String[] args) {
        new PrePublish().parseCommandline(args)
    }

    def publish(String assembly, String guid) {
        def basicAuth = 'Basic ' + "$config.jiraUsername:$config.jiraPassword".bytes.encodeBase64()
        jira.headers += [Authorization: basicAuth]
        log.info "Fetching results from ${auto.uri}results/$assembly/$guid ..."
        def results = auto.get(path: "results/$assembly/$guid").data
        TestResult[] tests = results.tests

        // We do not want a failure to contact jira to prevent results from showing in couch
        try {
            log.info 'augmenting with jira info'
            def keysWithDefects = results.tests.findResults { it.defect }.unique().join(',')
            log.info "keysWithDefects: $keysWithDefects"

            if (keysWithDefects) {
                def jiraResponse = jira.get(path: 'search', query: [jql: "key in($keysWithDefects)", fields: 'status'])
                log.info "jiraResponse: $jiraResponse"
                def keysWithStatuses = jiraResponse.data.issues.collectEntries { [(it.key): it.fields.status.name] }
                log.info "keysWithStatuses: $keysWithStatuses"

                results.tests = tests.collect { it << [jiraStatus: keysWithStatuses[it.defect]] }
            }
        } catch (Exception ex) {
            log.warn 'Unable to augment results with jira status', ex
        }

        tests.findAll { it.performance }.each { test ->
            def perfResult = couch.get(
                path: 'automation/_design/testResults/_view/performance',
                query: [startkey: /["${test.name}","",""]/, endkey: /["${test.name}_","",""]/]
            ).data

            if (perfResult) {
                def lastPerfTest = perfResult.rows[-1].value as StatisticalSummary
                def currentPerfTest = test.performance as StatisticalSummary
                if (currentPerfTest && lastPerfTest) {
                    def tTestResult = TestUtils.tTest(lastPerfTest, currentPerfTest, 0.05d)

                    if (!tTestResult) {
                        test.state = 'FAIL'
                        test.comment = 'Statistically significant difference of means detected: ' +
                            'Previous mean: ' + lastPerfTest.mean +
                            'Current mean: ' + currentPerfTest.mean
                    }
                }
            }
        }

        auto.put(path: "results/$assembly/$guid", body: results)
    }
}

class TestResult {
    String name
    String state
    String defect
    String comment
    StatisticalSummary performance
}
