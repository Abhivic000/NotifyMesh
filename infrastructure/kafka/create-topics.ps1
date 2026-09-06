# Creates the platform's Kafka topics with explicit partition counts.
# Kafka's default auto-create-on-first-use gives every topic 1 partition, which
# defeats the whole point of partitioning by userId (PRD section 15) - topics that
# matter are created explicitly instead of relying on that default.
#
# Usage: pwsh ./infrastructure/kafka/create-topics.ps1

$ErrorActionPreference = "Stop"

function New-Topic($name, $partitions) {
    Write-Host "Creating topic '$name' ($partitions partitions)..."
    docker exec notification-kafka /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server localhost:9092 `
        --create --if-not-exists `
        --topic $name `
        --partitions $partitions `
        --replication-factor 1
}

New-Topic "notification.events" 6
New-Topic "notification.email"  3
New-Topic "notification.sms"    3
New-Topic "notification.push"   3
