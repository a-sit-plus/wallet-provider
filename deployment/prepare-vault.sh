#!/bin/sh
set -eu

usage() {
  cat >&2 <<'END'
Usage:
  prepare-vault.sh /absolute/path/to/vault --storepass VALUE [options]

Creates the provider's signing keystore keystore.p12 (EC P-256) in the vault directory, unless it exists already.

Options:
  --storepass VALUE  password of the keystore and its key (keystore.secret)
  --alias NAME       alias of the key (keystore.alias), default: wallet-provider
  --dname VALUE      subject of the self-signed certificate, default: CN=Wallet Provider
  --validity DAYS    validity of the certificate, default: 3650
END
  exit 1
}

if [ "$#" -lt 1 ]; then
  usage
fi

vault_dir="$1"
shift
storepass=""
alias_name="wallet-provider"
dname="CN=Wallet Provider"
validity="3650"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --storepass)
      storepass="${2:-}"
      shift 2
      ;;
    --alias)
      alias_name="${2:-}"
      shift 2
      ;;
    --dname)
      dname="${2:-}"
      shift 2
      ;;
    --validity)
      validity="${2:-}"
      shift 2
      ;;
    *)
      usage
      ;;
  esac
done

if [ -z "$storepass" ] || [ -z "$alias_name" ]; then
  usage
fi

command -v keytool >/dev/null 2>&1 || { echo "keytool (from a JDK) is required" >&2; exit 1; }

mkdir -p "$vault_dir"
keystore="$vault_dir/keystore.p12"

if [ -e "$keystore" ]; then
  echo "Keeping existing $keystore"
else
  # The password is passed through the environment, so it does not show up in the process list.
  STOREPASS="$storepass" keytool -genkeypair \
    -alias "$alias_name" \
    -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA \
    -dname "$dname" \
    -validity "$validity" \
    -storetype PKCS12 \
    -keystore "$keystore" \
    -storepass:env STOREPASS
  chmod 600 "$keystore"
  echo "Created $keystore"
fi

cat <<END

Configure the provider with (deployment/config-server/wallet-provider.yml):

keystore:
  file: /vault/keystore.p12
  alias: $alias_name
  secret: <the --storepass value>
END
