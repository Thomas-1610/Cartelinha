# Ambientes e segredos

O aplicativo tem três momentos de uso. As credenciais de cada um ficam fora do Git.

| Ambiente | Onde vive | O que recebe |
| --- | --- | --- |
| Desenvolvimento | Computador de quem programa | Código da branch de trabalho. O SDK local fica em `local.properties`, que não é versionado. |
| Homologação | Ambiente `homologation` no GitHub | APK assinado gerado depois da CI de `develop`. Fica como artefato da execução, com o sufixo `-homolog` no nome da versão. Não substitui o download público. |
| Produção | Ambiente `production` no GitHub | APK assinado publicado na release de `main`, somente depois da aprovação. |

Os dois ambientes do GitHub exigem um aprovador antes de continuar. A pessoa que disparou a publicação pode aprovar: a trava existe para a promoção ser explícita, não para depender de outra conta.

A homologação e a produção usam a mesma chave de assinatura. Assim, um APK novo consegue atualizar o aplicativo já instalado. Elas não usam projetos Firebase separados: o aplicativo já aponta para o projeto configurado em `app/google-services.json`. Separar o backend exigiria outro projeto no console do Firebase e não faz parte desta estrutura.

## O que pode estar no repositório

`app/google-services.json` permanece no Git. A chave dentro dele identifica o aplicativo Firebase e é incluída em qualquer build Android. Ela não autoriza, sozinha, a administrar o projeto.

## O que não pode estar no repositório

- `local.properties`
- keystore (`.jks` ou `.keystore`)
- senhas, arquivos `.env` e contas de serviço do Google

A pipeline procura esses arquivos no Git e também varre o histórico com o Gitleaks. A chave de cliente do Firebase está na lista de exceções do `.gitleaks.toml`, pelo motivo acima.

## Segredos no GitHub

| Segredo | Função |
| --- | --- |
| `KEYSTORE_BASE64` | Keystore de release, codificado em Base64 |
| `KEYSTORE_PASSWORD` | Senha do keystore |
| `KEY_ALIAS` | Alias da chave |
| `KEY_PASSWORD` | Senha da chave |
| `RELEASE_TOKEN` | Opcional. Permite que a ação de preparar release dispare a CI |

A cópia local da chave fica na pasta `keystore/`, ignorada pelo Git. Guarde essa pasta em um local seguro. O GitHub não mostra o valor de um segredo depois que ele é salvo. Sem a chave, não é possível publicar uma atualização que o Android aceite sobre a versão já instalada.

No computador, uma build de release assinada pode ler estas propriedades do Gradle, se você preferir não usar variáveis de ambiente:

```text
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

## Verificações de segurança

- Gitleaks em cada push
- Validação do Gradle Wrapper
- Revisão de dependências nas pull requests, bloqueando severidade alta
- Dependabot nas dependências Gradle e nas ações do GitHub, com pull requests para `develop`
- Grafo de dependências enviado ao GitHub a cada push em `main`
- CodeQL nas pull requests e nas branches `develop` e `main`
- Alertas de vulnerabilidade e atualização de segurança do GitHub ativados no repositório
