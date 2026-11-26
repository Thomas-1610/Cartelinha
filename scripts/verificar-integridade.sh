#!/usr/bin/env bash
# Confere se o projeto ainda tem o que precisa para compilar e se nenhum segredo entrou no Git.
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$raiz"

obrigatorios=(
  "gradlew"
  "gradle/wrapper/gradle-wrapper.jar"
  "gradle/wrapper/gradle-wrapper.properties"
  "settings.gradle.kts"
  "app/build.gradle.kts"
  "app/google-services.json"
  "version.properties"
  "app/src/main/AndroidManifest.xml"
)

for arquivo in "${obrigatorios[@]}"; do
  if [[ ! -f "$arquivo" ]]; then
    echo "Arquivo obrigatório ausente: $arquivo"
    exit 1
  fi
done

if ! grep -Eq '^VERSION_NAME=[0-9]+\.[0-9]+\.[0-9]+$' version.properties; then
  echo "VERSION_NAME precisa seguir o formato MAJOR.MINOR.PATCH."
  exit 1
fi

if ! grep -Eq '^VERSION_CODE=[0-9]+$' version.properties; then
  echo "VERSION_CODE precisa ser um número inteiro."
  exit 1
fi

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  rastreados="$(git ls-files)"
  if echo "$rastreados" | grep -Eq '(^|/)(local\.properties|\.env)$|\.(jks|keystore|p12|pem)$|credentials\.json|serviceAccount'; then
    echo "Há arquivo sensível versionado. Remova-o do Git antes de continuar."
    echo "$rastreados" | grep -E '(^|/)(local\.properties|\.env)$|\.(jks|keystore|p12|pem)$|credentials\.json|serviceAccount' || true
    exit 1
  fi
fi

echo "Integridade do projeto confirmada."
