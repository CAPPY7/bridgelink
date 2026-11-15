#!/bin/bash
# Create the stable Mac signing certificate once. Does not print the password.
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p owner-backup
OUT="owner-backup/BridgeLink-Self-Signed.p12"
PASSFILE="owner-backup/MAC_SIGN_P12_PASSWORD.txt"
if [ -f "$OUT" ]; then
  echo "Certificate already exists. Not creating another."
  exit 0
fi
openssl rand -base64 24 | tr -d '\n' > "$PASSFILE"
CNF="owner-backup/codesign.cnf"
cat > "$CNF" << 'EOF'
[ req ]
distinguished_name = dn
x509_extensions = v3
prompt = no
[ dn ]
CN = BridgeLink Self-Signed
[ v3 ]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature
extendedKeyUsage = critical,codeSigning
EOF
openssl req -x509 -new -nodes -newkey rsa:2048 -keyout owner-backup/key.pem -out owner-backup/cert.pem -days 3650 -config "$CNF" >/dev/null 2>&1
openssl pkcs12 -export -inkey owner-backup/key.pem -in owner-backup/cert.pem -out "$OUT" -name "BridgeLink Self-Signed" -passout "file:$PASSFILE" >/dev/null
rm -f owner-backup/key.pem owner-backup/cert.pem "$CNF"
echo "Wrote the certificate backup. Password file is next to it. Neither is committed."
