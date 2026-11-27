# Fluxo de desenvolvimento

`main` guarda o que está publicado. `develop` junta o que já foi aceito e ainda espera a próxima versão. Nenhuma das duas recebe commit direto e nenhuma das duas é apagada quando uma pull request é integrada.

## Branches

| Branch | Uso |
| --- | --- |
| `feature/nome-curto` | Funcionalidade nova, criada a partir de `develop` |
| `fix/nome-curto` | Correção |
| `chore/nome-curto` | Manutenção que não muda o uso do aplicativo |
| `release/vX.Y.Z` | Preparação de uma versão |
| `develop` | Integração |
| `main` | Produção |

## Do código até a develop

1. Atualize `develop` e crie a branch de trabalho a partir dela.
2. Cada push dispara a pipeline de CI: integridade do projeto, Android Lint, testes unitários, resolução das dependências, busca de segredos, validação do Gradle Wrapper e build de debug.
3. A pull request para `develop` dispara de novo a política da pull request, a revisão de dependências e os testes de integração no emulador.
4. O título da pull request segue o padrão `tipo: descrição`. Os tipos aceitos são `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `release` e `revert`.
5. Se qualquer verificação falhar, a proteção da branch impede a integração.
6. Com as verificações verdes e a revisão concluída, a pull request entra em `develop`.

Uma pull request para `main` só pode sair de `develop` ou de `release/*`. A pipeline recusa qualquer outro caminho.

## Da develop até a produção

7. O push em `develop` roda a CI completa. Só depois do sucesso a pipeline de homologação começa, e ela espera aprovação no ambiente `homologation` antes de gerar o APK assinado de homologação.
8. A promoção para `main` é outra pull request. Ela passa pela CI, pela política de origem e pela proteção da branch.
9. O push em `main` roda a CI outra vez. A pipeline de produção só publica depois da aprovação no ambiente `production`.
10. A publicação cria a tag `vX.Y.Z`, a release e os APKs `Cartelinha.apk` e `Cartelinha-vX.Y.Z.apk`.
11. O botão de download do README aponta para o APK da release mais recente. Uma versão nova substitui esse download sem mudar o endereço.
12. Depois da publicação, traga `main` de volta para `develop` com um merge, para a próxima branch partir do código que foi publicado.

## Mensagens de commit

Use uma frase curta no imperativo, com o tipo na frente:

```text
feat: adiciona atalho do lembrete na tela inicial
fix: corrige a contagem do dia de pausa
ci: exige a validação do wrapper no CI
```

## Preparar uma versão

No GitHub, execute a ação **Preparar release** a partir de `develop`. Informe se a mudança é `patch`, `minor` ou `major` e escreva as alterações, uma por linha. A ação atualiza `version.properties` e `CHANGELOG.md`, abre a branch `release/vX.Y.Z` e cria a pull request para `main`.

Para essa pull request disparar a CI sozinha, cadastre um segredo `RELEASE_TOKEN` com permissão de escrita no repositório. Sem esse token, a ação ainda abre a pull request, mas o GitHub não inicia workflows com o token padrão. Nesse caso, atualize a branch com um commit seu para a CI rodar.

O `VERSION_CODE` aumenta a cada release. O `VERSION_NAME` segue o número escolhido.
