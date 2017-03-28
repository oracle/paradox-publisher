#!/bin/bash
assembly="$1"
testsToRun="$2"
version="$3"
email="$4"
environment="$5"

autoUrl = ''

read -t 1 publishCredentials
read -t 1 credentials

exec 5>&1 #saves things to a variable, but also let us tee out to stdout
trap "exec 5>&-" EXIT

echo "Running Tests"
guid=$(./runTests.bash "$assembly" "$testsToRun" "$environment" <<< "$credentials" | tee >(cat - >&5) | tail -1) || { echo "Failed to run tests"; exit 1; }
guid=$(tail -1 <<< "$guid")

echo "Running prePublish step"
{
    ./prePublish.bash "$assembly" "$guid" <<< "$publishCredentials" || { echo "Failed to run pre publish step"; }
}

echo "Publishing to Jira"
{
    output=$(./publishToJira.bash "$version" "$assembly" "$guid" <<< "$publishCredentials" | tee >(cat - >&5) | tail -1) || { echo "Failed to publish to jira" && false; } &&
    output=$(tail -1 <<< "$output") &&
    cycleId=$(jq -r .cycleId <<< "$output") &&
    versionId=$(jq -r .versionId <<< "$output") &&
    projectId=$(jq -r .projectId <<< "$output") &&

    echo "Emailing results" &&
    ./emailCycleResults.bash "$versionId" "$cycleId" "$email" "$version Test Results for $environment" "$autoUrl/webtesting/results/$assembly/$guid" "Test ran: $testsToRun";
}

echo "Publishing to Couch"
{
    ./publishToCouch.bash "$assembly" "$guid" "$username" "$password" <<< "$publishCredentials" || { echo "Failed to publish to couch"; }
}

echo "Publishing to Infinity"
{
    ./publishToInfinity.bash "$assembly" "$guid" || { echo "Failed to publish to Infinity"; }
}   
