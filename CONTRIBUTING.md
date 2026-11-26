# Como contribuir

Crie a branch a partir de `develop` e abra a pull request para `develop`. O detalhe do caminho até a produção está em [fluxo de desenvolvimento](docs/fluxo-de-desenvolvimento.md).

Antes de pedir a integração:

- rode `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
- confira que `local.properties` e a pasta `keystore/` continuam fora do Git;
- use um título no formato `tipo: descrição curta`.

Correções pequenas usam `fix/`. Funções novas usam `feature/`.
