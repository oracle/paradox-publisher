#!/bin/bash
// 2>/dev/null; /usr/bin/env groovy "$0" "$@"; exit $?;

autoUrl=''

@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.+' )
import groovyx.net.http.RESTClient

if (args.length != 3) {
  println "Usage: ./runTests.bash assembly testsToRun environment"
  println "got $args"
  return 1
}

assembly=args[0]
testsToRun=args[1]
environment=args[2]
println """Running ./runTests.bash "$assembly" "$testsToRun" "$environment" """

body = [
    testsToRun: testsToRun,
    environment: environment
]

// first try reading from stdin, else rundeck replacement, else do not add credentials to request
credentials = '@option.credentials@'
reader = new BufferedReader(new InputStreamReader(System.in))
if (reader.ready()) {
  credentials = reader.readLine()
}

if (credentials.startsWith('@option') == false) {
  body += [credentials: credentials]
}

rest = new RESTClient(autoUrl)
rest.defaultRequestContentType = 'application/json'
resp = rest.post(
  path: "queue/$assembly",
  body: body)

credentials=''
location = resp.headers.Location as String
if (!location) {
  println 'URL is null.  Check to see if the service is up'
  return 1
}

url = new URL(location)
item = (rest.get(path: url.path)).data
while (item.Status == "Running") {
  println "sleeping 60 seconds"
  sleep(60000)
  item = (rest.get(path: url.path)).data
  println "item = $item"
}

println 'Tests complete'
guid = url.path.split('/')[-1]?.trim()
println guid
