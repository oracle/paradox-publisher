package com.oracle.infy.qa.paradoxpublisher

import groovy.util.logging.Log4j2
import groovyx.net.http.RESTClient

/**
 * Fetch a cycle from jira and email
 */
@Log4j2
class EmailCycleResults {
    def config = new Config()

    RESTClient zapi

    static void main(String[] args) {
        def cli = new CliBuilder(
            usage: '''
                | emailCycleResults [OPTION]... <cycleId> <version> <email>...
                |
                |arguments:
                | <cycleId>   The id of the cycle to export
                | <version>   The version of the cycle to export
                | <email>...  The email address(es) results will be sent to
                |
                |options:'''.stripMargin(),
            stopAtNonOption: false
        )
        cli.with {
            f longOpt: 'footer', args: 1, 'The footer/bottom line of the email  [default: ""]'
            h longOpt: 'help', 'Show usage information'
            s longOpt: 'subject', args: 1, 'The subject line of the email [default: "Test Results"]'
            t longOpt: 'header', args: 1, 'The header/top line of the email [default: Url of cycle]'
        }

        if (args?.grep(['-h', '--help'])) {
            cli.usage()
            return
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        if (options.arguments().size() < 3) {
            cli.writer << 'error: Too few arguments\n'
            cli.usage()
            return
        }

        String cycleId = options.arguments()[0]
        String version = options.arguments()[1]
        List<String> addresses = options.arguments()[2..-1]
        new EmailCycleResults().email(
            version,
            cycleId,
            addresses,
            options.s as String,
            options.t as String,
            options.f as String)
    }

    void email(String versionId, String cycleId, List<String> email, String subject, String header, String footer) {
        if (!config.with { zapiUrl && jiraProjectId && jiraUsername && jiraPassword }) {
            log.warn "Missing config values: unable to execute ${this.getClass().name}"
            return null
        }

        zapi = new RESTClient(config.zapiUrl, 'application/json')
        def basicAuth = 'Basic ' + "$config.jiraUsername:$config.jiraPassword".bytes.encodeBase64()
        zapi.headers += [Authorization: basicAuth]

        log.info "Fetching cycle ($cycleId) from jira for project ($config.jiraProjectId) and version ($versionId)"
        def exportUrl = zapi.get(
            path: '/cycle/$cycleId/export',
            query: [projectId: config.jiraProjectId, versionId: versionId]).url
        log.debug exportUrl
        def dataRaw = zapi.get(uri: exportUrl)

        def pass = dataRaw.count { it.contains(',PASS,') }
        def fail = dataRaw.count { it.contains(',FAIL,') }
        def skipped = dataRaw.count { it.contains(',UNEXECUTED,') }
        def inconclusive = dataRaw.count { it.contains(',BLOCKED,') }
        def knownfail = dataRaw.count { it.contains(',KNOWN FAIL,') }
        def total = pass + fail + skipped + inconclusive + knownfail
        log.debug """\
            |summary:
            |  pass:         $pass
            |  fail:         $fail
            |  skipped:      $skipped
            |  inconclusive: $inconclusive
            |+ knownfail:    $knownfail
            |  ------------------------
            |  total:        $total""".stripMargin()

        /*
NR == 3 {
    csv_parse($0, csv, ",", "\"", "\"", "\\n", 1)
    startDate = csv[2]
    sub(/.*: /,"",startDate)
    environment = csv[4]
    sub(/.*: /,"",environment)
    print "\
<html>\
  <head>\
    <link rel=\"stylesheet\" type=\"text/css\" href=\"$cssFile\">\
  </head>\
  <body>\
    <table style=\"width:100%;border-collapse:collapse;\">\
      <tr><th>Started On</th><th>Environment</th><th>Total</th><th>PASS</th><th>FAIL</th><th>SKIPPED</th>
      <th>INCONCLUSIVE</th><th>KNOWNFAIL</th></tr>\
      <tr><td>" startDate "</td><td>" environment "</td><td>" total "</td><td class=\"PASS\">" pass "</td>
      <td class=\"FAIL\">" fail "</td><td class=\"UNEXECUTED\">" skipped "</td>
      <td class=\"BLOCKED\">" inconclusive "</td><td class=\"KNOWNFAIL\">" knownfail "</td></tr>\
    </table>\
    <br>\
    <table style=\"width:100%;border-collapse:collapse;\" class=\"details\">\
      <tr><th>Name</th><th>State</th></tr>"
}
NR > 4 {
    csv_parse($0, csv, ",", "\"", "\"", "\\n", 1)
    print "\
      <tr>\
       <td><a href=\"http://jira/browse/" csv[1] "\">" csv[3] "</a></td>\
       <td class=\"" ( csv[4] != "-" ? "KNOWNFAIL" : csv[2] ) "\">" ( csv[4] != "-" ? "<a href=\"http://jira/browse/"
       csv[4] "\">KNOWNFAIL</a>" : csv[2] ) "</td>\
      </tr>"
}

END {
    print "\
    </table>\
  </body>\
</html>"
}' total="$total" pass="$pass" fail="$fail" skipped="$skipped" inconclusive="$inconclusive" knownfail="$knownfail" <<<
"$dataRaw")

info "Sending results to $email"
debug "$content"

#This relies on a correctly functioning sendmail installed and configured.
#To send mail through some smtp servers, you may need to put
#   define(`SMART_HOST',`MTA-SERVER-HERE')dnl
#inside of /etc/mail/sendmail.mc, and then run sendmailconfig
*/

        String html = ''

        def emailContent = """From:$config.jiraUsername
To: $email
Subject: ${subject ?: 'Test Results'}
Content-Type: text/html
MIME-Version: 1.0

${header ?: "$zapi.uri/cycle/$cycleId/export"}
$html
${footer ?: ''}"""

        assert false, "TODO: email contents of $emailContent"
    }
}
