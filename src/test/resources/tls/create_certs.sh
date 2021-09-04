#!/bin/bash

set -e

PASSWORD="changeit"

create_keystore() {
  openssl req -x509 -newkey rsa:2048 -days 3650 -nodes -config ssl.conf -keyout "$1.key" -out "$1.crt"
  openssl pkcs12 -export -inkey "$1.key" -in "$1.crt" -out "$1.p12" -password "pass:$2"
}

create_truststore() {
  rm -f "truststore_$1.p12"
  keytool -import -file "$1.crt" -keystore "truststore_$1.p12" -alias "$1" -storetype pkcs12 -noprompt -storepass "$2"
}

# Generate server certs
create_keystore "server_a" "$PASSWORD"
create_keystore "server_b" "$PASSWORD"

# Generate client certs
create_keystore "client_a" "$PASSWORD"
create_keystore "client_b" "$PASSWORD"

# Generate Truststore
create_truststore "client_a" "$PASSWORD"
create_truststore "client_b" "$PASSWORD"
create_truststore "server_a" "$PASSWORD"
create_truststore "server_b" "$PASSWORD"

# Cleanup
rm -- *.key *.crt
