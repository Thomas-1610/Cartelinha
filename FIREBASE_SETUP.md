# Firebase do Cartelinha

O app funciona offline (Room + DataStore + alarmes). Com a conta autenticada,
a configuração, os registros diários e o vínculo do casal vão para o
Firestore do projeto `cartelinha-e45b4`.

## O que falta no console (obrigatório para o código do parceiro)

As regras **publicadas** no Firebase ainda recusam `users/` e `couples/.../edits`.
Por isso o app fechava com “erro interno” ao vincular: o parceiro escutava `edits`
e a permissão negada derrubava o processo. O código agora trata isso sem fechar,
mas o vínculo completo (perfil + histórico de correções) só funciona depois de
publicar `firebase/firestore.rules`.

### Publicar as regras (2 minutos)

1. Abra https://console.firebase.google.com/project/cartelinha-e45b4/firestore/rules
2. Se pedir login, use a conta dona do projeto.
3. Apague o conteúdo da caixa de regras e cole o arquivo `firebase/firestore.rules` deste repositório.
4. Clique em **Publicar**.

Ou, no computador, depois de `firebase login`:

```
npx -y firebase-tools@latest deploy --only firestore:rules --project cartelinha-e45b4
```

### Conferir Authentication

https://console.firebase.google.com/project/cartelinha-e45b4/authentication/providers

- E-mail/senha deve estar **ativado**.
- Cada aparelho usa **um e-mail diferente** (mulher e homem).

### Teste do vínculo

1. Mulher: entra na conta → Conectividade → Gerar código.
2. Homem: outra conta → cola o código de 6 caracteres → Vincular.
3. Os dois devem continuar abertos. Na mulher, o chip vira “Parceiro conectado”.

## Documentos

- `users/{uid}` — perfil para restaurar a conta em outro aparelho
- `couples/{uidDaDona}` — vínculo do casal
- `couples/{id}/cycle/config` — esquema do anticoncepcional
- `couples/{id}/records/{epochDay}` — tomada, falta ou placebo
- `couples/{id}/edits/{epochDay_millis}` — correções
- `inviteCodes/{code}` — convite de 6 caracteres, uso único, 72 horas
- `fcmTokens/{uid}` — token de notificação do aparelho

A dona da conta grava. O parceiro lê.
