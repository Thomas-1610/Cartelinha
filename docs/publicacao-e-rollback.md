# Publicação e rollback

A produção só começa depois que a CI da `main` termina com sucesso. Em seguida o ambiente `production` fica esperando aprovação. Sem essa aprovação o APK não é gerado e a release não é criada.

## O que a publicação faz

1. Confere os segredos da assinatura.
2. Gera o APK release assinado com a versão de `version.properties`.
3. Publica dois arquivos na mesma release:
   - `Cartelinha.apk`, usado pelo botão de download do README;
   - `Cartelinha-vX.Y.Z.apk`, com o número da versão no nome.
4. Cria a tag `vX.Y.Z` apontando para a revisão que passou na CI.
5. Escreve na release o trecho correspondente do `CHANGELOG.md`.
6. Marca essa release como a mais recente.

O endereço estável do aplicativo é:

```text
https://github.com/Thomas-1610/Cartelinha/releases/latest/download/Cartelinha.apk
```

A página com o histórico de todas as versões é:

```text
https://github.com/Thomas-1610/Cartelinha/releases
```

As tags `v*` não podem ser apagadas nem movidas. Cada publicação permanece identificada.

## Homologação

A homologação segue a mesma ordem: CI verde em `develop`, aprovação no ambiente `homologation` e só então o APK assinado. O arquivo fica anexado à execução da ação, por 30 dias, e não entra na página de Releases. O nome da versão recebe o sufixo `-homolog`.

## Onde ver o histórico

- Releases: versão, data, alterações e APK.
- Ambientes `homologation` e `production`: quem aprovou e qual revisão foi implantada.
- Ações do GitHub: registro de cada execução, inclusive as que falharam antes da publicação.

## Rollback

O rollback não reescreve o histórico. Ele faz uma versão já publicada voltar a ser a oferecida para download.

1. Abra a ação **Rollback de produção**.
2. Informe a tag, por exemplo `v1.0.0`.
3. Aprove o ambiente `production`.
4. A ação confirma que a tag existe e que a release tem `Cartelinha.apk`.
5. Essa release volta a ser a mais recente. O botão do README passa a baixar esse APK.

A versão que estava em destaque antes da restauração continua publicada e aparece nas notas da release restaurada. Para corrigir um problema com código novo, o caminho normal continua sendo uma branch `fix/`, pull request, CI e uma versão nova. O rollback cobre o caso em que a versão anterior precisa voltar a ser a disponível imediatamente.
