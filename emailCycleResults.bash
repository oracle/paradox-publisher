#!/bin/bash
projectId=''
username=''
password=''
domain=''
cssFile=''
versionId="$1"
cycleId="$2"
email="$3"
subject="$4"
header="$5"
footer="$6"
level="INFO"
debug()   { [ "$level" = DEBUG ] && echo $@; }
verbose() { [ "$level" = DEBUG -o "$level" = VERBOSE ] && echo $@; }
info()    { [ "$level" = DEBUG -o "$level" = VERBOSE -o "$level" = INFO ] && echo $@; }
warn()    { [ "$level" = DEBUG -o "$level" = VERBOSE -o "$level" = INFO -o "$level" = WARN ] && echo $@; }
error()   { [ "$level" = DEBUG -o "$level" = VERBOSE -o "$level" = INFO -o "$level" = WARN -o "$level" = ERROR ] && echo $@; }

hashed = $(echo -n '$username:$password' | base64)
curlOptions=(
    -s
    -H "Authorization:Basic $hashed")
[ "$level" = DEBUG ] && curlOptions+=('-v');

exportUrl=$(curl "${curlOptions[@]}" "http://jira/rest/zapi/latest/cycle/$cycleId/export?projectId=$projectId&versionId=$versionId" | jq -r '.url')
verbose "exportUrl = $exportUrl"

info "Fetching cycle ($cycleId) from jira for project ($projectId) and version ($versionId)"
dataRaw=$(curl "${curlOptions[@]}" "$exportUrl" | tr -d '\r')
verbose "dataRaw Len = ${#dataRaw}"
debug "dataRaw = $dataRaw"

pass=$(grep -c ',PASS,' <<< "$dataRaw")
fail=$(grep -c ',FAIL,' <<< "$dataRaw")
skipped=$(grep -c ',UNEXECUTED,' <<< "$dataRaw")
inconclusive=$(grep -c ',BLOCKED,' <<< "$dataRaw")
knownfail=$(grep -c ',KNOWNFAIL,' <<< "$dataRaw")
total=$((pass + fail + skipped + inconclusive + knownfail))
verbose "summary:"
verbose "  pass:         $pass"
verbose "  fail:         $fail"
verbose "  skipped:      $skipped"
verbose "  inconclusive: $inconclusive"
verbose "+ knownfail:    $knownfail"
verbose "  ------------------------"
verbose "  total:        $total"

# This function is borrowed from  http://lorance.freeshell.org/csv/csv.txt  which the author has placed in public domain
content=$(awk '
function csv_parse(string,csv,sep,quote,escape,newline,trim,fields,pos,strtrim) {
    if (length(string) == 0) return 0
    string = sep string
    fields = 0
    while (length(string) > 0) {
        if (trim && substr(string, 2, 1) == " ") {
            if (length(string) == 1) return fields
            string = substr(string, 2)
            continue
        }
        strtrim = 0
        if (substr(string, 2, 1) == quote) {
            pos = 2
            do {
                pos++
                if (pos != length(string) &&
                    substr(string, pos, 1) == escape &&
                    index(quote escape, substr(string, pos + 1, 1)) != 0) {
                    string = substr(string, 1, pos - 1) substr(string, pos + 1)
                } else if (substr(string, pos, 1) == quote) {
                    strtrim = 1
                } else if (pos >= length(string)) {
                    if (newline == -1) {
                        return -1
                    } else if (newline) {
                        if (getline == -1) return -4
                        string = string newline $0
                    }
                }
            } while (pos < length(string) && strtrim == 0)
            if (strtrim == 0) {
                return -3
            }
        } else {
            if (length(string) == 1 || substr(string, 2, 1) == sep) {
                fields++
                csv[fields] = ""
                if (length(string) == 1) return fields
                string = substr(string, 2)
                continue
            }
            pos = index(substr(string, 2), sep)
            if (pos == 0) {
                fields++
                csv[fields] = substr(string, 2)
                return fields
            }
        }
        if (trim && pos != (length(string) + strtrim) && substr(string, pos + strtrim, 1) == " ") {
            trim = strtrim
            while (pos < length(string) && substr(string, pos + trim, 1) == " ") {
                trim++
            }
            string = substr(string, 1, pos + strtrim - 1) substr(string,  pos + trim)
            if (!strtrim) {
                pos -= trim
            }
        }
        if ((pos != length(string) && substr(string, pos + 1, 1) != sep)) {
            return -4
        }
        fields++
        csv[fields] = substr(string, 2 + strtrim, pos - (1 + strtrim * 2))
        if (pos == length(string)) {
            return fields
        } else {
            string = substr(string, pos + 1)
        }
    }
    return fields
}

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
      <tr><th>Started On</th><th>Environment</th><th>Total</th><th>PASS</th><th>FAIL</th><th>SKIPPED</th><th>INCONCLUSIVE</th><th>KNOWNFAIL</th></tr>\
      <tr><td>" startDate "</td><td>" environment "</td><td>" total "</td><td class=\"PASS\">" pass "</td><td class=\"FAIL\">" fail "</td><td class=\"UNEXECUTED\">" skipped "</td><td class=\"BLOCKED\">" inconclusive "</td><td class=\"KNOWNFAIL\">" knownfail "</td></tr>\
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
       <td class=\"" ( csv[4] != "-" ? "KNOWNFAIL" : csv[2] ) "\">" ( csv[4] != "-" ? "<a href=\"http://jira/browse/" csv[4] "\">KNOWNFAIL</a>" : csv[2] ) "</td>\
      </tr>"
}

END {
    print "\
    </table>\
  </body>\
</html>"
}' total="$total" pass="$pass" fail="$fail" skipped="$skipped" inconclusive="$inconclusive" knownfail="$knownfail" <<< "$dataRaw")

info "Sending results to $email"
debug "$content"

#This relies on a correctly functioning sendmail installed and configured.
#To send mail through some smtp servers, you may need to put
#   define(`SMART_HOST',`MTA-SERVER-HERE')dnl
#inside of /etc/mail/sendmail.mc, and then run sendmailconfig
sendmail -t "$email" <<< "From:$username@$domain
Subject: $subject
Content-Type: text/html
MIME-Version: 1.0

$header
$content
$footer"
