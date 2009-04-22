#!/bin/bash
# This script was tested to work with bouncycastle 1.32.
#
# (NOTE: keytool does not pick up bouncycastle's jce provider jar
#  unless it is installed under the system jar directory)

set -x
set -e

CERTSTORE=cacerts.bks

if [ -a $CERTSTORE ]; then
    rm $CERTSTORE || exit 1
fi

if [ -z "$STOREPASS" ]; then
    STOREPASS=changeit
fi

COUNTER=0
for cert in `ls -1 cacerts`
  do
  yes | keytool \
      -import \
      -v \
      -trustcacerts \
      -alias $COUNTER \
      -file <(openssl x509 -in cacerts/$cert) \
      -keystore $CERTSTORE \
      -storetype BKS \
      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
      -storepass $STOREPASS
  let "COUNTER=$COUNTER + 1"
done
