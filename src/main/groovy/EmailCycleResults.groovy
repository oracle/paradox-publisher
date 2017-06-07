import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient

/**
 * Fetch a cycle from jira and email
 */
@Log4j
class EmailCycleResults {
    static zapi = new RESTClient(Config.zapiUrl, 'application/json')

    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'emailCycleResults [-a assembly] [-g guid]')
        cli.with {
            h longOpt: 'help', 'Show usage information'
            c longOpt: 'cycleId', args: 1, 'The id of the cycle to export'
            m longOpt: 'email', args: 1 'The email address(es) results will be sent to'
            s longOpt: 'email', args: 1 'The subject line of the email'
            t longOpt: 'email', args: 1 'The header/top line of the email'
            f longOpt: 'email', args: 1 'The footer/bottom line of the email'
            v longOpt: 'version', args: 1, 'The version of the cycle to export'
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        email(options.v, options.c, options.m, options.s, options.t, options.f)
    }

    static void email(String versionId, String cycleId, String email, String subject, String header, String footer) {
        def basicAuth = 'Basic ' + "$Config.jiraUsername:$Config.jiraPassword".bytes.encodeBase64()
        zapi.headers += [Authorization: basicAuth]

        def exportUrl = zapi.get(
            path: '/cycle/$cycleId/export',
            query: [projectId: Config.jiraProjectId, versionId: versionId]).url
        log.debug exportUrl
        log.info "Fetching cycle ($cycleId) from jira for project ($Config.jiraProjectId) and version ($versionId)"
        def dataRaw = zapi.get(uri: exportUrl)

        def pass = dataRaw.count { it.contains(',PASS,') }
        def fail = dataRaw.count { it.contains(',FAIL,') }
        def skipped = dataRaw.count { it.contains(',UNEXECUTED,') }
        def inconclusive = dataRaw.count { it.contains(',BLOCKED,') }
        def knownfail = dataRaw.count { it.contains(',KNOWNFAIL,') }
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

        def emailContent = """From:$Config.jiraUsername
To: $email
Subject: $subject
Content-Type: text/html
MIME-Version: 1.0

$header
$html
$footer"""

        assert false, "TODO: email contents of $emailContent"
    }
}
