# Cartelinha

O Cartelinha ajuda a lembrar e a acompanhar a rotina do anticoncepcional. Ele mostra o dia de hoje, avisa no horário combinado e guarda o histórico no próprio celular. Se você quiser, também pode entrar com uma conta e compartilhar o acompanhamento com outra pessoa.

Ele organiza a rotina. Não substitui a orientação de um profissional de saúde.

[![Baixar a versão mais recente](https://img.shields.io/github/v/release/Thomas-1610/Cartelinha?style=for-the-badge&label=vers%C3%A3o%20mais%20recente&color=BC6261)](https://github.com/Thomas-1610/Cartelinha/releases/latest/download/Cartelinha.apk)

[![CI](https://github.com/Thomas-1610/Cartelinha/actions/workflows/ci.yml/badge.svg)](https://github.com/Thomas-1610/Cartelinha/actions/workflows/ci.yml)
[![Homologação](https://github.com/Thomas-1610/Cartelinha/actions/workflows/cd-homologacao.yml/badge.svg)](https://github.com/Thomas-1610/Cartelinha/actions/workflows/cd-homologacao.yml)
[![Produção](https://github.com/Thomas-1610/Cartelinha/actions/workflows/cd-producao.yml/badge.svg)](https://github.com/Thomas-1610/Cartelinha/actions/workflows/cd-producao.yml)
[![CodeQL](https://github.com/Thomas-1610/Cartelinha/actions/workflows/codeql.yml/badge.svg)](https://github.com/Thomas-1610/Cartelinha/actions/workflows/codeql.yml)

## Baixar aplicativo

A versão mais recente é a **<!-- versao-atual -->v1.0.0<!-- /versao-atual -->**. O botão abaixo baixa o APK publicado na página oficial de versões deste repositório.

[![Baixar APK](https://img.shields.io/badge/Baixar%20APK-Cartelinha-BC6261?style=for-the-badge)](https://github.com/Thomas-1610/Cartelinha/releases/latest/download/Cartelinha.apk)

Todas as versões ficam em [Releases](https://github.com/Thomas-1610/Cartelinha/releases). Cada versão mostra a data, o que mudou e o arquivo `Cartelinha.apk`.

### Instalar no Android

1. Toque em **Baixar APK** e espere o arquivo terminar de baixar.
2. Abra o arquivo `Cartelinha.apk`.
3. Se o celular pedir permissão, permita a instalação feita pelo navegador ou pelo aplicativo de arquivos. No Android isso aparece como instalação de apps desconhecidos e vale só para quem vai abrir o arquivo.
4. Confirme **Instalar** e depois abra o Cartelinha.

O celular precisa ter **Android 10 ou mais novo**.

Na primeira vez, o aplicativo pode pedir:

- notificações, para avisar no horário;
- alarmes, para o lembrete tocar mesmo com o celular em espera;
- início junto com o celular, para o lembrete continuar depois de reiniciar;
- internet, usada apenas se você criar uma conta ou sincronizar com outra pessoa.

O Cartelinha também pode vibrar quando o lembrete aparecer.

Baixe o aplicativo somente por esta página ou pela página de [Releases](https://github.com/Thomas-1610/Cartelinha/releases). Não instale um APK recebido por mensagem ou por outro site.

## O que dá para fazer

- Escolher o método e o esquema, como cartela, uso contínuo ou intervalo de dias.
- Ver o que corresponde ao dia de hoje.
- Marcar se a dose foi tomada ou se houve uma falta.
- Receber um lembrete no horário escolhido.
- Consultar o calendário e o histórico.
- Usar o aplicativo sem internet. Os registros ficam no celular.
- Criar uma conta para recuperar os dados em outro aparelho.
- Gerar um código para outra pessoa acompanhar a rotina.

## Versões

A versão publicada para download é identificada por uma tag, por exemplo `v1.0.0`. O que mudou em cada versão está no [histórico de versões](CHANGELOG.md) e também na página de [Releases](https://github.com/Thomas-1610/Cartelinha/releases).

Quando uma versão nova é aprovada, a release é atualizada e o mesmo botão de download passa a entregar o APK mais recente.

## Como o aplicativo é organizado

As telas são feitas com Kotlin e Jetpack Compose. A conta do ciclo fica numa parte separada das telas, para o cálculo não depender da interface. Os registros são gravados no celular. A conta, quando existe, sincroniza esses dados com o Firebase. Os lembretes são agendados no próprio aparelho.

| Parte | Para que serve |
| --- | --- |
| Telas | Início, calendário, histórico, configuração e conta |
| Regras do ciclo | Calcular o dia da cartela, a pausa ou o intervalo |
| Banco local | Guardar configuração e registros no celular |
| Firebase | Conta, sincronização e aviso entre aparelhos |
| Lembretes | Notificação no horário escolhido |

As verificações automáticas usam GitHub Actions. O caminho de uma alteração até o APK está em [fluxo de desenvolvimento](docs/fluxo-de-desenvolvimento.md).

## Tecnologias

- Kotlin
- Android, a partir do Android 10
- Jetpack Compose e Material 3
- Room e DataStore
- Firebase Authentication, Firestore e Cloud Messaging
- GitHub Actions para testes, segurança, build e publicação

## Para quem mexe no código

O trabalho do dia a dia acontece em branches separadas e só entra em `develop` por pull request, depois que os testes e as verificações passam. A versão pública sai de `main`, também por pull request e com uma aprovação antes da publicação.

- [Fluxo de desenvolvimento](docs/fluxo-de-desenvolvimento.md)
- [Ambientes e segredos](docs/ambientes-e-segredos.md)
- [Publicação e rollback](docs/publicacao-e-rollback.md)
- [Como contribuir](CONTRIBUTING.md)
